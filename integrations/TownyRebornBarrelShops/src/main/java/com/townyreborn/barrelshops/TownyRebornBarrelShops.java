package com.townyreborn.barrelshops;

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.event.economy.ShopPurchaseEvent;
import com.ghostchu.quickshop.api.event.inventory.ShopInventoryCalculateEvent;
import com.ghostchu.quickshop.api.event.inventory.ShopInventoryChangedEvent;
import com.ghostchu.quickshop.api.shop.Shop;
import com.ghostchu.quickshop.api.shop.permission.BuiltInShopPermission;
import com.ghostchu.quickshop.shop.inventory.BukkitInventoryWrapper;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class TownyRebornBarrelShops
extends JavaPlugin
implements Listener {
    private static final String PREFIX = "\u00a7d\u00a7lTOWNY\u00a7a\u00a7lREBORN \u00a78\u00bb \u00a77";
    private QuickShop quickShop;
    private Plugin biggerBarrels;
    private final Map<Inventory, Location> inventoryLocations = new IdentityHashMap<Inventory, Location>();
    private final Map<Location, ItemStack[]> previousContents = new HashMap<Location, ItemStack[]>();
    private final Map<Location, String> refreshErrors = new HashMap<Location, String>();
    private final Set<Location> pending = new HashSet<Location>();
    private boolean refreshScheduled;

    public void onEnable() {
        try {
            if (!Boolean.TRUE.equals(BukkitInventoryWrapper.class.getMethod("townyRebornBiggerBarrelsFix", new Class[0]).invoke(null, new Object[0]))) {
                throw new IllegalStateException("Patched QuickShop inventory adapter is missing.");
            }
            this.quickShop = QuickShop.getInstance();
            this.biggerBarrels = this.getServer().getPluginManager().getPlugin("BiggerBarrels");
            if (this.quickShop == null || this.biggerBarrels == null) {
                throw new IllegalStateException("QuickShop or BiggerBarrels has not started.");
            }
            this.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)this);
            this.getServer().getScheduler().runTaskTimer((Plugin)this, () -> {
                int n = this.refreshAll(false);
            }, 1L, 100L);
            this.getLogger().info("Connected QuickShop-Hikari to BiggerBarrels' 54-slot stock. Stock refresh is active.");
        }
        catch (ReflectiveOperationException | RuntimeException exception) {
            this.getLogger().severe("Barrel shop fix could not start: " + exception.getMessage());
            this.getServer().getPluginManager().disablePlugin((Plugin)this);
        }
    }

    public void onDisable() {
        this.inventoryLocations.clear();
        this.previousContents.clear();
        this.refreshErrors.clear();
        this.pending.clear();
    }

    private boolean loadedBarrel(Shop<?, ?> shop) {
        Location location = shop.bukkitLocation();
        return location.getWorld() != null && location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4) && location.getBlock().getType() == Material.BARREL;
    }

    private Inventory inventoryFor(Shop<?, ?> shop) {
        Location location = shop.bukkitLocation();
        Inventory inventory;
        try {
            inventory = (Inventory)this.biggerBarrels.getClass().getMethod("getBarrel", org.bukkit.block.Block.class).invoke(this.biggerBarrels, location.getBlock());
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("BiggerBarrels inventory API is unavailable", error);
        }
        this.inventoryLocations.put(inventory, location.clone());
        return inventory;
    }

    private Shop<?, ?> shopFor(Inventory inventory) {
        Location location = this.inventoryLocations.get(inventory);
        if (location != null) {
            Shop shop = this.quickShop.getShopManager().getShop(location);
            if (shop != null && this.loadedBarrel(shop) && this.inventoryFor(shop) == inventory) {
                return shop;
            }
            this.inventoryLocations.remove(inventory);
        }
        if (inventory.getSize() != 54 || inventory.getLocation() != null || inventory.getHolder() != null) {
            return null;
        }
        for (Shop shop : this.quickShop.getShopManager().getLoadedShops()) {
            if (!this.loadedBarrel(shop) || this.inventoryFor(shop) != inventory) continue;
            return shop;
        }
        return null;
    }

    private int refreshAll(boolean bl) {
        HashSet<Location> hashSet = new HashSet<Location>();
        int n = 0;
        for (Shop shop : this.quickShop.getShopManager().getLoadedShops()) {
            if (!this.loadedBarrel(shop)) continue;
            hashSet.add(shop.bukkitLocation());
            if (!this.safeRefresh(shop, bl)) continue;
            ++n;
        }
        this.previousContents.keySet().retainAll(hashSet);
        this.refreshErrors.keySet().retainAll(hashSet);
        this.inventoryLocations.values().removeIf(location -> !hashSet.contains(location));
        return n;
    }

    private boolean safeRefresh(Shop<?, ?> shop, boolean bl) {
        Location location = shop.bukkitLocation();
        try {
            boolean bl2 = this.refresh(shop, bl);
            this.refreshErrors.remove(location);
            return bl2;
        }
        catch (RuntimeException runtimeException) {
            String string = runtimeException.getClass().getSimpleName() + ": " + runtimeException.getMessage();
            if (!string.equals(this.refreshErrors.put(location.clone(), string))) {
                this.getLogger().warning("Could not refresh barrel shop at " + String.valueOf(location) + ": " + string);
            }
            return false;
        }
    }

    private boolean refresh(Shop<?, ?> shop, boolean bl) {
        if (!this.loadedBarrel(shop)) {
            return false;
        }
        Location location = shop.bukkitLocation();
        Inventory inventory = this.inventoryFor(shop);
        ItemStack[] objectArray = inventory.getStorageContents();
        if (!bl && Arrays.equals(this.previousContents.get(location), objectArray)) {
            return false;
        }
        ItemStack[] itemStackArray = new ItemStack[objectArray.length];
        int n = 0;
        while (n < objectArray.length) {
            itemStackArray[n] = objectArray[n] == null ? null : objectArray[n].clone();
            ++n;
        }
        n = shop.getRemainingStock();
        int n2 = shop.getRemainingSpace();
        new ShopInventoryCalculateEvent(shop, n2, n).callEvent();
        shop.setSignText();
        this.previousContents.put(location.clone(), itemStackArray);
        return true;
    }

    private void queue(Shop<?, ?> shop) {
        if (shop == null || !this.loadedBarrel(shop)) {
            return;
        }
        this.pending.add(shop.bukkitLocation().clone());
        if (this.refreshScheduled) {
            return;
        }
        this.refreshScheduled = true;
        this.getServer().getScheduler().runTask((Plugin)this, () -> {
            this.refreshScheduled = false;
            HashSet<Location> hashSet = new HashSet<Location>(this.pending);
            this.pending.clear();
            for (Location location : hashSet) {
                Shop currentShop = this.quickShop.getShopManager().getShop(location);
                if (currentShop == null) continue;
                this.safeRefresh(currentShop, true);
            }
        });
    }

    private void queue(Inventory inventory) {
        this.queue(this.shopFor(inventory));
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onClick(InventoryClickEvent inventoryClickEvent) {
        this.queue(inventoryClickEvent.getInventory());
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onDrag(InventoryDragEvent inventoryDragEvent) {
        this.queue(inventoryDragEvent.getInventory());
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent inventoryCloseEvent) {
        this.queue(inventoryCloseEvent.getInventory());
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onMove(InventoryMoveItemEvent inventoryMoveItemEvent) {
        this.queue(inventoryMoveItemEvent.getSource());
        this.queue(inventoryMoveItemEvent.getDestination());
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onShopChanged(ShopInventoryChangedEvent shopInventoryChangedEvent) {
        Location location = shopInventoryChangedEvent.getInventoryWrapper().getLocation();
        if (location != null) {
            this.queue(this.quickShop.getShopManager().getShop(location));
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onPurchase(ShopPurchaseEvent shopPurchaseEvent) {
        this.queue(shopPurchaseEvent.getShop());
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onOpen(InventoryOpenEvent inventoryOpenEvent) {
        Shop<?, ?> shop = this.shopFor(inventoryOpenEvent.getInventory());
        if (shop == null) {
            return;
        }
        HumanEntity humanEntity = inventoryOpenEvent.getPlayer();
        if (humanEntity instanceof Player) {
            Player player = (Player)humanEntity;
            if (this.quickShop.getConfig().getBoolean("shop.lock").booleanValue() && !player.hasPermission("quickshop.other.open") && !shop.playerAuthorize(player.getUniqueId(), BuiltInShopPermission.ACCESS_INVENTORY)) {
                inventoryOpenEvent.setCancelled(true);
                player.sendMessage("\u00a7d\u00a7lTOWNY\u00a7a\u00a7lREBORN \u00a78\u00bb \u00a77You cannot open this shop's stock.");
                return;
            }
        }
        this.queue(shop);
    }

    public boolean onCommand(CommandSender commandSender, Command command, String string, String[] stringArray) {
        if (!commandSender.hasPermission("townyreborn.barrelshops.admin")) {
            commandSender.sendMessage("\u00a7d\u00a7lTOWNY\u00a7a\u00a7lREBORN \u00a78\u00bb \u00a77You do not have permission to use this command.");
            return true;
        }
        if (stringArray.length == 1 && stringArray[0].equalsIgnoreCase("refresh")) {
            commandSender.sendMessage("\u00a7d\u00a7lTOWNY\u00a7a\u00a7lREBORN \u00a78\u00bb \u00a77Updated \u00a7a" + this.refreshAll(true) + " \u00a77loaded barrel shops.");
        } else {
            commandSender.sendMessage("\u00a7d\u00a7lTOWNY\u00a7a\u00a7lREBORN \u00a78\u00bb \u00a77Barrel shop fix is active. Connected inventories: \u00a7a" + this.inventoryLocations.size() + "\u00a77. Use \u00a7a/trbarrelshops refresh \u00a77to update signs.");
        }
        return true;
    }
}

