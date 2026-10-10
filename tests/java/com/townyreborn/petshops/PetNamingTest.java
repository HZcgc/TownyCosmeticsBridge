package com.townyreborn.petshops;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.function.BiConsumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryView;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;

class PetNamingTest {
    private final Object pet=new Object();
    private final UUID owner=UUID.fromString("11111111-2222-3333-4444-555555555555");
    private final JavaPlugin plugin=mock(JavaPlugin.class);
    private final Integrations.MCPets pets=mock(Integrations.MCPets.class);
    private final Player player=mock(Player.class);
    private final Entity entity=mock(Entity.class);
    private final BukkitScheduler scheduler=mock(BukkitScheduler.class);
    private final List<Runnable> tasks=new ArrayList<>();
    private final List<Throwable> errors=new ArrayList<>();
    @SuppressWarnings("unchecked") private final BiConsumer<Player,String> messages=mock(BiConsumer.class);
    private final PetNaming naming;
    PetNamingTest() throws Exception {
        when(plugin.isEnabled()).thenReturn(true); when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(player.isOnline()).thenReturn(true); when(player.getUniqueId()).thenReturn(owner);
        when(player.isSneaking()).thenReturn(true); when(player.hasPermission(PetNaming.PERMISSION)).thenReturn(true);
        when(pets.find(entity)).thenReturn(pet); when(pets.owner(pet)).thenReturn(owner);
        when(pets.present(pet)).thenReturn(true); when(pets.maxNameLength()).thenReturn(16);
        naming=spy(new PetNaming(plugin,pets,messages,errors::add)); doNothing().when(naming).show(eq(player),any());
        when(scheduler.runTask(eq(plugin),any(Runnable.class))).thenAnswer(invocation->{ tasks.add(invocation.getArgument(1)); return null; });
    }
    private PetNaming.Session start() throws Exception {
        try (var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var event=new PlayerInteractEntityEvent(player,entity,EquipmentSlot.HAND);
            naming.click(event); assertTrue(event.isCancelled());
            naming.click(event); naming.clickAt(new PlayerInteractAtEntityEvent(player,entity,new org.bukkit.util.Vector(),EquipmentSlot.OFF_HAND));
            assertEquals(1,tasks.size()); tasks.removeFirst().run();
            var session=org.mockito.ArgumentCaptor.forClass(PetNaming.Session.class);
            verify(naming).show(eq(player),session.capture()); return session.getValue();
        }
    }
    @Test void shiftRightClickNamesOnlyAnOwnersSummonedPetAndSuppressesDuplicateEvents() throws Exception {
        var session=start(); naming.save(player,session,"<gradient:#ff55ff:#aaff55>Hi</gradient>");
        verify(pets).rename(pet,"§dH§x§a§a§f§f§5§5i");
        naming.save(player,session,"Different"); verify(pets,times(1)).rename(eq(pet),anyString()); assertTrue(errors.isEmpty());
    }
    @Test void otherPlayersNormalRightClicksAndPedestalModelsKeepTheirExistingFlow() throws Exception {
        when(pets.owner(pet)).thenReturn(UUID.randomUUID()); assertFalse(naming.interact(player,entity));
        when(pets.owner(pet)).thenReturn(owner); when(player.isSneaking()).thenReturn(false); assertFalse(naming.interact(player,entity));
        when(player.isSneaking()).thenReturn(true); when(pets.find(entity)).thenReturn(null); assertFalse(naming.interact(player,entity));
        assertTrue(tasks.isEmpty()); verify(pets,never()).rename(any(),any());
    }
    @Test void staleRevokedAndLoggedOutSessionsCannotRenameAPet() throws Exception {
        var session=start(); when(pets.present(pet)).thenReturn(false);
        naming.save(player,session,"&aBuddy"); verify(pets,never()).rename(any(),any());
        when(pets.present(pet)).thenReturn(true); naming.quit(new PlayerQuitEvent(player,net.kyori.adventure.text.Component.empty()));
        naming.save(player,session,"&aBuddy"); verify(pets,never()).rename(any(),any());
        assertTrue(errors.isEmpty());
    }
    @Test void mcpetsBlacklistAndVisibleLengthAreCheckedBeforeSaving() throws Exception {
        var session=start(); when(pets.blockedWord("§abadword")).thenReturn("badword");
        naming.save(player,session,"&abadword"); verify(pets,never()).rename(any(),any());
        naming.save(player,session,"this name is far too long"); verify(pets,never()).rename(any(),any());
        naming.save(player,session,"&aBuddy"); verify(pets).rename(pet,"§aBuddy"); assertTrue(errors.isEmpty());
    }
    @Test void mcpetsMenuStaysAccessibleAndReloadInvalidatesOlderNameDialogs() throws Exception {
        var session=start(); naming.openNativeMenu(player,session); verify(pets).openMenu(player,pet);
        naming.save(player,session,"Buddy"); verify(pets,never()).rename(any(),any());
        reset(naming); doNothing().when(naming).show(eq(player),any());
        session=start(); naming.clear(); naming.save(player,session,"Buddy"); verify(pets,never()).rename(any(),any());
    }
    @Test void explicitRenameDenialAndDisabledNamingLeaveTheNativeMenuAlone() throws Exception {
        when(player.hasPermission(PetNaming.PERMISSION)).thenReturn(false); assertFalse(naming.interact(player,entity));
        when(player.hasPermission(PetNaming.PERMISSION)).thenReturn(true); plugin.getConfig().set("naming.enabled",false);
        assertFalse(naming.interact(player,entity)); assertTrue(tasks.isEmpty());
    }
    private InventoryClickEvent nativeMenuClick() {
        var event=mock(InventoryClickEvent.class); var view=mock(InventoryView.class);
        when(event.getWhoClicked()).thenReturn(player); when(event.getView()).thenReturn(view);
        when(view.getTitle()).thenReturn("Pet interactions");
        when(event.isCancelled()).thenReturn(true); // MCPets cancels every click before MONITOR.
        return event;
    }
    @Test void nativeRenameButtonUsesTheRgbDialogWithoutRequiringSneakingOrNativeColorPermission() throws Exception {
        when(player.isSneaking()).thenReturn(false);
        when(pets.pendingMenuRename(player,"Pet interactions")).thenReturn(pet,null);
        var event=nativeMenuClick();
        try (var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            naming.menuClick(event); naming.menuClick(event);
            verify(pets).cancelMenuRename(player); assertEquals(1,tasks.size()); tasks.removeFirst().run();
        }
        var session=org.mockito.ArgumentCaptor.forClass(PetNaming.Session.class);
        verify(naming).show(eq(player),session.capture());
        naming.save(player,session.getValue(),"&#54DAF4H&#54C8EBZ&#54B7E2c&#54A5D9g&#5493D1c");
        var saved=org.mockito.ArgumentCaptor.forClass(String.class); verify(pets).rename(eq(pet),saved.capture());
        assertEquals("HZcgc",org.bukkit.ChatColor.stripColor(saved.getValue()));
        assertFalse(saved.getValue().contains("&")); assertTrue(saved.getValue().contains("§x"));
        verify(player,never()).hasPermission("mcpets.color");
        verify(event,never()).setCancelled(anyBoolean()); verify(event,never()).setCurrentItem(any());
        naming.save(player,session.getValue(),"Changed"); verify(pets,times(1)).rename(eq(pet),anyString());
        assertTrue(errors.isEmpty());
    }
    @Test void unrelatedNativeActionsOtherOwnersAndRevokedPetsKeepTheirNativeFlow() throws Exception {
        var event=nativeMenuClick(); naming.menuClick(event);
        when(pets.pendingMenuRename(player,"Pet interactions")).thenReturn(pet);
        when(pets.owner(pet)).thenReturn(UUID.randomUUID()); naming.menuClick(event);
        when(pets.owner(pet)).thenReturn(owner); when(pets.present(pet)).thenReturn(false); naming.menuClick(event);
        verify(pets,never()).cancelMenuRename(any()); assertTrue(tasks.isEmpty()); assertTrue(errors.isEmpty());
    }
    @Test void disablingNamingOrDenyingRenameDoesNotConsumeNativeChatPrompts() throws Exception {
        var event=nativeMenuClick(); when(player.hasPermission(PetNaming.PERMISSION)).thenReturn(false);
        naming.menuClick(event);
        when(player.hasPermission(PetNaming.PERMISSION)).thenReturn(true); plugin.getConfig().set("naming.enabled",false);
        naming.menuClick(event);
        verify(pets,never()).pendingMenuRename(any(),any()); verify(pets,never()).cancelMenuRename(any());
        assertTrue(tasks.isEmpty());
    }
}
