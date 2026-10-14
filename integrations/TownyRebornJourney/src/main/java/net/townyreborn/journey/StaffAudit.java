package net.townyreborn.journey;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;

/** Write-ahead audit receipts. A receipt is transferable only after its profile sequence commits. */
final class StaffAudit {
    private static final List<String> FIELDS = List.of("uuid", "name", "points", "active-minutes",
        "journey-active-minutes", "progress-revision", "history-credit-revision", "history-credit-points",
        "history-credit-source", "owned-perks", "unlocked-perks", "claimed-gifts", "day", "daily-awards",
        "pending-purchase", "pending-gift", "pending-gift-money", "pending-gift-town-blocks", "pending-gift-town", "pending-gift-commands");
    private final Path root;
    StaffAudit(Path folder) { root = folder.resolve("staff-audit"); }

    private static Map<String, Object> snapshot(YamlConfiguration profile) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String field : FIELDS) {
            Object value = profile.get(field);
            if (value instanceof org.bukkit.configuration.ConfigurationSection section) value = new java.util.TreeMap<>(section.getValues(true));
            if (value != null) result.put(field, normalise(value));
        }
        return result;
    }

    private static Object normalise(Object value) {
        if(value instanceof Number number) {
            double numeric=number.doubleValue();
            if(numeric==Math.rint(numeric)&&numeric>=Long.MIN_VALUE&&numeric<=Long.MAX_VALUE)return number.longValue();
            return numeric;
        }
        if(value instanceof Map<?,?> map) {
            Map<String,Object> result=new java.util.TreeMap<>();
            map.forEach((key,item)->result.put(String.valueOf(key),normalise(item)));return result;
        }
        if(value instanceof List<?> list)return list.stream().map(StaffAudit::normalise).toList();
        return value;
    }

    record Prepared(long sequence, Path pending) { }

    private void recover(UUID uuid, YamlConfiguration profile) throws IOException {
        Path folder = root.resolve(uuid.toString());
        if (!Files.isDirectory(folder)) return;
        long committed = profile == null ? 0 : profile.getLong("staff-audit-sequence", 0);
        try (var paths = Files.list(folder)) {
            for (Path file : paths.filter(p -> p.getFileName().toString().endsWith(".pending")).toList()) {
                long sequence = Long.parseLong(file.getFileName().toString().replace(".pending", ""));
                if (sequence == committed) commit(new Prepared(sequence, file));
                else Files.move(file, file.resolveSibling(sequence + ".uncommitted"), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /** Prepare before the profile write; a failed profile write never becomes completed activity. */
    Prepared prepare(UUID uuid, YamlConfiguration before, YamlConfiguration after) throws IOException {
        recover(uuid, before);
        Map<String, Object> old = before == null ? Map.of() : snapshot(before), current = snapshot(after);
        if (old.equals(current)) return null;
        Path folder = root.resolve(uuid.toString()), counter = folder.resolve("sequence.txt");
        long previous = before == null ? 0 : before.getLong("staff-audit-sequence", 0);
        if (Files.isRegularFile(counter)) {
            try { previous = Math.max(previous, Long.parseLong(Files.readString(counter).trim())); }
            catch (NumberFormatException error) { throw new IOException("Invalid Staff audit counter; preserve the counter", error); }
        }
        long sequence = Math.addExact(previous, 1);
        Store.atomic(counter, Long.toString(sequence));
        YamlConfiguration receipt = new YamlConfiguration();
        receipt.set("schema", 1);
        receipt.set("event-id", "journey-" + uuid + "-" + sequence);
        receipt.set("uuid", uuid.toString());
        receipt.set("sequence", sequence);
        receipt.set("occurred-at", Instant.now().toString());
        receipt.set("initial", before == null);
        receipt.createSection("before", old);
        receipt.createSection("after", current);
        Path pending = folder.resolve(sequence + ".pending");
        Store.atomic(pending, receipt.saveToString());
        return new Prepared(sequence, pending);
    }

    void commit(Prepared prepared) throws IOException {
        if (prepared == null) return;
        Files.move(prepared.pending(), prepared.pending().resolveSibling(prepared.sequence() + ".yml"), StandardCopyOption.REPLACE_EXISTING);
    }
}
