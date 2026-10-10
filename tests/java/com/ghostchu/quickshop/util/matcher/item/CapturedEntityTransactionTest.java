package com.ghostchu.quickshop.util.matcher.item;

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.QuickShopAPI;
import com.ghostchu.quickshop.api.inventory.*;
import com.ghostchu.quickshop.shop.SimpleInventoryTransaction;
import com.ghostchu.quickshop.util.Util;
import com.ghostchu.quickshop.util.logger.Log;
import dev.dejvokep.boostedyaml.YamlDocument;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.*;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ghostchu.quickshop.util.matcher.item.AnimalTestItems.*;

/** Uses upstream RemoveItemOperation, AddItemOperation and default removal with the patched transaction. */
class CapturedEntityTransactionTest {
    @Test void buyingSeveralBucketsTransfersEachStockedAnimalInsteadOfCloningTemplate() {
        runWithNativeOperations(() -> {
            var template=bucket("gold","{UUID:[I;99,0,0,0],Age:0,HuntingCooldown:400}");
            var first=bucket("gold","{UUID:[I;1,0,0,0],Age:0,HuntingCooldown:100}");
            var second=bucket("gold","{UUID:[I;2,0,0,0],Age:0,HuntingCooldown:200}");
            var shop=new MemoryInventory(3,first,second);
            var player=new MemoryInventory(3);
            assertTrue(new SimpleInventoryTransaction(shop,player,template,2).failSafeCommit());
            assertEquals(0,shop.count()); assertEquals(2,player.count());
            assertSameData(first,player.contents[0]); assertSameData(second,player.contents[1]);
            assertFalse(template.isSimilar(player.contents[0]));
            assertFalse(template.isSimilar(player.contents[1]));
        });
    }

    @Test void sellingCatchBallAnimalsKeepsEachOriginalCaptureIdAndHistory() {
        runWithNativeOperations(() -> {
            var template=captured("COW","{Age:0,Health:10.0f}","sample","Owner","T0","");
            var first=captured("COW","{Age:0,Health:7.0f}","one","PlayerA","T1","");
            var second=captured("COW","{Age:100,Health:9.0f}","two","PlayerB","T2","");
            var player=new MemoryInventory(3,first,second);
            var shop=new MemoryInventory(3);
            assertTrue(new SimpleInventoryTransaction(player,shop,template,2).failSafeCommit());
            assertEquals(0,player.count());assertEquals(2,shop.count());
            assertSameData(first,shop.contents[0]);assertSameData(second,shop.contents[1]);
            assertFalse(template.isSimilar(shop.contents[0]));
            assertFalse(template.isSimilar(shop.contents[1]));
        });
    }

    @Test void partiallyFullDestinationRestoresBothInventoriesAndExactAnimalIds() {
        runWithNativeOperations(() -> {
            var first=captured("COW","{Age:0}","one","A","T1","");
            var second=captured("COW","{Age:200}","two","B","T2","");
            var source=new MemoryInventory(3,first,second);
            var target=new MemoryInventory(1);
            var transaction=new SimpleInventoryTransaction(source,target,first,2);
            assertFalse(transaction.failSafeCommit());
            assertEquals(2,source.count());assertEquals(0,target.count());
            assertSameData(first,source.contents[0]);assertSameData(second,source.contents[1]);
            assertTrue(transaction.getProcessingStack().isEmpty());
            // A repeated failed attempt must not duplicate or consume anything.
            assertFalse(new SimpleInventoryTransaction(source,target,first,2).failSafeCommit());
            assertEquals(2,source.count());assertEquals(0,target.count());
        });
    }

    @Test void insufficientMatchingStockDoesNotTakeOtherColorOrDuplicateExistingDestination() {
        runWithNativeOperations(() -> {
            var gold=bucket("gold","{UUID:[I;1,2,3,4],Age:0}");
            var blue=bucket("blue","{UUID:[I;5,6,7,8],Age:0}");
            var existing=bucket("gold","{UUID:[I;9,10,11,12],Age:0}");
            var shop=new MemoryInventory(3,gold,blue);var player=new MemoryInventory(3,existing);
            assertFalse(new SimpleInventoryTransaction(shop,player,gold,2).failSafeCommit());
            assertEquals(2,shop.count());assertEquals(1,player.count());
            assertSameData(gold,shop.contents[0]);assertSameData(blue,shop.contents[1]);assertSameData(existing,player.contents[0]);
        });
    }

    @Test void buyingAnAnimalAgainMovesTheSameItemAndCannotRegenerateTheShopSample() {
        runWithNativeOperations(() -> {
            var sample=captured("COW","{}","sample","Owner","T0","");
            var real=captured("COW","{}","actual","Seller","T1","");
            var shop=new MemoryInventory(2,real);var player=new MemoryInventory(2);
            assertTrue(new SimpleInventoryTransaction(shop,player,sample,1).failSafeCommit());
            assertTrue(new SimpleInventoryTransaction(player,shop,sample,1).failSafeCommit());
            assertEquals(1,shop.count());assertEquals(0,player.count());assertSameData(real,shop.contents[0]);
            assertTrue(new SimpleInventoryTransaction(shop,player,sample,1).failSafeCommit());
            assertSameData(real,player.contents[0]);assertEquals(0,shop.count());
            assertFalse(new SimpleInventoryTransaction(shop,player,sample,1).failSafeCommit());
            assertEquals(1,player.count());assertSameData(real,player.contents[0]);
        });
    }

