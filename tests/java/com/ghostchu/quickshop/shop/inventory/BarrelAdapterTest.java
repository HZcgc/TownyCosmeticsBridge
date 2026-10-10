package com.ghostchu.quickshop.shop.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import com.ghostchu.quickshop.QuickShop;

class BarrelAdapterTest {
    public interface BarrelPlugin extends Plugin { Inventory getBarrel(Block block); }

    @Test void allStockOperationsUseFiftyFourSlotsAndLocationStaysPhysical() {
        Inventory physical=mock(Inventory.class),expanded=mock(Inventory.class);
        Location location=mock(Location.class);Block block=mock(Block.class);
        when(location.getWorld()).thenReturn(mock(org.bukkit.World.class));when(location.getBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.BARREL);when(physical.getLocation()).thenReturn(location);
        when(expanded.getContents()).thenReturn(new ItemStack[54]);
        ItemStack[] expandedContents=new ItemStack[54];
        ItemStack lastItem=mock(ItemStack.class);expandedContents[53]=lastItem;
        when(expanded.getStorageContents()).thenReturn(expandedContents);
        BarrelPlugin barrels=mock(BarrelPlugin.class);when(barrels.isEnabled()).thenReturn(true);
        when(barrels.getBarrel(block)).thenReturn(expanded);
        PluginManager manager=mock(PluginManager.class);when(manager.getPlugin("BiggerBarrels")).thenReturn(barrels);
        try(var bukkit=mockStatic(Bukkit.class);var qs=mockStatic(QuickShop.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            qs.when(QuickShop::getInstance).thenReturn(mock(QuickShop.class));
            BukkitInventoryWrapper wrapper=new BukkitInventoryWrapper(physical);
            assertEquals(54,wrapper.createSnapshot().length);assertSame(location,wrapper.getLocation());
            ItemStack[] stock=new ItemStack[54];
            wrapper.clear();wrapper.restoreSnapshot(stock);wrapper.setContents(stock);wrapper.addItem();
            var iterator=wrapper.iterator();
            int slots=0;ItemStack last=null;
            while(iterator.hasNext()) { last=iterator.next(); slots++; }
            assertEquals(54,slots);assertSame(lastItem,last);iterator.remove();assertNull(expandedContents[53]);
            verify(expanded).clear();verify(expanded).setContents(stock);verify(expanded).setStorageContents(same(stock));
            verify(expanded).setStorageContents(same(expandedContents));verify(expanded).addItem();
            verify(physical,never()).getContents();verify(physical,never()).clear();
            assertTrue(BukkitInventoryWrapper.townyRebornBiggerBarrelsFix());
        }
    }

    @Test void ordinaryInventoryKeepsNativeOperations() {
        Inventory physical=mock(Inventory.class);when(physical.getContents()).thenReturn(new ItemStack[27]);
        try(var qs=mockStatic(QuickShop.class)) {
            qs.when(QuickShop::getInstance).thenReturn(mock(QuickShop.class));
            var wrapper=new BukkitInventoryWrapper(physical);
            assertEquals(27,wrapper.createSnapshot().length);wrapper.clear();verify(physical).clear();
        }
    }
}
