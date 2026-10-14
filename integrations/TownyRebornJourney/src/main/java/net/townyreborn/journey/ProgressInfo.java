package net.townyreborn.journey;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;

/** Text comes from the same curve and rates used by the point-awarding code. */
final class ProgressInfo {
    static List<String> summary(Journey journey, State state) {
        int level = journey.level(state.points);
        List<String> lines = new ArrayList<>(List.of("&7Level: &a" + level + " &8| &f" + journey.title(level)));
        if (level == journey.steps) {
            lines.add("&aJourney complete: 100%");
            lines.add("&7Remaining: &a0%");
        } else {
            long earned = state.points - journey.threshold(level);
            long needed = journey.threshold(level + 1) - journey.threshold(level);
            lines.add("&7Current level: &e" + level);
            lines.add("&7Working towards: &fLevel " + (level + 1));
            lines.add("&7Completed: &a" + journey.percent(state.points) + "%");
            lines.add("&7Still remaining: &e" + journey.remainingPercent(state.points) + "%");
            lines.add("&7This step: &f" + earned + " / " + needed + " JP");
            lines.add("&7You need &e" + journey.remaining(state.points, level + 1) + " more Journey Points&7.");
        }
        lines.add("");
        lines.add("&7Choose any of the point-earning activities.");
        lines.add("&7All activities add to the same JP total.");
        lines.add("&7Your level increases automatically.");
        lines.add("&7Click for rates, limits and your stats.");
        return lines;
    }

    static List<String> legend() {
        return List.of("&aGreen: earlier, completed level", "&eYellow: your current Journey level", "&cRed: future, locked level", "",
            "&7Reached rewards can be claimed later.", "&7Purchases never block the next level.");
    }
    static List<String> explanation() {
        return List.of("&7Journey Points combine your activities.", "&7They all fill the same progress bar.", "",
            "&fExample:", "&710 active minutes &8= &a10 JP", "&75 fish catches &8= &a15 JP", "&71 new mcMMO level &8= &a5 JP",
            "&7Total: &a30 JP &8→ &fLevel 2", "", "&7Level 3 needs 75 JP in total.",
            "&7Perks cost game money, never JP.", "&7Claiming rewards is optional.");
    }
    static List<String> requirement(Journey journey, State state, int target) {
        long missing = journey.remaining(state.points, target);
        return List.of("&7Required total: &f" + journey.threshold(target) + " JP", "&7Your total: &a" + state.points + " JP",
            missing == 0 ? "&aMilestone reached." : "&7Still needed: &e" + missing + " JP",
            "&7Earn JP by playing, levelling skills,", "&7fishing, exploring or advancements.");
    }
    static List<String> source(ConfigurationSection config, State state, String id, String rateKey, String action) {
        long rate = config.getLong("sources." + id + "." + rateKey, 0);
        long cap = config.getLong("sources." + id + ".daily-cap", 0);
        boolean today = LocalDate.now(ZoneId.of("Europe/Berlin")).toString().equals(state.day);
        long earned = today ? state.daily.getOrDefault(id, 0L) : 0;
        List<String> lines = new ArrayList<>(List.of("&f" + action, "&7Reward: &a+" + rate + " JP", "&7Daily limit: &f" + cap + " JP",
            "&7Earned today: &f" + earned + " JP", "&7Available today: &e" + Math.max(0, cap - earned) + " JP"));
        if (id.equals("mcmmo") || id.equals("jobs") || id.equals("quests")) {
            lines.add("&7Only new personal highest levels count.");
            lines.add("&7Existing levels are your starting baseline.");
        } else if (id.equals("exploration")) lines.add("&7Each new biome counts once per player.");
        else if (id.equals("advancements")) lines.add("&7Each advancement once; recipes excluded.");
        return lines;
    }
}