    @Test void partialRemovalFromAnExistingStackPreservesItsIdentityAndCount() {
        runWithNativeOperations(() -> {
            var animal=captured("COW","{}","real","A","T","");animal.setAmount(3);
            var shop=new MemoryInventory(3,animal);var player=new MemoryInventory(3);
            var template=captured("COW","{}","sample","B","T2","");
            assertTrue(new SimpleInventoryTransaction(shop,player,template,2).failSafeCommit());
            assertEquals(1,shop.count());assertEquals(2,player.count());
            assertSameData(animal,shop.contents[0]);assertSameData(animal,player.contents[0]);
        });
    }

    @Test void ordinaryItemsAndIntentionalUnlimitedAdminShopBehaviorRemainCompatible() {
        runWithNativeOperations(() -> {
            var diamond=item(Material.DIAMOND,"{id:\"minecraft:diamond\",count:1}",null);diamond.setAmount(10);
            var source=new MemoryInventory(2,diamond);var target=new MemoryInventory(2);
            assertTrue(new SimpleInventoryTransaction(source,target,diamond,5).failSafeCommit());
            assertEquals(5,source.count());assertEquals(5,target.count());
            assertTrue(new SimpleInventoryTransaction(null,target,diamond,2).failSafeCommit());
            assertEquals(7,target.count());
            assertTrue(new SimpleInventoryTransaction(target,null,diamond,3).failSafeCommit());
            assertEquals(4,target.count());
        });
    }

    private static void assertSameData(ItemStack expected,ItemStack actual) {
        assertNotNull(actual);assertArrayEquals(expected.serializeAsBytes(),actual.serializeAsBytes());
    }

    private static void runWithNativeOperations(Runnable test) {
        QuickShop plugin=mock(QuickShop.class);YamlDocument config=mock(YamlDocument.class);
        when(plugin.getConfig()).thenReturn(config);when(config.getBoolean("matcher.towny-animals-enabled",true)).thenReturn(true);
        when(plugin.getItemMatcher()).thenReturn(new BukkitItemMatcherImpl(plugin));
        PluginManager manager=mock(PluginManager.class);Plugin catchBall=mock(Plugin.class);
        when(manager.getPlugin("CatchBall")).thenReturn(catchBall);when(catchBall.isEnabled()).thenReturn(true);
        try(var bukkit=mockStatic(Bukkit.class);var qs=mockStatic(QuickShop.class);
            var api=mockStatic(QuickShopAPI.class);var util=mockStatic(Util.class);var log=mockStatic(Log.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            qs.when(QuickShop::getInstance).thenReturn(plugin);api.when(QuickShopAPI::getInstance).thenReturn(plugin);
            util.when(()->Util.serialize(any(ItemStack.class))).thenReturn("test-item");
            util.when(()->Util.getItemMaxStackSizes(any(ItemStack[].class))).thenAnswer(invocation->
                Arrays.stream((ItemStack[])invocation.getArgument(0)).mapToInt(ItemStack::getMaxStackSize).toArray());
            util.when(()->Util.getItemMaxStackSize(any(Material.class))).thenAnswer(invocation->
                invocation.getArgument(0)==Material.AXOLOTL_BUCKET?1:64);
            test.run();
        }
    }

    private static final class MemoryInventory implements InventoryWrapper {
        ItemStack[] contents;
        MemoryInventory(int size,ItemStack... items) {
            contents=new ItemStack[size];for(int i=0;i<items.length;i++)contents[i]=items[i].clone();
        }
        int count() { return Arrays.stream(contents).filter(Objects::nonNull).mapToInt(ItemStack::getAmount).sum(); }
        @Override public InventoryWrapperIterator iterator() { return InventoryWrapperIterator.ofItemStacks(contents); }
        @Override public ItemStack[] createSnapshot() {
            return Arrays.stream(contents).map(item->item==null?null:item.clone()).toArray(ItemStack[]::new);
        }
        @Override public boolean restoreSnapshot(ItemStack[] snapshot) { contents=snapshot;return true; }
        @Override public void clear() { Arrays.fill(contents,null); }
        @Override public void setContents(ItemStack[] items) { contents=items; }
        @Override public InventoryWrapperManager getWrapperManager() { return null; }
        @Override public InventoryHolder getHolder() { return null; }
        @Override public InventoryWrapperType getInventoryType() { return InventoryWrapperType.BUKKIT; }
        @Override public Location getLocation() { return null; }
        @Override public boolean isNeedUpdate() { return false; }
    }
}
