package net.townyreborn.journey;

import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Upgrade the beta catalogue while keeping curves, source rates and paid ownership IDs. */
final class ConfigUpgrade {
    static final int VERSION = 3;

    static YamlConfiguration upgrade(YamlConfiguration current, YamlConfiguration defaults) throws Exception {
        if (current.getInt("catalogue-version", 0) >= VERSION) return current;
        YamlConfiguration updated = new YamlConfiguration();
        updated.loadFromString(current.saveToString());
        updated.set("catalogue-version", VERSION);
        if (!updated.isConfigurationSection("journey.short-titles"))
            updated.createSection("journey.short-titles", defaults.getConfigurationSection("journey.short-titles").getValues(false));
        if (!updated.isConfigurationSection("display"))
            updated.createSection("display", defaults.getConfigurationSection("display").getValues(false));
        Map<Integer, List<String>> oldByLevel = new HashMap<>();
        ConfigurationSection oldGifts = current.getConfigurationSection("gifts");
        if (oldGifts != null) for (String id : oldGifts.getKeys(false)) {
            int level = oldGifts.getInt(id + ".level", 0);
            oldByLevel.computeIfAbsent(level, key -> new ArrayList<>()).add(id);
            oldByLevel.get(level).addAll(oldGifts.getStringList(id + ".legacy-ids"));
        }
        updated.set("gifts", null);
        ConfigurationSection newGifts = defaults.getConfigurationSection("gifts");
        for (String id : newGifts.getKeys(false)) {
            ConfigurationSection gift = newGifts.getConfigurationSection(id);
            if (gift.getInt("level") > updated.getInt("journey.steps", 116)) continue;
            updated.createSection("gifts." + id, gift.getValues(false));
            Set<String> aliases = new LinkedHashSet<>(gift.getStringList("legacy-ids"));
            aliases.addAll(oldByLevel.getOrDefault(gift.getInt("level"), List.of()));
            aliases.remove(id);
            updated.set("gifts." + id + ".legacy-ids", List.copyOf(aliases));
        }
        updated.set("perks.daily_supporter", null);
        for (String id : List.of("shops50", "shops100", "shops150", "shops200", "shops240", "warps2", "warps3")) {
            if (!updated.isConfigurationSection("perks." + id)) continue;
            for (String field : List.of("name", "help-commands"))
                updated.set("perks." + id + "." + field, defaults.get("perks." + id + "." + field));
        }
        new Journey(updated); // Validate the complete result before replacing the user's file.
        return updated;
    }
}
