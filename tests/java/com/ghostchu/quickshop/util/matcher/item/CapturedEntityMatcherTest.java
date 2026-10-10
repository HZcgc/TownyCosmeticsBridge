package com.ghostchu.quickshop.util.matcher.item;

import com.ghostchu.quickshop.QuickShop;
import dev.dejvokep.boostedyaml.YamlDocument;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ghostchu.quickshop.util.matcher.item.AnimalTestItems.*;
import static com.ghostchu.quickshop.util.matcher.item.AnimalNbt.*;

class CapturedEntityMatcherTest {
    @Test void twoUnmodifiedVanillaShopSamplesMatchDespiteHuntingCooldown() {
        // Anonymous, unnamed vanilla buckets from the supplied database; no player/shop IDs.
        String first="H4sIAAAAAAAA/2WOLQ/CMBCGX+hGRglBYCDBI/G4BcQEGjtKW0ZD1yNbx8e/5xYEglOXu+f9kIDAZK+iOtqmdRSA+TrD0BksahesbtQlbtWLPEVfnjt9s1Eg1dSFCGAgITXVdwo2xDbD8k+zeajGKYaTiryRWP2Ir1vJShffpeEOCWYFG7tQ7YhpenKdfoZTAZFXtt9TjAqrfLzmJ87HmM8HYiPTPz9fkGg/zwAAAA==";
        String second="H4sIAAAAAAAA/2WOvQ8BQRDFH4ucVWpI9Eq97hKFQq09Y3edjb0duZsT/nsjCoX2vd/7sIDBbEdCx9B2kTMwXxcYRo9FE3NwLV1kS09OLKk69+4WxGDsuM8CYGBhHTd3ziFLV2D5l9k8qI2k8Kjm5C1WP+LbVmkyyqvy+sHAlHXQXowx2QdKci1PuoKpygdW3H/MN/FLmKe1AAAA";
        var item1=new SavedItem(Material.AXOLOTL_BUCKET,Base64.getDecoder().decode(first),null,1);
        var item2=new SavedItem(Material.AXOLOTL_BUCKET,Base64.getDecoder().decode(second),null,1);
        assertFalse(item1.isSimilar(item2));
        assertEquals(true,CapturedEntityItemMatcher.matches(item1,item2));
    }

    @Test void axolotlRuntimeIdentityHealthCooldownAndBreedingTicksMayVary() {
        var first = bucket("gold", "{UUID:[I;1,2,3,4],Age:0,Health:14.0f,HuntingCooldown:525,AgeLocked:0b}");
        var second = bucket("gold", "{UUID:[I;5,6,7,8],Age:600,Health:10.0f,PersistenceRequired:1b}");
        byte[] before1 = first.serializeAsBytes(), before2 = second.serializeAsBytes();
        assertEquals(true, CapturedEntityItemMatcher.matches(first, second));
        assertEquals(true, CapturedEntityItemMatcher.matches(second, first));
        assertArrayEquals(before1, first.serializeAsBytes()); assertArrayEquals(before2, second.serializeAsBytes());
    }

    @Test void rareBlueColorIsSeparateAndGrowingBabiesMatchOnlyBabies() {
        assertEquals(false, CapturedEntityItemMatcher.matches(bucket("blue", "{Age:0}"), bucket("gold", "{Age:0}")));
        assertEquals(true, CapturedEntityItemMatcher.matches(bucket("gold", "{Age:-23946}"), bucket("gold", "{Age:-100}")));
        assertEquals(false, CapturedEntityItemMatcher.matches(bucket("gold", "{Age:-1}"), bucket("gold", "{Age:0}")));
    }

    @Test void axolotlNameEnchantsPluginDataAndUnknownPropertiesStayStrict() {
        String base = "{id:\"minecraft:axolotl_bucket\",count:1,components:{\"minecraft:axolotl/variant\":\"gold\","
            + "\"minecraft:bucket_entity_data\":{Age:0},\"minecraft:custom_name\":{text:%s},"
            + "\"minecraft:custom_data\":{PublicBukkitValues:{\"itemsadder:id\":%s}}}}";
        var named = item(Material.AXOLOTL_BUCKET, base.formatted("\"Goldie\"", "\"pet-a\""), null);
        var renamed = item(Material.AXOLOTL_BUCKET, base.formatted("\"Other\"", "\"pet-a\""), null);
        var otherPlugin = item(Material.AXOLOTL_BUCKET, base.formatted("\"Goldie\"", "\"pet-b\""), null);
        assertEquals(false, CapturedEntityItemMatcher.matches(named, renamed));
        assertEquals(false, CapturedEntityItemMatcher.matches(named, otherPlugin));
        assertEquals(false, CapturedEntityItemMatcher.matches(bucket("gold", "{Age:0,NoAI:1b}"), bucket("gold", "{Age:0}")));
    }

