package net.townyreborn.journey;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Restore purchase eligibility using the exact previously shipped catalogue, never paid ownership. */
final class LegacyPerks {
    private final long base, increase;
    private final int steps;
    private final Map<String, Integer> gates = new LinkedHashMap<>();
    LegacyPerks() {
        try (var stream = Objects.requireNonNull(LegacyPerks.class.getResourceAsStream("/legacy-perks-beta3.yml"));
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var config = new YamlConfiguration(); config.load(reader);
            base = config.getLong("points-first"); increase = config.getLong("points-increase"); steps = config.getInt("steps");
            for (String id : config.getConfigurationSection("perks").getKeys(false)) gates.put(id, config.getInt("perks." + id));
        } catch (Exception error) { throw new IllegalStateException("Previous perk catalogue could not be read", error); }
    }
    private long threshold(int level) { long n = level - 1L; return base * n + increase * n * (n - 1) / 2; }
    int level(long points) {
        int result = 1;
        while (result < steps && threshold(result + 1) <= Math.max(0, points)) result++;
        return result;
    }
    void restore(State state, Journey journey, YamlConfiguration old) {
        if (old.getInt("schema") != 2 || !state.uuid.toString().equals(old.getString("uuid")))
            throw new IllegalArgumentException("Invalid previous perk profile");
        // Profiles from the restarted curve must not be interpreted using the old curve.
        if (!old.getString("progress-revision", "").isEmpty()) return;
        int previousLevel = level(old.getLong("points"));
        for (var entry : gates.entrySet())
            if (entry.getValue() <= previousLevel && journey.perks.containsKey(entry.getKey()) && state.unlocked.add(entry.getKey())) state.dirty = true;
        // Only an actual recorded purchase proves ownership. Point thresholds never grant permissions.
        for (String id : old.getStringList("owned-perks"))
            if (journey.perks.containsKey(id) && state.owned.add(id)) state.dirty = true;
        journey.inheritLowerPerks(state.owned);
        state.unlocked.addAll(state.owned);
    }
}
