package com.townyreborn.petshops;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PetSoundsTest {
    @TempDir Path folder;
    private void nativeFiles() throws Exception {
        Path mobs=folder.resolve("MythicMobs/Mobs"), skills=folder.resolve("MythicMobs/Skills/future-pack");
        Files.createDirectories(mobs); Files.createDirectories(skills);
        Files.writeString(mobs.resolve("pets.yml"),"""
            unrelated_mob_name:
              Skills:
              - skill{s=spawn_effect} ~onSpawn
              - randomskill{skills=clown_honkshot,clown_honk;sync=true} ~onInteract
              - skill{s=walk_sound} ~onTimer:10
            """);
        Files.writeString(skills.resolve("pets.yaml"),"""
            spawn_effect:
              Skills:
              - state{state=spawn} @self
              - sound{s=entity.villager.celebrate} @self
            walk_sound:
              Skills:
              - sound{s=entity.pig.step} @self
            clown_honk:
              Skills:
              - state{state=honk;speed=1} @self
              - delay 6
              - sound{s=entity.pig.step;p=1.2;v=0.1} @self
              - delay 2
              - sound{s=block.note_block.trumpet_exposed;p=1.85;v=0.7} @self
              - delay 3
              - sound{s=block.note_block.trumpet_exposed;p=1.85;v=0.7} @self
              - delay 9
              - sound{s=entity.pig.step;p=1.2;v=0.1} @self
            clown_honkshot:
              Skills:
              - state{state=honkshot} @self
              - sound{s=entity.villager.celebrate;p=1.6;v=0.6} @self
              - delay 14
              - sound{s=block.note_block.trumpet_exposed;p=1.85;v=0.7} @self
              - delay 2
              - sound{s=entity.villager.hurt;p=1.6;v=0.6} @self
            """);
    }
    private PetCatalog catalog() throws Exception {
        Path pets=folder.resolve("MCPets/Pets"); Files.createDirectories(pets);
        Files.writeString(pets.resolve("new.yml"),"Id: future_pet\nMythicMob: unrelated_mob_name\nPermission: pet.future\n");
        var defaults=new YamlConfiguration(); defaults.set("ingame-price",75000); defaults.set("store-price",10.99);
        defaults.set("store-url","https://shop.example.com/package/123");
        return new PetCatalog(folder.resolve("PetShops/pets.yml"),pets,defaults);
    }
    @Test void nativeInteractionSoundsUseTheActualMobAndClipWithExactCumulativeDelays() throws Exception {
        nativeFiles(); var sounds=PetSounds.read(folder.resolve("MythicMobs")).forPet("unrelated_mob_name");
        assertEquals(Set.of("honk","honkshot"),sounds.keySet()); // No spawn or timer sounds.
        var honk=PetSounds.forAnimation(sounds,"honk");
        assertEquals(List.of(6,8,11,20),honk.stream().map(PetSounds.Cue::tick).toList());
        assertEquals("minecraft:block.note_block.trumpet_exposed",honk.get(1).sound());
        assertEquals(0.7f,honk.get(1).volume()); assertEquals(1.85f,honk.get(1).pitch());
        assertEquals(SoundCategory.MASTER,honk.get(1).category());
        assertEquals(List.of(0,14,16),PetSounds.forAnimation(sounds,"honkshot").stream().map(PetSounds.Cue::tick).toList());
        assertEquals(1050,PetSounds.duration(honk));
    }
    @Test void nestedSoundHelpersKeepTheirOwnDelayAndDoNotBlockTheCallingSkill() {
        var nativeCatalog=new PetSounds.NativeCatalog(Map.of("fox",List.of("skill{s=greet} ~onInteract")),Map.of(
            "greet",List.of("state{state=wave}","delay 3","skill{s=voice}","delay 2","sound{s=entity.fox.ambient}"),
            "voice",List.of("delay 4","e:s{sound=custom:fox.voice;volume=0.8;pitch=1.4;category=PLAYERS}","skill{s=greet}",
                "damage{a=100}","command{c=op Player}")));
        var cues=nativeCatalog.forPet("fox").get("wave");
        assertEquals(List.of(5,7),cues.stream().map(PetSounds.Cue::tick).toList());
        assertEquals("custom:fox.voice",cues.get(1).sound()); assertEquals(SoundCategory.PLAYERS,cues.get(1).category());
    }
    @Test void addingOrReloadingPetsReadsNativeSoundsAndPreservesPriceLinkAndHologramHeight() throws Exception {
        nativeFiles(); var catalog=catalog(); var pet=catalog.add("future_pet");
        assertEquals("auto",FilesIO.read(catalog.shopFile(pet.id())).getString("interaction-sounds"));
        assertEquals(4,pet.sounds().get("honk").size());
        catalog.set(pet.id(),"hologram-height",2.8); catalog.set(pet.id(),"ingame-price",85000.0);
        Path skills=folder.resolve("MythicMobs/Skills/future-pack/pets.yaml");
        Files.writeString(skills,Files.readString(skills).replace("v=0.7","v=0.9"));
        assertTrue(catalog.updateDefinitions(catalog.scanDefinitions()));
        assertEquals(0.9f,catalog.get(pet.id()).sounds().get("honk").get(1).volume());
        assertEquals(85000,catalog.get(pet.id()).price()); assertEquals(2.8,catalog.get(pet.id()).hologramHeight());
        assertEquals("https://shop.example.com/package/123",catalog.get(pet.id()).storeUrl());
        assertEquals("trpetshop grant {id} future_pet {transaction}",FilesIO.read(catalog.shopFile(pet.id())).getString("tebex.command"));
    }
    @Test void customSoundProfilesAndOffSurviveReloadWhileInvalidEditsNeverReplaceTheLastOffer() throws Exception {
        nativeFiles(); var catalog=catalog(); var pet=catalog.add("future_pet");
        var custom=Map.of("honk",List.of(Map.of("sound","custom:pet.honk","delay-ticks",5,"volume",0.8,"pitch",1.1,"category","PLAYERS")));
        catalog.set(pet.id(),"interaction-sounds",custom); catalog.reload();
        assertEquals("custom:pet.honk",catalog.get(pet.id()).sounds().get("honk").getFirst().sound());
        byte[] before=Files.readAllBytes(catalog.shopFile(pet.id()));
        for (Object value:List.of(-1,5.5,Double.NaN,1201)) {
            var bad=Map.of("honk",List.of(Map.of("sound","custom:pet.honk","delay-ticks",value)));
            assertThrows(java.io.IOException.class,()->catalog.set(pet.id(),"interaction-sounds",bad));
            assertArrayEquals(before,Files.readAllBytes(catalog.shopFile(pet.id())));
        }
        catalog.set(pet.id(),"interaction-sounds","off"); catalog.reload();
        assertTrue(catalog.get(pet.id()).sounds().isEmpty()); assertEquals(75000,catalog.get(pet.id()).price());
    }
    @Test void soundPlaybackUsesNativeVolumePitchCategoryAndTheScheduledAnimationTick() {
        var scheduler=mock(BukkitScheduler.class); var plugin=mock(Plugin.class); var speaker=mock(Entity.class); var world=mock(World.class);
        var location=new Location(world,3,64,8); when(speaker.isValid()).thenReturn(true); when(speaker.getLocation()).thenReturn(location);
        var tasks=new ArrayList<Runnable>();
        when(scheduler.runTaskLater(eq(plugin),any(Runnable.class),eq(8L))).thenAnswer(invocation->{ tasks.add(invocation.getArgument(1)); return null; });
        var cues=List.of(new PetSounds.Cue(0,"entity.cat.ambient",1,1.8f,SoundCategory.MASTER),
            new PetSounds.Cue(8,"custom:cauldron",0.6f,2,SoundCategory.MASTER));
        try (var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            PetSounds.play(plugin,speaker,cues,()->true);
            verify(world).playSound(location,"minecraft:entity.cat.ambient",SoundCategory.MASTER,1,1.8f);
            verify(world,never()).playSound(location,"custom:cauldron",SoundCategory.MASTER,0.6f,2);
            assertEquals(1,tasks.size()); tasks.getFirst().run();
            verify(world).playSound(location,"custom:cauldron",SoundCategory.MASTER,0.6f,2);
            verifyNoMoreInteractions(world);
        }
    }
    @Test void delayedSoundsAreSuppressedAfterReloadRemovalOrReplacementOfTheAnimation() {
        var scheduler=mock(BukkitScheduler.class); var plugin=mock(Plugin.class); var speaker=mock(Entity.class); var world=mock(World.class);
        when(speaker.isValid()).thenReturn(true); when(speaker.getLocation()).thenReturn(new Location(world,0,64,0));
        var tasks=new ArrayList<Runnable>(); var current=new AtomicBoolean(true);
        when(scheduler.runTaskLater(eq(plugin),any(Runnable.class),anyLong())).thenAnswer(invocation->{ tasks.add(invocation.getArgument(1)); return null; });
        try (var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            PetSounds.play(plugin,speaker,List.of(new PetSounds.Cue(7,"entity.cat.ambient",1,1,SoundCategory.MASTER)),current::get);
            current.set(false); tasks.getFirst().run(); verifyNoInteractions(world);
            current.set(true); when(speaker.isValid()).thenReturn(false); tasks.getFirst().run(); verifyNoInteractions(world);
        }
    }
    private static void field(Object target,String name,Object value) throws Exception {
        var field=target.getClass()==TownyRebornPetShops.class ? target.getClass().getDeclaredField(name) : TownyRebornPetShops.class.getDeclaredField(name);
        field.setAccessible(true); field.set(target,value);
    }
    @Test void actualRightClickStartsOneAudioSequenceAcrossDuplicateEventsAndOtherPlayers() throws Exception {
        nativeFiles(); var catalog=catalog(); var pet=catalog.add("future_pet");
        catalog.set(pet.id(),"interaction-sounds",Map.of("honk",List.of(Map.of("sound","entity.cat.ambient","delay-ticks",0),
            Map.of("sound","block.note_block.trumpet_exposed","delay-ticks",8))));
        var plugin=mock(TownyRebornPetShops.class,CALLS_REAL_METHODS); doReturn(new YamlConfiguration()).when(plugin).getConfig();
        var models=mock(Integrations.Models.class); var speaker=mock(Entity.class); var world=mock(World.class); var scheduler=mock(BukkitScheduler.class);
        UUID npc=UUID.randomUUID(), entity=UUID.randomUUID(); when(speaker.getUniqueId()).thenReturn(entity);
        var location=new Location(world,0,64,0); when(speaker.isValid()).thenReturn(true); when(speaker.getLocation()).thenReturn(location);
        var bindings=new NpcBindings(folder.resolve("PetShops/npcs.yml")); bindings.put(new NpcBindings.Binding(npc,1,pet.id(),true));
        var handle=new Integrations.Models.Handle(entity,null,null);
        when(models.interact(handle,"auto")).thenReturn(new Integrations.Models.Playback("honk",500));
        Class<?> runtime=Arrays.stream(TownyRebornPetShops.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals("RuntimeNpc")).findFirst().orElseThrow();
        var constructor=runtime.getDeclaredConstructor(Entity.class,TextDisplay.class,Interaction.class,Integrations.Models.Handle.class,boolean.class); constructor.setAccessible(true);
        Object active=constructor.newInstance(speaker,null,null,handle,true);
        field(plugin,"catalog",catalog); field(plugin,"bindings",bindings); field(plugin,"models",models);
        field(plugin,"active",new HashMap<>(Map.of(npc,active))); field(plugin,"entities",new HashMap<>(Map.of(entity,npc)));
        field(plugin,"lastClick",new HashMap<>()); field(plugin,"animationUntil",new HashMap<>());
        var players=List.of(mock(Player.class),mock(Player.class));
        for (Player player:players) { when(player.getUniqueId()).thenReturn(UUID.randomUUID()); when(player.hasPermission("townyreborn.petshops.use")).thenReturn(true); }
        var tasks=new ArrayList<Runnable>();
        when(scheduler.runTaskLater(eq(plugin),any(Runnable.class),eq(8L))).thenAnswer(invocation->{ tasks.add(invocation.getArgument(1)); return null; });
        try (var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            for (Player player:players) for (int repeat=0;repeat<2;repeat++) {
                var event=new PlayerInteractEntityEvent(player,speaker,EquipmentSlot.HAND); plugin.entityClick(event); assertTrue(event.isCancelled());
            }
            verify(models,times(1)).interact(handle,"auto"); verify(world,times(1)).playSound(location,"minecraft:entity.cat.ambient",SoundCategory.MASTER,1,1);
            assertEquals(1,tasks.size());
            field(plugin,"active",new HashMap<>()); tasks.getFirst().run();
            verifyNoMoreInteractions(world);
        }
    }
}
