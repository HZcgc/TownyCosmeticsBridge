package com.townyreborn.staffconnector;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Off-thread handover of committed Journey audit receipts and read-only profile snapshots. */
final class JourneyInsights {
    static final String PLUGIN = "TownyRebornProgression";
    private final InsightsOutbox outbox;
    private final Logger log;
    private volatile Path root;
    private YamlConfiguration config = new YamlConfiguration();
    private long nextSnapshots;
    private final Set<String> warnings = new HashSet<>();
    private final Map<Path,String> profileStamps = new HashMap<>();
    private String catalogHash = "";

    JourneyInsights(InsightsOutbox outbox, Logger log) { this.outbox = outbox; this.log = log; }
    void configure(Path folder) { root = folder; }
    boolean audited() { Path folder = root; return folder != null && Files.isDirectory(folder.resolve("staff-audit")); }

    void read() {
        Path folder = root;
        if (folder == null) return;
        try {
            if (System.currentTimeMillis() >= nextSnapshots) {
                config = load(folder.resolve("config.yml"));
                snapshots(folder);
                nextSnapshots = System.currentTimeMillis() + 60_000;
            }
            Path audit = folder.resolve("staff-audit");
            if (!Files.isDirectory(audit)) return;
            List<Path> receipts;
            try (var paths = Files.walk(audit, 2)) {
                receipts = paths.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().matches("[0-9]+\\.(yml|pending)"))
                    .sorted().limit(80).toList();
            }
            for (Path path : receipts) {
                try { transfer(folder, path); }
                catch(NoSuchFileException raced) { /* The Journey writer may have finalised the same receipt. */ }
                catch(Exception error) { warn(path.toString(), "A Journey audit receipt is unavailable; it remains on disk for review."); }
            }
        } catch (Exception error) { warn("read", "Journey insights retained after " + error.getClass().getSimpleName()); }
    }

    private void warn(String key, String message) { if (warnings.add(key)) log.warning(message); }

    private void transfer(Path folder, Path path) throws Exception {
        YamlConfiguration receipt = load(path);
        String uuid = receipt.getString("uuid", "");
        if (receipt.getInt("schema") != 1 || !uuid.equals(path.getParent().getFileName().toString())
            || !UUID.fromString(uuid).toString().equals(uuid)) throw new IllegalArgumentException("Invalid Journey audit identity");
        long sequence = receipt.getLong("sequence");
        String id = "journey-" + uuid + "-" + sequence;
        if (sequence <= 0 || !id.equals(receipt.getString("event-id"))) throw new IllegalArgumentException("Invalid Journey audit sequence");
        if (path.toString().endsWith(".pending")) {
            YamlConfiguration profile = load(folder.resolve("journeydata").resolve(uuid + ".yml"));
            if (profile.getLong("staff-audit-sequence", -1) != sequence) return;
            Path committed = path.resolveSibling(sequence + ".yml");
            try { Files.move(path, committed, StandardCopyOption.REPLACE_EXISTING); }
            catch (NoSuchFileException raced) { return; }
            path = committed;
        }
        List<Map<String, Object>> events = events(receipt, config);
        if (!outbox.addDurable(events)) return;
        // Only audit receipts are acknowledged. Gameplay profiles, purchases and migration journals are read-only.
        Files.deleteIfExists(path);
    }

    static YamlConfiguration load(Path file) throws Exception {
        if (Files.size(file) > 1_000_000) throw new IllegalArgumentException("Journey file is too large");
        YamlConfiguration result = new YamlConfiguration();
        result.loadFromString(Files.readString(file));
        return result;
    }

    static int level(long points, YamlConfiguration config) {
        int maximum = Math.max(1, Math.min(10000, config.getInt("journey.steps", 116))), level = 1;
        long cost = Math.max(1, config.getLong("journey.points-first", 150)), increase = Math.max(0, config.getLong("journey.points-increase", 30));
        for (long threshold = cost; level < maximum && points >= threshold; threshold += cost) { level++; cost += increase; }
        return level;
    }

    private static Set<String> strings(ConfigurationSection section, String key) { return new TreeSet<>(section.getStringList(key)); }
    private static Set<String> added(ConfigurationSection before, ConfigurationSection after, String key) {
        Set<String> values = strings(after, key); values.removeAll(strings(before, key)); return values;
    }

    static List<Map<String, Object>> events(YamlConfiguration receipt, YamlConfiguration config) {
        ConfigurationSection before = receipt.getConfigurationSection("before"), after = receipt.getConfigurationSection("after");
        if (before == null || after == null) throw new IllegalArgumentException("Missing Journey audit state");
        List<Map<String, Object>> rows = new ArrayList<>();
        long delta = after.getLong("points") - before.getLong("points");
        long history = Math.max(0, after.getLong("history-credit-points") - before.getLong("history-credit-points"));
        if (history > 0) add(rows, receipt, "journey_history_restored", "historical", history,
            Map.of("source", after.getString("history-credit-source", "recorded backup")));
        long remaining = Math.max(0, delta - history);
        ConfigurationSection daily = after.getConfigurationSection("daily-awards");
        if (daily != null) for (String source : new TreeSet<>(daily.getKeys(false))) {
            long old = before.getString("day", "").equals(after.getString("day", "")) ? before.getLong("daily-awards." + source) : 0;
            long points = Math.min(remaining, Math.max(0, daily.getLong(source) - old));
            if (points > 0) { add(rows, receipt, "journey_jp", source, points, Map.of("day_utc", after.getString("day", ""))); remaining -= points; }
        }
        if (remaining > 0 || delta < 0) add(rows, receipt, "journey_adjustments", "recorded adjustment", delta < 0 ? delta : remaining, Map.of("source", "profile adjustment; not attributed to gameplay"));
        int oldLevel = level(before.getLong("points"), config), newLevel = level(after.getLong("points"), config);
        if (newLevel != oldLevel) add(rows, receipt, history > 0 ? "journey_levels_restored" : "journey_levels", "level " + newLevel,
            newLevel - oldLevel, Map.of("from_level", oldLevel, "to_level", newLevel, "rank", config.getString("journey.titles." + newLevel, "Level " + newLevel)));
        for (String perk : added(before, after, "unlocked-perks")) add(rows, receipt, "journey_unlocks", perk, 1, Map.of("historical", history > 0));
        for (String perk : added(before, after, "owned-perks")) {
            boolean paid = perk.equals(before.getString("pending-purchase.perk")) && "DEBITED".equals(before.getString("pending-purchase.phase"));
            add(rows, receipt, paid ? "journey_perk_purchases" : "journey_ownership_restored", perk, 1, Map.of("confirmed_purchase", paid));
        }
        for (String perk : strings(before, "owned-perks")) if (!strings(after, "owned-perks").contains(perk))
            add(rows, receipt, "journey_ownership_removed", perk, 1, Map.of());
        for (String gift : added(before, after, "claimed-gifts")) {
            boolean delivered=gift.equals(before.getString("pending-gift"));
            add(rows, receipt, delivered?"journey_rewards_claimed":"journey_reward_receipts_restored", gift, 1,
                delivered?Map.of("confirmed_delivery",true,"commands",before.getStringList("pending-gift-commands"),"town",before.getString("pending-gift-town","")):Map.of("confirmed_delivery",false));
            if (delivered) {
                if (before.getDouble("pending-gift-money") > 0) add(rows, receipt, "journey_reward_money", gift, before.getDouble("pending-gift-money"), Map.of());
                if (before.getInt("pending-gift-town-blocks") > 0) add(rows, receipt, "journey_reward_chunks", gift, before.getInt("pending-gift-town-blocks"), Map.of());
            }
        }
        String oldPending = before.getString("pending-purchase.perk", "") + ":" + before.getString("pending-purchase.phase", ""),
            pending = after.getString("pending-purchase.perk", "") + ":" + after.getString("pending-purchase.phase", "");
        if(!oldPending.equals(pending)&&"DEBITED".equals(after.getString("pending-purchase.phase"))
            &&after.getString("pending-purchase.perk","").equals(before.getString("pending-purchase.perk"))
            &&"PREPARED".equals(before.getString("pending-purchase.phase")))
            add(rows,receipt,"journey_money_spent",after.getString("pending-purchase.perk"),after.getDouble("pending-purchase.price"),Map.of("amount_kind","saved debit receipt; delivery tracked separately"));
        if (!oldPending.equals(pending)) add(rows, receipt, "journey_purchase_receipt", after.getString("pending-purchase.perk", before.getString("pending-purchase.perk", "unknown")), 0,
            Map.of("from", oldPending, "to", pending, "price", after.getDouble("pending-purchase.price", before.getDouble("pending-purchase.price"))));
        if (rows.isEmpty()) add(rows, receipt, "journey_profile_commit", "profile", 0, Map.of("initial", receipt.getBoolean("initial")));
        return rows;
    }

    private static void add(List<Map<String, Object>> rows, YamlConfiguration receipt, String metric, String dimension, double value, Map<String, Object> details) {
        ConfigurationSection after = receipt.getConfigurationSection("after");
        Map<String, Object> expanded = new LinkedHashMap<>(details);
        expanded.put("commit", receipt.getString("event-id")); expanded.put("points_after", after.getLong("points"));
        expanded.put("sequence",receipt.getLong("sequence"));
        String key = receipt.getString("event-id") + ":" + metric + ":" + dimension;
        rows.add(new LinkedHashMap<>(Map.of("type", "game.event", "event_id", "journey-" + GameInsights.hash(key),
            "occurred_at", receipt.getString("occurred-at"), "plugin", PLUGIN, "metric", metric,
            "dimension", dimension, "value", value, "uuid", receipt.getString("uuid"), "username", after.getString("name", ""), "details", expanded)));
    }

    private boolean snapshot(String key, Map<String, Object> data, String time) {
        return outbox.add(Map.of("type", "integration.snapshot", "plugin", PLUGIN, "snapshot_key", key, "data", data, "occurred_at", time));
    }

    private void snapshots(Path folder) throws Exception {
        String time = Instant.now().toString();
        Map<String, Object> catalogue = new LinkedHashMap<>();
        for (String key : List.of("journey", "sources", "perks", "gifts")) {
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section != null) catalogue.put(key, plain(section));
        }
        catalogue.put("audit_available", audited());
        String signature=GameInsights.hash(Json.encode(catalogue));
        if(!signature.equals(catalogHash)&&snapshot("catalog", catalogue, time))catalogHash=signature;
        Path profiles = folder.resolve("journeydata");
        if (!Files.isDirectory(profiles)) return;
        try (var paths = Files.list(profiles)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().matches("[0-9a-f-]{36}\\.yml")).sorted().toList()) {
                String stamp=Files.getLastModifiedTime(path).toString()+":"+Files.size(path);
                if(stamp.equals(profileStamps.get(path)))continue;
                YamlConfiguration profile;
                try { profile = load(path); }
                catch (Exception error) { warn(path.toString(), "A Journey profile could not be read; previous HQ snapshot retained."); continue; }
                String uuid = path.getFileName().toString().replace(".yml", "");
                if (profile.getInt("schema") != 2 || !uuid.equals(profile.getString("uuid"))) continue;
                Map<String, Object> data = new LinkedHashMap<>();
                for (String key : List.of("uuid", "name", "points", "active-minutes", "journey-active-minutes", "owned-perks", "unlocked-perks", "claimed-gifts", "history-credit-points", "history-credit-source", "history-credit-at", "day", "daily-awards", "pending-purchase", "pending-gift", "staff-audit-sequence")) {
                    Object value = profile.get(key);
                    if (value instanceof ConfigurationSection section) value = plain(section);
                    if (value != null) data.put(key, value);
                }
                int level = level(profile.getLong("points"), config);
                data.put("level", level); data.put("rank", config.getString("journey.titles." + level, "Level " + level));
                data.put("profile_saved_at", Files.getLastModifiedTime(path).toInstant().toString());
                if(snapshot("player." + uuid, data, time))profileStamps.put(path,stamp);
            }
        }
    }

    static Map<String, Object> plain(ConfigurationSection section) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) { Object value = section.get(key); values.put(key, value instanceof ConfigurationSection nested ? plain(nested) : value); }
        return values;
    }
}
