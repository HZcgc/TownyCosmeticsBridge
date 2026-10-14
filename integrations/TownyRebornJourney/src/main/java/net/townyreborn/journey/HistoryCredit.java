package net.townyreborn.journey;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Restore earned pre-reset JP once, preserving current points and all delivery receipts. */
final class HistoryCredit {
    static final long LIMIT = 1_000_000_000_000L;
    private final Path root;
    private final Journey journey;
    HistoryCredit(Path root, Journey journey) { this.root = root; this.journey = journey; }
    record Proof(long points, long minutes, String source) {}
    record Receipt(UUID uuid, String revision, String phase, long beforePoints, long afterPoints,
                   long beforeMinutes, long afterMinutes, long originalPoints, String source, String beforeHash, long created) {
        long credit() { return afterPoints - beforePoints; }
        long minutes() { return afterMinutes - beforeMinutes; }
    }
    private static YamlConfiguration read(Path path) throws Exception {
        if (!Files.isRegularFile(path)) throw new IOException("Historical progress record is not a file");
        YamlConfiguration c = new YamlConfiguration(); c.loadFromString(Files.readString(path)); return c;
    }
    private static long counter(YamlConfiguration c, String key) throws IOException {
        if (c.contains(key) && !(c.get(key) instanceof Number)) throw new IOException("Invalid historical counter: " + key);
        long n = c.getLong(key, 0);
        if (n < 0 || n > LIMIT || c.contains(key) && ((Number)c.get(key)).doubleValue() != n)
            throw new IOException("Invalid historical counter: " + key);
        return n;
    }
    private Proof nativeProof(Path path, UUID id) throws Exception {
        var c = read(path);
        if (c.getInt("schema") != 2 || !c.contains("points") || !id.toString().equals(c.getString("uuid"))
            || !c.getString("progress-revision", "").isEmpty() || !c.getString("history-credit-revision", "").isEmpty())
            throw new IOException("Historical native profile does not prove pre-reset progress");
        return new Proof(counter(c, "points"), counter(c, "active-minutes"), "reset-native");
    }
    private Proof legacyProof(Path path, UUID id, String source) throws Exception {
        var c = read(path); long level = counter(c, "level");
        if (level < 1 || level > 10000 || c.contains("schema") || !c.getString("progress-revision", "").isEmpty()
            || c.contains("uuid") && !id.toString().equals(c.getString("uuid")))
            throw new IOException("Invalid legacy progress profile");
        long minutes = counter(c, "active-minutes"), quests = counter(c, "quest-points");
        long points = Math.min(LIMIT, journey.legacyPerks.legacyPoints((int)level, minutes, quests));
        return new Proof(points, minutes, source);
    }
    Proof proof(UUID id) throws Exception {
        if (!journey.progressRevision.isEmpty()) {
            Path backup = root.resolve("migration-backups").resolve(journey.progressRevision);
            Path nativeFile = backup.resolve("journeydata").resolve(id + ".yml");
            if (Files.exists(nativeFile)) return nativeProof(nativeFile, id);
            Path legacyFile = backup.resolve("playerdata").resolve(id + ".yml");
            if (Files.exists(legacyFile)) return legacyProof(legacyFile, id, "reset-legacy");
        }
        Path legacyFile = root.resolve("playerdata").resolve(id + ".yml");
        return Files.exists(legacyFile) ? legacyProof(legacyFile, id, "legacy-fallback") : new Proof(0, 0, "none");
    }
    private Path receiptFile(UUID id) { return root.resolve("history-credits").resolve(id + ".yml"); }
    boolean alreadyCredited(State state) throws Exception {
        return !state.historyCreditRevision.isEmpty() || Files.exists(receiptFile(state.uuid)) && readReceipt(state.uuid).phase.equals("COMPLETED");
    }
    private static String hash(Path profile) throws Exception {
        return Files.exists(profile) ? HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(profile))) : "absent";
    }
    private Receipt readReceipt(UUID id) throws Exception {
        var c = read(receiptFile(id));
        for (String key : List.of("before-points","after-points","before-journey-minutes","after-journey-minutes","original-points"))
            if (!c.contains(key)) throw new IOException("Incomplete historical credit receipt");
        String revision = c.getString("revision", ""), phase = c.getString("phase", ""), beforeHash = c.getString("before-profile-sha256", "");
        if (c.getInt("schema") != 1 || !id.toString().equals(c.getString("uuid")) || !revision.matches("[a-z0-9_-]{1,80}")
            || !Set.of("PREPARED", "COMPLETED").contains(phase) || !(beforeHash.equals("absent") || beforeHash.matches("[a-f0-9]{64}")))
            throw new IOException("Invalid historical credit receipt");
        Receipt r = new Receipt(id, revision, phase, counter(c,"before-points"), counter(c,"after-points"),
            counter(c,"before-journey-minutes"), counter(c,"after-journey-minutes"), counter(c,"original-points"),
            c.getString("source", ""), beforeHash, c.getLong("created"));
        if (r.afterPoints < r.beforePoints || r.afterMinutes < r.beforeMinutes || r.credit() > r.originalPoints || r.source.isEmpty() || r.created <= 0)
            throw new IOException("Invalid historical credit amount");
        return r;
    }
    void writeReceipt(Receipt r, String phase) throws IOException {
        var c = new YamlConfiguration(); c.set("schema",1); c.set("uuid",r.uuid.toString()); c.set("revision",r.revision); c.set("phase",phase);
        c.set("before-points",r.beforePoints); c.set("after-points",r.afterPoints); c.set("before-journey-minutes",r.beforeMinutes);
        c.set("after-journey-minutes",r.afterMinutes); c.set("original-points",r.originalPoints); c.set("source",r.source);
        c.set("before-profile-sha256",r.beforeHash); c.set("created",r.created);
        Store.atomic(receiptFile(r.uuid), c.saveToString());
    }
    private void stamp(State s, Receipt r) {
        s.historyCreditRevision = r.revision; s.historyCreditPoints = r.credit(); s.historyCreditMinutes = r.minutes();
        s.historyCreditSource = r.source; s.historyCreditAt = r.created; s.dirty = true;
    }
    Receipt prepare(State state, Path profile) throws Exception {
        if (journey.historyCreditRevision.isEmpty()) return null;
        if (Files.exists(receiptFile(state.uuid))) {
            Receipt r = readReceipt(state.uuid);
            if (!state.historyCreditRevision.isEmpty()) {
                if (!state.historyCreditRevision.equals(r.revision) || state.historyCreditPoints != r.credit()
                    || state.historyCreditMinutes != r.minutes() || !state.historyCreditSource.equals(r.source))
                    throw new IOException("Historical credit profile and receipt disagree");
                return r.phase.equals("PREPARED") ? r : null;
            }
            if (r.phase.equals("COMPLETED")) {
                // A downgrade may have stripped the new profile fields. The separate receipt forbids re-crediting.
                if (!Files.isRegularFile(profile)) throw new IOException("Credited player profile is missing; restore it before loading historical progress");
                journey.legacyPerks.unlock(state, journey, r.originalPoints);
                stamp(state, r); return r;
            }
            if (!r.beforeHash.equals(hash(profile)) || state.points != r.beforePoints || state.journeyMinutes != r.beforeMinutes)
                throw new IOException("Historical credit is uncertain; staff must review it before another credit");
            state.points = r.afterPoints; state.journeyMinutes = r.afterMinutes;
            state.historyCreditJustApplied = r.credit() > 0;
            journey.legacyPerks.unlock(state, journey, r.originalPoints);
            stamp(state, r); return r;
        }
        // A persisted profile marker alone still proves that the points were already credited.
        if (!state.historyCreditRevision.isEmpty()) {
            if (!state.historyCreditRevision.matches("[a-z0-9_-]{1,80}") || state.historyCreditPoints < 0 || state.historyCreditPoints > LIMIT
                || state.historyCreditMinutes < 0 || state.historyCreditMinutes > LIMIT || state.historyCreditSource.isEmpty() || state.historyCreditAt <= 0)
                throw new IOException("Invalid historical credit marker");
            return null;
        }
        Proof proof = proof(state.uuid);
        if (proof.source.equals("none") && !Files.exists(profile)) {
            state.historyCreditRevision = journey.historyCreditRevision; state.historyCreditSource = "none";
            state.historyCreditAt = System.currentTimeMillis(); state.dirty = true; return null;
        }
        if (state.points < 0 || state.points > LIMIT || state.journeyMinutes < 0 || state.journeyMinutes > LIMIT)
            throw new IOException("Current progress is outside historical credit limits");
        if (Files.exists(profile)) {
            Path backup = root.resolve("migration-backups").resolve(journey.historyCreditRevision).resolve("journeydata").resolve(state.uuid + ".yml");
            if (!Files.exists(backup)) Store.atomic(backup, Files.readString(profile));
        }
        long credit = Math.min(proof.points, LIMIT - state.points);
        long minutes = Math.min(proof.minutes, Math.max(0L, state.minutes - state.journeyMinutes));
        minutes = Math.min(minutes, LIMIT - state.journeyMinutes);
        Receipt r = new Receipt(state.uuid,journey.historyCreditRevision,"PREPARED",state.points,state.points + credit,
            state.journeyMinutes,state.journeyMinutes + minutes,proof.points,proof.source,hash(profile),System.currentTimeMillis());
        writeReceipt(r,"PREPARED");
        state.points = r.afterPoints; state.journeyMinutes = r.afterMinutes;
        state.historyCreditJustApplied = r.credit() > 0;
        journey.legacyPerks.unlock(state, journey, proof.points);
        stamp(state,r); return r;
    }
    void complete(Receipt r) throws IOException { if (!r.phase.equals("COMPLETED")) writeReceipt(r,"COMPLETED"); }
}
