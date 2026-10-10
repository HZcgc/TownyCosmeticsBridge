package com.townyreborn.petshops;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import com.ticxo.modelengine.api.animation.BlueprintAnimation;
import com.ticxo.modelengine.api.animation.handler.AnimationHandler;
import com.ticxo.modelengine.api.animation.property.IAnimationProperty;
import com.ticxo.modelengine.api.entity.BaseEntity;
import com.ticxo.modelengine.api.entity.data.IEntityData;
import com.ticxo.modelengine.api.events.BaseEntityInteractEvent;
import com.ticxo.modelengine.api.events.BaseEntityInteractEvent.Action;
import com.ticxo.modelengine.api.generator.blueprint.ModelBlueprint;
import com.ticxo.modelengine.api.model.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

/** Exercises the installed ModelEngine R4.1.0 interfaces, including native click events. */
class PedestalModelTest {
    private Integrations.Models adapter() throws Exception {
        return new Integrations.Models(ModelBlueprint.class.getClassLoader());
    }
    private Integrations.Models.Handle handle(ModeledEntity entity, ActiveModel model) {
        return new Integrations.Models.Handle(UUID.randomUUID(), entity, model);
    }
    @Test void nativeAttackOpensTheShopAndMainHandInteractionPlaysTheAnimation() throws Exception {
        var adapter=adapter(); var player=mock(Player.class); var base=mock(BaseEntity.class);
        UUID id=UUID.randomUUID(); when(base.getUUID()).thenReturn(id);
        try (var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            for (var action:Action.values()) for (var slot:List.of(EquipmentSlot.HAND,EquipmentSlot.OFF_HAND)) {
                var event=new BaseEntityInteractEvent(player,base,null,action,slot,false,null,new Vector());
                var expected=action==Action.ATTACK ? Integrations.Click.SHOP
                    : slot==EquipmentSlot.HAND ? Integrations.Click.INTERACT : Integrations.Click.IGNORE;
                assertEquals(expected,adapter.click(event),action+" "+slot);
                assertEquals(id,adapter.baseId(event)); assertSame(player,adapter.player(event));
            }
        }
    }
    @Test void renderDistanceAndOcclusionOverridesAreAppliedToThePedestalModel() throws Exception {
        var entity=mock(ModeledEntity.class); var base=mock(BaseEntity.class); var data=mock(IEntityData.class);
        when(entity.getBase()).thenReturn(base); when(base.getData()).thenReturn(data);
        adapter().rendering(handle(entity,null),64);
        verify(base).setRenderRadius(64);
        verify(data).setBlockedCull(false); verify(data).setBackCull(false); verify(data).setVerticalCullDistance(64.0);
        verifyNoMoreInteractions(data);
    }
    @Test void renderRangeCanBeSetBeforeModelEngineRegistersItsEntityData() throws Exception {
        var entity=mock(ModeledEntity.class); var base=mock(BaseEntity.class); when(entity.getBase()).thenReturn(base);
        adapter().rendering(handle(entity,null),96);
        verify(base).setRenderRadius(96);
    }
    @Test void facingAPlayerUpdatesBothNativeBodyAndHeadRotations() throws Exception {
        var entity=mock(ModeledEntity.class);
        adapter().face(handle(entity,null),-90.0f);
        verify(entity).setYBodyRotImmediately(-90.0f); verify(entity).setYHeadRotImmediately(-90.0f);
    }
    @Test void theActualPedestalTickFacesTheClosestVisiblePlayerAndStopsWhenNoOneIsNearby() throws Exception {
        var plugin=mock(TownyRebornPetShops.class,CALLS_REAL_METHODS);
        var config=new YamlConfiguration(); config.set("npc.look-at-player-range",8);
        doReturn(config).when(plugin).getConfig();
        var world=mock(World.class); var base=mock(ArmorStand.class); var hitbox=mock(Interaction.class);
        var entity=mock(ModeledEntity.class); var location=new Location(world,0,64,0);
        when(base.isValid()).thenReturn(true); when(base.getLocation()).thenReturn(location); when(hitbox.isValid()).thenReturn(true);
        var spectator=mock(Player.class); when(spectator.getGameMode()).thenReturn(GameMode.SPECTATOR);
        var invisible=mock(Player.class); when(invisible.getGameMode()).thenReturn(GameMode.SURVIVAL); when(invisible.isInvisible()).thenReturn(true);
        var closest=mock(Player.class); when(closest.getGameMode()).thenReturn(GameMode.SURVIVAL); when(closest.getLocation()).thenReturn(new Location(world,2,64,0));
        var farther=mock(Player.class); when(farther.getGameMode()).thenReturn(GameMode.SURVIVAL); when(farther.getLocation()).thenReturn(new Location(world,0,64,6));
        when(world.getNearbyPlayers(location,8)).thenReturn(List.of(spectator,invisible,farther,closest));
        Class<?> runtime=Arrays.stream(TownyRebornPetShops.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals("RuntimeNpc")).findFirst().orElseThrow();
        var constructor=runtime.getDeclaredConstructor(Entity.class,TextDisplay.class,Interaction.class,Integrations.Models.Handle.class,boolean.class);
        constructor.setAccessible(true);
        Object active=constructor.newInstance(base,null,hitbox,handle(entity,null),true);
        for (var entry:Map.of("active",Map.of(UUID.randomUUID(),active),"models",adapter()).entrySet()) {
            var field=TownyRebornPetShops.class.getDeclaredField(entry.getKey()); field.setAccessible(true); field.set(plugin,entry.getValue());
        }
        var tick=TownyRebornPetShops.class.getDeclaredMethod("tickPedestals"); tick.setAccessible(true); tick.invoke(plugin);
        verify(base).setRotation(-90.0f,0.0f); verify(entity).setYBodyRotImmediately(-90.0f); verify(entity).setYHeadRotImmediately(-90.0f);
        clearInvocations(base,entity); when(world.getNearbyPlayers(location,8)).thenReturn(List.of()); tick.invoke(plugin);
        verify(base,never()).setRotation(anyFloat(),anyFloat()); verifyNoInteractions(entity);
    }
    @Test void theFourSuppliedPetsPlayTheirOwnNativeClipOnceAndKeepTheirIdleAnimation() throws Exception {
        for (String selected:List.of("flap","honk","crush","cauldron")) {
            var blueprint=new ModelBlueprint();
            for (String name:List.of("idle","walk","spawn",selected)) {
                var animation=new BlueprintAnimation(blueprint,name); animation.setLength(1.75);
                blueprint.getAnimations().put(name,animation);
            }
            if (selected.equals("honk")) blueprint.getAnimations().put("honkshot",new BlueprintAnimation(blueprint,"honkshot"));
            var model=mock(ActiveModel.class); var handler=mock(AnimationHandler.class); var property=mock(IAnimationProperty.class);
            when(model.getBlueprint()).thenReturn(blueprint); when(model.getAnimationHandler()).thenReturn(handler);
            when(handler.playAnimation(selected,0.1,0.1,1.0,true)).thenReturn(property);
            var playback=adapter().interact(handle(null,model),"auto");
            assertEquals(selected,playback.animation()); assertEquals(1950,playback.durationMillis());
            verify(handler).playAnimation(selected,0.1,0.1,1.0,true);
            verify(property).setForceLoopMode(BlueprintAnimation.LoopMode.ONCE);
            verifyNoMoreInteractions(handler); // Never stop/remove the standing idle animation.
        }
    }
    @Test void futurePetsAllowAutomaticExplicitAndDisabledInteractionsWithoutChoosingWalk() {
        var clips=List.of("idle","walk","spawn","wag_tail","greet");
        assertEquals("greet",Integrations.Models.chooseInteraction(clips,"auto"));
        assertEquals("wag_tail",Integrations.Models.chooseInteraction(clips,"wag_tail"));
        assertNull(Integrations.Models.chooseInteraction(clips,"off"));
        assertNull(Integrations.Models.chooseInteraction(clips,"missing"));
        assertNull(Integrations.Models.chooseInteraction(List.of("idle","walk"),"auto"));
    }
}
