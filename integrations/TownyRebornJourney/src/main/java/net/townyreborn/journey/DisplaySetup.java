package net.townyreborn.journey;

import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.YamlConfiguration;

/** Change only player-format fields in the installed configs; preserve channels and other settings. */
final class DisplaySetup {
    static final String SMALL_MCMmo = "%string_replaceCharacters_small-numbers_{mcmmo_power_level}%";
    private static final Pattern MMO = Pattern.compile("%(?:string_replaceCharacters_[^%]*\\{(?:mcmmo_power_level|reborn_level)\\}|(?:mcmmo_power_level|reborn_level))%");
    private static final Pattern JOURNEY = Pattern.compile("%reborn_(?:chat_prefix|tab_prefix|title|short_title|abbreviation)%");

    static void run(RebornPlugin plugin) throws Exception {
        if (!plugin.getConfig().getBoolean("display.auto-configure", true)) return;
        Path plugins = plugin.getDataFolder().toPath().getParent();
        update(plugin, plugins.resolve("TAB/groups.yml"), true);
        update(plugin, plugins.resolve("Towny/settings/ChatConfig.yml"), false);
        update(plugin, plugins.resolve("Towny/settings/chatconfig.yml"), false);
        Path nametag = plugins.resolve("LyttleNametag/config.yml");
        if (Files.isRegularFile(nametag)) {
            YamlConfiguration config = LegacyCleanup.load(nametag);
            if (nametag(config)) save(plugin, nametag, config);
        }
    }

    private static void update(RebornPlugin plugin, Path file, boolean tab) throws Exception {
        if (!Files.isRegularFile(file)) return;
        YamlConfiguration config = LegacyCleanup.load(file);
        boolean changed = tab ? tab(config) : chat(config);
        if (!changed) return;
        save(plugin, file, config);
    }

    private static void save(RebornPlugin plugin, Path file, YamlConfiguration config) throws Exception {
        Path backup = plugin.getDataFolder().toPath().resolve("legacy-backup/display-v3").resolve(file.getParent().getFileName()).resolve(file.getFileName());
        Files.createDirectories(backup.getParent());
        if (!Files.exists(backup)) Files.copy(file, backup);
        Store.atomic(file, config.saveToString());
        plugin.getLogger().info("Updated Journey player format in " + file.getFileName() + "; other settings preserved.");
    }

    static boolean tab(YamlConfiguration config) {
        boolean changed = false;
        for (String key : new ArrayList<>(config.getKeys(true))) {
            if (!key.endsWith(".tabprefix") || !config.isString(key)) continue;
            String parent = key.substring(0, key.length() - "tabprefix".length());
            String oldSuffix = config.getString(parent + "tabsuffix", "");
            String ownSuffix = " &f" + SMALL_MCMmo + "&r";
            String suffix = oldSuffix.startsWith(ownSuffix) ? oldSuffix.substring(ownSuffix.length()) : MMO.matcher(oldSuffix).replaceAll("");
            String prefix = " %luckperms_prefix%&r %reborn_tab_prefix%&r";
            String newSuffix = " &f" + SMALL_MCMmo + "&r" + suffix;
            if (!prefix.equals(config.getString(key)) || !newSuffix.equals(oldSuffix) ||
                !"%player_displayname%".equals(config.getString(parent + "customtabname"))) {
                config.set(key, prefix); config.set(parent + "tabsuffix", newSuffix);
                config.set(parent + "customtabname", "%player_displayname%");
                changed = true;
            }
        }
        return changed;
    }

    static boolean chat(YamlConfiguration config) {
        boolean changed = false;
        for (String key : new ArrayList<>(config.getKeys(true))) {
            if (!key.startsWith("channel_formats.") && !key.startsWith("worlds.") && !key.startsWith("modify_chat.worlds.")) continue;
            if (!config.isString(key)) continue;
            String original = config.getString(key);
            String updated = chatFormat(original);
            if (!updated.equals(original)) { config.set(key, updated); changed = true; }
        }
        return changed;
    }

    static String chatFormat(String original) {
        if (!original.contains("{msg}") || (!original.contains("{playername}") && !original.contains("{modplayername}")))
            return original; // Preserve unsupported/custom formatter lines.
        if (original.contains("%reborn_chat_prefix%") && original.contains("{modplayername}") && original.contains("mcmmo_power_level"))
            return original;
        var matcher = MMO.matcher(original);
        String mcmmo = matcher.find() ? matcher.group().replace("reborn_level", "mcmmo_power_level") : SMALL_MCMmo;
        String format = MMO.matcher(original).replaceAll("");
        format = JOURNEY.matcher(format).replaceAll("");
        String prefixToken = format.contains("{permprefix}") ? "{permprefix}" :
            format.contains("%luckperms_prefix%") ? "%luckperms_prefix%" :
            format.contains("{permgroupprefix}") ? "{permgroupprefix}" : "";
        if (!prefixToken.isEmpty()) format = format.replace(prefixToken, prefixToken + " %reborn_chat_prefix%");
        else format = format.replace("{modplayername}", "{permprefix} %reborn_chat_prefix%{modplayername}")
            .replace("{playername}", "{permprefix} %reborn_chat_prefix%{playername}");
        format = format.replace("{playername}", "{modplayername}");
        // Keep the channel tag, Towny tags, permission suffix, separator and message format.
        return format.replace("{modplayername}", "{modplayername}&r &f" + mcmmo + "&r");
    }

    static boolean nametag(YamlConfiguration config) {
        String format = "<light_purple>%townyadvanced_town_unformatted%</light_purple> <green>%townyadvanced_nation_unformatted%</green>\n"
            + "<luckperms_prefix> <light_purple>%reborn_title%</light_purple>\n<displayname>";
        if (format.equals(config.getString("nametag"))) return false;
        config.set("nametag", format);
        return true;
    }
}
