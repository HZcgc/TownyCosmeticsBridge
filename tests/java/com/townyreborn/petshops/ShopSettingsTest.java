package com.townyreborn.petshops;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ShopSettingsTest {
    @Test void oldSettingsMigrateEvenWhenTheNewJarSuppliesVersionTwoDefaults() {
        var shipped=new YamlConfiguration(); shipped.set("presentation-version",2); shipped.set("npc.render-distance",64);
        var config=new YamlConfiguration(); config.setDefaults(shipped);
        config.set("defaults.ingame-price",50000); config.set("defaults.store-price",10.99);
        config.set("defaults.store-url","https://shop.example.com/package/custom");
        config.set("prefix","My custom prefix ");
        config.set("hologram.lines",List.of("Custom name: {pet}","{ingame_price} / {store_price}","&fRechtsklick zum Freischalten","My extra line"));
        assertTrue(ShopSettings.upgrade(config));
        assertEquals(75000,config.getDouble("defaults.ingame-price")); assertEquals(10.99,config.getDouble("defaults.store-price"));
        assertEquals("https://shop.example.com/package/custom",config.getString("defaults.store-url"));
        assertEquals("My custom prefix ",config.getString("prefix"));
        assertEquals(List.of("Custom name: {pet}","{ingame_price} / {store_price}",ShopSettings.HOLOGRAM.get(2),ShopSettings.HOLOGRAM.get(3),"My extra line"),config.getStringList("hologram.lines"));
        assertTrue(config.contains("npc.render-distance",true)); assertEquals(64,ShopSettings.renderDistance(config));
        assertEquals(8,config.getDouble("npc.look-at-player-range"));
        config.set("defaults.ingame-price",50000); config.set("hologram.lines",List.of("A later custom text"));
        assertFalse(ShopSettings.upgrade(config)); assertEquals(50000,config.getDouble("defaults.ingame-price"));
        assertEquals(List.of("A later custom text"),config.getStringList("hologram.lines"));
    }
    @Test void customPricesAndTrackingRangesSurviveAndMissingHologramsUseEnglish() {
        var config=new YamlConfiguration(); config.set("defaults.ingame-price",88000); config.set("npc.render-distance",128);
        assertTrue(ShopSettings.upgrade(config));
        assertEquals(88000,config.getDouble("defaults.ingame-price")); assertEquals(128,ShopSettings.renderDistance(config));
        assertEquals(ShopSettings.HOLOGRAM,config.getStringList("hologram.lines"));
        config.set("npc.render-distance",4); assertEquals(16,ShopSettings.renderDistance(config));
        config.set("npc.render-distance",400); assertEquals(256,ShopSettings.renderDistance(config));
        assertEquals("75,000",ShopMenu.money(75000)); assertEquals("10.99",ShopMenu.euros(10.99));
    }
    @Test void announcementAndNamingDefaultsUpgradeExistingVersionTwoConfigsWithoutResettingCustomSettings() {
        var config=new YamlConfiguration(); config.set("presentation-version",2);
        config.set("defaults.ingame-price",88000); config.set("hologram.lines",List.of("Custom holo"));
        assertTrue(ShopSettings.upgrade(config)); assertTrue(config.getBoolean("announcements.enabled"));
        assertEquals(ShopSettings.ANNOUNCEMENT,config.getString("announcements.message"));
        assertTrue(config.getBoolean("naming.enabled")); assertEquals(16,config.getInt("naming.max-length"));
        assertEquals(88000,config.getDouble("defaults.ingame-price")); assertEquals(List.of("Custom holo"),config.getStringList("hologram.lines"));
        config.set("announcements.enabled",false); config.set("announcements.message","Custom: {player}/{pet}");
        config.set("naming.enabled",false); config.set("naming.max-length",12);
        assertFalse(ShopSettings.upgrade(config)); assertFalse(config.getBoolean("announcements.enabled"));
        assertEquals("Custom: {player}/{pet}",config.getString("announcements.message"));
        assertFalse(config.getBoolean("naming.enabled")); assertEquals(12,config.getInt("naming.max-length"));
    }
}
