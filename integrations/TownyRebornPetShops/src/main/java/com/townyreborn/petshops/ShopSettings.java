package com.townyreborn.petshops;

import java.util.*;
import org.bukkit.configuration.ConfigurationSection;

/** One-time upgrade of the shipped settings; individual admin edits survive reloads. */
final class ShopSettings {
    static final String ANNOUNCEMENT="&a{player} &7has purchased &d{pet}&7!";
    static final List<String> HOLOGRAM = List.of("&d{pet}", "&a{ingame_price} $ &8| &a{store_price} €",
        "&fLeft-click to view buying options", "&7Right-click to interact");

    private ShopSettings() {}

    static boolean upgrade(ConfigurationSection config) {
        boolean changed=false;
        for (var entry:Map.of("announcements.enabled",true,"announcements.message",ANNOUNCEMENT,
                "naming.enabled",true,"naming.max-length",16).entrySet())
            if (!config.contains(entry.getKey(),true)) { config.set(entry.getKey(),entry.getValue()); changed=true; }
        if (config.contains("presentation-version", true) && config.getInt("presentation-version", 0) >= 2) return changed;
        if (!config.contains("defaults.ingame-price", true) || config.getDouble("defaults.ingame-price", 75000) == 50000)
            config.set("defaults.ingame-price", 75000.0);
        List<String> lines = new ArrayList<>(config.getStringList("hologram.lines"));
        int legacy = lines.indexOf("&fRechtsklick zum Freischalten");
        if (legacy >= 0) {
            lines.set(legacy, HOLOGRAM.get(2));
            lines.add(legacy + 1, HOLOGRAM.get(3));
            config.set("hologram.lines", lines);
        } else if (lines.isEmpty()) config.set("hologram.lines", HOLOGRAM);
        for (var entry : Map.of("npc.render-distance", 64, "npc.look-at-player-range", 8.0,
                "npc.update-interval-ticks", 4, "interaction.cooldown-seconds", 3.0).entrySet())
            if (!config.contains(entry.getKey(), true)) config.set(entry.getKey(), entry.getValue());
        config.set("presentation-version", 2);
        return true;
    }

    static int renderDistance(ConfigurationSection config) {
        return Math.clamp(config.getInt("npc.render-distance", 64), 16, 256);
    }
}
