package net.townyreborn.journey;

import java.util.*;
import org.bukkit.configuration.ConfigurationSection;

/** Text comes from the same curve and rates used by the point-awarding code. */
final class ProgressInfo {
    static List<String> summary(Journey journey, State state) {
        int level = journey.level(state.points);
        List<String> lines = new ArrayList<>(List.of("&7Level: &a" + level + " &8| &r" + journey.styledTitle(level)));
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
        lines.add("&aClick for activities and daily limits.");
        return lines;
    }

    static List<String> legend() {
        return List.of("&aGreen: earlier, completed level", "&eYellow: your current Journey level", "&cRed: future, locked level", "",
            "&7Reached rewards can be claimed later.", "&7Purchases never block the next level.");
    }
    static List<String> explanation(Journey journey, ConfigurationSection config) {
        long time = 10 * config.getLong("sources.playtime.points-per-minute", 1);
        long fish = 5 * config.getLong("sources.fishing.points-per-catch", 3);
        long skill = config.getLong("sources.mcmmo.points-per-level", 5);
        return List.of("&7Journey Points combine your activities.", "&7They all fill the same progress bar.", "",
            "&dExample: combine activities", "&710 active minutes &8= &a" + time + " JP", "&75 fish catches &8= &a" + fish + " JP",
            "&71 new mcMMO level &8= &a" + skill + " JP", "&7Combined: &a" + (time + fish + skill) + " JP", "",
            "&7Level 2 needs &f" + journey.threshold(2) + " total JP&7.", "&7Level 3 needs &f" + journey.threshold(3) + " total JP&7.",
            "&7Points are cumulative and never spent.", "&7Perks use money; rewards are optional.",
            config.getBoolean("sources.quests.enabled", false) ? "&7Each finished quest also earns JP, within its daily cap." : "&7Normal Quests give their own rewards, not JP.");
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
        boolean today = DailyTime.today().equals(state.day);
        long earned = today ? state.daily.getOrDefault(id, 0L) : 0;
        List<String> lines = new ArrayList<>(List.of("&f" + action, "&7Reward: &a+" + rate + " JP", "&7Daily limit: &f" + cap + " JP",
            "&7Earned today: &f" + earned + " JP", "&7Available today: &e" + Math.max(0, cap - earned) + " JP"));
        lines.add("");
        lines.add("&7Daily reset: &f00:00 UTC &8(server time)");
        if (id.equals("mcmmo") || id.equals("jobs")) {
            lines.add("&7Only new personal highest levels count.");
            lines.add("&7Existing levels are your starting baseline.");
        } else if (id.equals("quests")) {
            lines.add("&7Each actual completion counts, including repeats.");
            lines.add("&7Old completed quests are not credited again.");
        } else if (id.equals("exploration")) lines.add("&7Each new biome counts once per player.");
        else if (id.equals("advancements")) lines.add("&7Each advancement once; recipes excluded.");
        return lines;
    }
}
