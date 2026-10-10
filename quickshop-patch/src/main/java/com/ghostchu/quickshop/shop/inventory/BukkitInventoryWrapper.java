package com.ghostchu.quickshop.shop.inventory;

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.inventory.InventoryWrapper;
import com.ghostchu.quickshop.api.inventory.InventoryWrapperIterator;
import com.ghostchu.quickshop.api.inventory.InventoryWrapperManager;
import com.ghostchu.quickshop.api.inventory.InventoryWrapperType;
import com.ghostchu.quickshop.common.util.JsonUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BukkitInventoryWrapper
implements InventoryWrapper {
    private final Inventory inventory;
    private final InventoryWrapperManager manager;
    private final Supplier<String> eigenCodeProvider;
    private final String eigenCode;

    public BukkitInventoryWrapper(@NotNull Inventory inventory) {
        this(inventory, () -> null);
    }

    public BukkitInventoryWrapper(@NotNull Inventory inventory, Supplier<String> eigenCodeProvider) {
        this.inventory = inventory;
        this.manager = QuickShop.getInstance().getInventoryWrapperManager();
        this.eigenCodeProvider = eigenCodeProvider;
        this.eigenCode = eigenCodeProvider.get();
    }

    /** Compatibility marker verified by TownyRebornBarrelShops before it starts. */
    public static boolean townyRebornBiggerBarrelsFix() {
        return true;
    }

    /** Keep the native location/holder, but route every stock operation to the expanded inventory. */
    private Inventory stockInventory() {
        Location location = this.inventory.getLocation();
        if (location == null || location.getWorld() == null || location.getBlock().getType() != Material.BARREL) {
            return this.inventory;
        }
        Plugin barrels = Bukkit.getPluginManager().getPlugin("BiggerBarrels");
        if (barrels == null || !barrels.isEnabled()) return this.inventory;
        try {
            Object result = barrels.getClass().getMethod("getBarrel", org.bukkit.block.Block.class)
                    .invoke(barrels, location.getBlock());
            if (result instanceof Inventory expanded) return expanded;
            throw new IllegalStateException("BiggerBarrels did not provide its stock inventory");
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not access the BiggerBarrels stock inventory", error);
        }
    }

    @Override
    @NotNull
    public InventoryWrapperIterator iterator() {
        return InventoryWrapperIterator.ofBukkitInventory(this.stockInventory());
    }

    @Override
    public void clear() {
        this.stockInventory().clear();
    }

    @Override
    @NotNull
    public ItemStack[] createSnapshot() {
        ItemStack[] content = this.stockInventory().getContents();
        ItemStack[] snapshot = new ItemStack[content.length];
        for (int i = 0; i < content.length; ++i) {
            snapshot[i] = content[i] != null ? content[i].clone() : null;
        }
        return snapshot;
    }

    @Override
    @NotNull
    public InventoryWrapperManager getWrapperManager() {
        return this.manager;
    }

    @Override
    public InventoryHolder getHolder() {
        return this.inventory.getHolder();
    }

    @Override
    @NotNull
    public InventoryWrapperType getInventoryType() {
        return InventoryWrapperType.BUKKIT;
    }

    @Override
    @Nullable
    public Location getLocation() {
        return this.inventory.getLocation();
    }

    @Override
    public boolean isValid() {
        if (this.inventory.getHolder(false) != null) {
            return true;
        }
        return this.inventory instanceof InventoryHolder;
    }

    @Override
    public boolean isNeedUpdate() {
        return !Objects.equals(this.eigenCode, this.eigenCodeProvider.get());
    }

    @Override
    public boolean restoreSnapshot(@NotNull ItemStack[] snapshot) {
        this.stockInventory().setContents(snapshot);
        return true;
    }

    @Override
    @NotNull
    public Map<Integer, ItemStack> addItem(ItemStack ... itemStacks) {
        return this.stockInventory().addItem(itemStacks);
    }

    @Override
    public void setContents(ItemStack[] itemStacks) {
        this.stockInventory().setStorageContents(itemStacks);
    }

    public String toString() {
        HashMap<String, String> map = new HashMap<String, String>();
        map.put("inventory", this.inventory.toString());
        map.put("inventoryType", this.inventory.getClass().getName());
        return JsonUtil.getGson().toJson(map);
    }
}