    @Test void defaultAdultAndDefaultLucyAreCanonicalButAgeLockRemainsSignificant() {
        var plain = item(Material.AXOLOTL_BUCKET, "{id:\"minecraft:axolotl_bucket\",count:20,components:{}}", null);
        assertEquals(true, CapturedEntityItemMatcher.matches(plain, item(Material.AXOLOTL_BUCKET,
            "{id:\"minecraft:axolotl_bucket\",count:1,components:{\"minecraft:axolotl/variant\":\"lucy\",\"minecraft:bucket_entity_data\":{Age:0}}}",null)));
        assertEquals(false, CapturedEntityItemMatcher.matches(bucket("gold", "{Age:0,AgeLocked:1b}"), bucket("gold", "{Age:0}")));
    }

    @Test void differentMaterialsAndOrdinaryHeadsAreNotCapturedAnimals() {
        var head = item(Material.PLAYER_HEAD, "{id:\"minecraft:player_head\",components:{}}", null);
        assertEquals(false, CapturedEntityItemMatcher.matches(bucket("gold", "{}"), head));
        assertEquals(false, CapturedEntityItemMatcher.matches(head, bucket("gold", "{}")));
        assertNull(CapturedEntityItemMatcher.matches(head, head.clone()));
    }

    @Test void catchBallIdentityAndConfiguredCaptureHistoryMayVaryWithoutChangingItems() throws Exception {
        var first = captured("COW", "{UUID:[I;1,2,3,4],Age:0,Health:10.0f,Pos:[1.0d,2.0d,3.0d],Variant:\"temperate\","
            + "Spigot.ticksLived:51,Paper.OriginWorld:[I;1,2,3,4],fall_distance:0.0d,invulnerable_time:10,current_impulse_context_reset_grace_time:5}",
            "animal-one", "FirstPlayer", "2026-10-10, 12:00:00", "");
        var second = captured("COW", "{Variant:\"temperate\",Health:7.0f,Age:500,UUID:[I;5,6,7,8],"
            + "Spigot.ticksLived:8000,Paper.OriginWorld:[I;9,10,11,12],fall_distance:3.0d,invulnerable_time:0,current_impulse_context_reset_grace_time:0}",
            "animal-two", "SecondPlayer", "2026-10-11, 12:00:00", "");
        byte[] before1=first.serializeAsBytes(), before2=second.serializeAsBytes();
        assertEquals(CapturedEntityItemMatcher.comparison(first, true, TEMPLATES),
            CapturedEntityItemMatcher.comparison(second, true, TEMPLATES));
        PluginManager manager=mock(PluginManager.class); Plugin catchBall=mock(Plugin.class);
        when(manager.getPlugin("CatchBall")).thenReturn(catchBall); when(catchBall.isEnabled()).thenReturn(true);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            assertEquals(true, CapturedEntityItemMatcher.matches(first, second));
            assertEquals(true, CapturedEntityItemMatcher.matches(second, first));
        }
        assertArrayEquals(before1, first.serializeAsBytes()); assertArrayEquals(before2, second.serializeAsBytes());
    }

    @Test void speciesVariantsOwnerAndNestedIdentifiersStayStrict() throws Exception {
        var cow=captured("COW","{Age:0,Variant:\"temperate\"}","one","A","T","");
        for (var different:List.of(
            captured("PIG","{Age:0,Variant:\"temperate\"}","two","B","T",""),
            captured("COW","{Age:0,Variant:\"cold\"}","two","B","T",""),
            captured("COW","{Age:0,Variant:\"temperate\",Owner:[I;1,2,3,4]}","two","B","T",""))) {
            assertNotEquals(CapturedEntityItemMatcher.comparison(cow,true,TEMPLATES),
                CapturedEntityItemMatcher.comparison(different,true,TEMPLATES));
        }
        assertNotEquals(parse("{attributes:[{id:\"a\",modifiers:[{UUID:[I;1,2,3,4]}]}]}"),
            parse("{attributes:[{id:\"a\",modifiers:[{UUID:[I;5,6,7,8]}]}]}"));
    }

    @Test void villagerProfessionOffersAndInventoryAreNotInterchangeable() throws Exception {
        var first=captured("VILLAGER","{Age:0,VillagerData:{profession:\"minecraft:librarian\",level:2},"
            + "Offers:{Recipes:[{sell:{id:\"minecraft:enchanted_book\",components:{enchantment:\"mending\"}}}]}}","1","A","T","");
        var second=captured("VILLAGER","{Age:0,VillagerData:{profession:\"minecraft:librarian\",level:2},"
            + "Offers:{Recipes:[{sell:{id:\"minecraft:enchanted_book\",components:{enchantment:\"sharpness\"}}}]}}","2","B","T","");
        assertNotEquals(CapturedEntityItemMatcher.comparison(first,true,TEMPLATES),
            CapturedEntityItemMatcher.comparison(second,true,TEMPLATES));
        var horseA=captured("HORSE","{Age:0,Items:[{id:\"minecraft:diamond\",count:1}]}","1","A","T","");
        var horseB=captured("HORSE","{Age:0,Items:[{id:\"minecraft:dirt\",count:1}]}","2","B","T","");
        assertNotEquals(CapturedEntityItemMatcher.comparison(horseA,true,TEMPLATES),
            CapturedEntityItemMatcher.comparison(horseB,true,TEMPLATES));
    }

    @Test void remainingBallUsesOtherPluginPdcAndUnrecognizedLoreStayStrict() throws Exception {
        var first=captured("COW","{}","1","A","T",",\"catchball:remaining_uses\":3,\"other:reward\":\"rare\"");
        var used=captured("COW","{}","2","B","T",",\"catchball:remaining_uses\":1,\"other:reward\":\"rare\"");
        var altered=captured("COW","{}","2","B","T",",\"catchball:remaining_uses\":3,\"other:reward\":\"basic\"");
        assertNotEquals(CapturedEntityItemMatcher.comparison(first,true,TEMPLATES),
            CapturedEntityItemMatcher.comparison(used,true,TEMPLATES));
        assertNotEquals(CapturedEntityItemMatcher.comparison(first,true,TEMPLATES),
            CapturedEntityItemMatcher.comparison(altered,true,TEMPLATES));
        assertNotEquals(CapturedEntityItemMatcher.comparison(first,true,List.of()),
            CapturedEntityItemMatcher.comparison(captured("COW","{}","2","B","T",
                ",\"catchball:remaining_uses\":3,\"other:reward\":\"rare\""),true,List.of()));
        assertNull(CapturedEntityItemMatcher.normalizeHistoryLine("Price: 100", "Price: 200"));
    }

    @Test void malformedOversizedAndUnknownEntityDataFailClosed() {
        var invalid=new SavedItem(Material.AXOLOTL_BUCKET,new byte[]{1,2,3},null,1);
        assertEquals(false,CapturedEntityItemMatcher.matches(invalid,bucket("gold","{}")));
        assertThrows(IllegalArgumentException.class,()->parse("{UUID:[I;1,2,3,4],Offers:{invalid"));
        assertThrows(IllegalArgumentException.class,()->parse("{x:1,x:2}"));
        assertThrows(IllegalArgumentException.class,()->parse("{x:"+quote("a".repeat(MAX_BYTES))+"}"));
        assertThrows(IllegalArgumentException.class,()->parse("{x:"+"[".repeat(70)+"0"+"]".repeat(70)+"}"));
        var broken=captured("COW","{x:","one","A","T","");
        var other=captured("COW","{}","two","B","T","");
        PluginManager manager=mock(PluginManager.class);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            assertEquals(false,CapturedEntityItemMatcher.matches(broken,other));
        }
    }

    @Test void binaryAndSnbtReaderRetainAllTypesAndIgnoreOnlyCompoundOrdering() throws Exception {
        String value="{b:1b,s:2s,i:3,l:4L,f:5.0f,d:6.0d,bytes:[B;1b,-1b],ints:[I;1,2],longs:[L;1L,2L],"
            + "list:[{name:'a',v:1},{v:2,name:'b'}],quoted:\"a\\\\b\\\"c\",flag:true,empty:{}}";
        Tag parsed=parse(value);
        assertEquals(parsed,read(encode(parsed)));
        assertEquals(parse("{a:1,b:2}"),parse("{b:2,a:1}"));
        assertNotEquals(parse("{x:1}"),parse("{x:1b}"));
        assertNotEquals(parse("{x:[I;1]}"),parse("{x:[L;1L]}"));
    }

    @Test void currentConfiguredBukkitMatcherUsesAnimalRuleAndOrdinaryItemsRemainStrict() {
        QuickShop plugin=mock(QuickShop.class); YamlDocument config=mock(YamlDocument.class);
        when(plugin.getConfig()).thenReturn(config);when(config.getBoolean("matcher.towny-animals-enabled",true)).thenReturn(true);
        var matcher=new BukkitItemMatcherImpl(plugin);
        assertTrue(matcher.matches(bucket("gold","{Age:0,HuntingCooldown:525}"),bucket("gold","{Age:0}")));
        assertFalse(matcher.matches(bucket("gold","{Age:0}"),bucket("blue","{Age:0}")));
        assertTrue(matcher.matches(null,null)); assertFalse(matcher.matches(null,bucket("gold","{}")));
        assertTrue(matcher.matches(item(Material.DIAMOND,"{id:\"minecraft:diamond\",count:1}",null),
            item(Material.DIAMOND,"{id:\"minecraft:diamond\",count:1}",null)));
    }
}
