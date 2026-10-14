package net.townyreborn.journey;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.plugin.Plugin;

/** Optional LMB Quests hook: actual completions rather than a falling aggregate counter. */
final class QuestPoints implements Listener {
    static final String FINISH_EVENT = "com.leonardobishop.quests.bukkit.api.event.PlayerFinishQuestEvent";
    private final RebornPlugin plugin;
    QuestPoints(RebornPlugin plugin) { this.plugin = plugin; }

    void register() {
        if (!plugin.getConfig().getBoolean("sources.quests.enabled", false)) return;
        Plugin quests = plugin.getServer().getPluginManager().getPlugin("Quests");
        if (quests == null || !quests.isEnabled()) {
            plugin.warnOnce("quests-points", "Quest JP needs the optional Quests plugin; other Journey sources remain available.");
            return;
        }
        try {
            Class<? extends Event> event = Class.forName(FINISH_EVENT, true, quests.getClass().getClassLoader()).asSubclass(Event.class);
            Method accessor = event.getMethod("getQuestProgress");
            Class<?> progress = accessor.getReturnType();
            Method id = progress.getMethod("getQuestId"), date = progress.getMethod("getCompletionDate");
            Method completed = progress.getMethod("isCompleted"), uuid = progress.getMethod("getPlayerUUID");
            plugin.getServer().getPluginManager().registerEvent(event, this, EventPriority.MONITOR, (listener, value) -> {
                if (!(value instanceof PlayerEvent playerEvent) || value instanceof Cancellable cancel && cancel.isCancelled()) return;
                Player player = playerEvent.getPlayer();
                try {
                    Object record = accessor.invoke(value);
                    if (!Boolean.TRUE.equals(completed.invoke(record)) || !player.getUniqueId().equals(uuid.invoke(record))) return;
                    String questId = (String) id.invoke(record);
                    long timestamp = ((Number) date.invoke(record)).longValue();
                    // Snapshot before Quests can restart and mutate this record.
                    Runnable award = () -> complete(player, questId, timestamp);
                    if (Bukkit.isPrimaryThread()) award.run();
                    else plugin.getServer().getScheduler().runTask(plugin, award);
                } catch (ReflectiveOperationException | RuntimeException error) {
                    plugin.warnOnce("quests-event", "Could not read the Quests completion event; no estimated JP was granted.");
                }
            }, plugin, true);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException error) {
            plugin.warnOnce("quests-points", "This Quests API cannot provide completion JP; no aggregate counter fallback is used.");
        }
    }

    static String key(String questId) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(questId.getBytes(StandardCharsets.UTF_8)));
    }

    void complete(Player player, String questId, long timestamp) {
        if (!plugin.getConfig().getBoolean("sources.quests.enabled", false) || !player.isOnline()
            || !plugin.sources.eligible(player) || questId == null || questId.isBlank() || timestamp <= 0) return;
        try {
            State state = plugin.state(player);
            if (state.blocked) return;
            plugin.activate(player, state);
            String key = key(questId);
            if (timestamp <= state.questCompletions.getOrDefault(key, 0L)) return;
            int previous = plugin.journey.level(state.points);
            state.questCompletions.put(key, timestamp);
            state.dirty = true;
            state.capped(DailyTime.today(), "quests",
                plugin.getConfig().getLong("sources.quests.points-per-completion", 10),
                plugin.getConfig().getLong("sources.quests.daily-cap", 300));
            // Receipt and points are committed together, even when the cap is already full.
            plugin.store.save(state);
            if (plugin.journey.level(state.points) > previous) plugin.levelReached(player, state, previous);
        } catch (Exception error) {
            try { plugin.state(player).blocked = true; } catch (Exception ignored) { }
            plugin.profileError(player, error);
        }
    }
}
