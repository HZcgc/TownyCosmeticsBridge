package com.townyreborn.cratemodels;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.util.Vector;

final class CrateInteraction {
    private final Set<UUID> pending = new HashSet<UUID>();

    CrateInteraction() {
    }

    boolean queue(UUID uUID) {
        return this.pending.add(uUID);
    }

    boolean take(UUID uUID) {
        return this.pending.remove(uUID);
    }

    void clear() {
        this.pending.clear();
    }

    static boolean allowed(Player player, Block block, ItemStack itemStack, Plugin plugin, Plugin plugin2) throws EventException {
        PlayerInteractEvent playerInteractEvent = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, itemStack, block, BlockFace.UP, EquipmentSlot.HAND, new Vector(0.5, 0.8, 0.5));
        for (RegisteredListener registeredListener : PlayerInteractEvent.getHandlerList().getRegisteredListeners()) {
            Plugin plugin3 = registeredListener.getPlugin();
            if (plugin3 == plugin || plugin3 == plugin2 || !plugin3.isEnabled()) continue;
            registeredListener.callEvent((Event)playerInteractEvent);
        }
        return playerInteractEvent.useInteractedBlock() != Event.Result.DENY;
    }
}

