package com.townyreborn.staffconnector;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

record Settings(
        URI endpoint,
        String token,
        String serverName,
        Duration connectTimeout,
        Duration requestTimeout,
        int flushIntervalSeconds,
        int batchSize,
        int maximumQueuedEvents,
        int playerSyncIntervalSeconds,
        int serverSnapshotIntervalSeconds,
        int remoteActionPollSeconds,
        int deduplicationWindowSeconds,
        int vulcanAlertCooldownSeconds,
        String townPlaceholder,
        boolean trackPlayers,
        boolean trackVulcan,
        boolean trackLiteBans,
        boolean trackPremiumVanish,
        boolean trackStaffCommands,
        boolean trackPlayerCommands,
        boolean trackMessages,
        boolean remoteActionsEnabled,
        boolean includeCommandArguments,
        Set<String> auditedCommands
) {
    static Settings load(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        URI endpoint;
        try {
            endpoint = URI.create(config.getString("portal.endpoint", "https://staff.townyreborn.com/api.php").trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("portal.endpoint is not a valid URL", exception);
        }
        if (!"https".equalsIgnoreCase(endpoint.getScheme())) {
            throw new IllegalArgumentException("portal.endpoint must use HTTPS");
        }

        String serverName = clean(config.getString("portal.server-name", "smp"), "smp", 64);
        String token = config.getString("portal.api-token", "").trim();
        String placeholder = config.getString("town-placeholder", "%townyadvanced_town%").trim();
        List<String> configuredCommands = config.getStringList("staff-command-audit.commands");
        Set<String> commands = new LinkedHashSet<>();
        for (String configured : configuredCommands) {
            String command = configured.trim().toLowerCase(Locale.ROOT);
            if (command.startsWith("/")) command = command.substring(1);
            int namespace = command.indexOf(':');
            if (namespace >= 0) command = command.substring(namespace + 1);
            if (command.matches("[a-z0-9_-]{1,40}")) commands.add(command);
        }
        // PremiumVanish' /pv <page> command is configuration/navigation noise.
        // Keep only /pva (administrative vanish) even when an older config.yml
        // still contains the previous "pv" entry.
        commands.remove("pv");
        commands.add("pva");

        return new Settings(
                endpoint,
                token,
                serverName,
                Duration.ofSeconds(bound(config.getInt("portal.connect-timeout-seconds", 5), 2, 30)),
                Duration.ofSeconds(bound(config.getInt("portal.request-timeout-seconds", 10), 3, 60)),
                bound(config.getInt("portal.flush-interval-seconds", 15), 10, 300),
                bound(config.getInt("portal.batch-size", 20), 1, 25),
                bound(config.getInt("portal.maximum-queued-events", 5000), 100, 50_000),
                bound(config.getInt("player-sync-interval-seconds", 300), 30, 3600),
                bound(config.getInt("server-snapshot-interval-seconds", 60), 30, 900),
                bound(config.getInt("remote-actions.poll-interval-seconds", 10), 5, 60),
                bound(config.getInt("deduplication-window-seconds", 10), 1, 120),
                bound(config.getInt("vulcan-alert-cooldown-seconds", 5), 1, 300),
                placeholder,
                config.getBoolean("tracking.players", true),
                config.getBoolean("tracking.vulcan", true),
                config.getBoolean("tracking.litebans", true),
                config.getBoolean("tracking.premiumvanish", true),
                config.getBoolean("tracking.staff-commands", true),
                config.getBoolean("tracking.player-commands", true),
                config.getBoolean("tracking.messages", true),
                config.getBoolean("remote-actions.enabled", true),
                config.getBoolean("staff-command-audit.include-arguments", true),
                Set.copyOf(commands)
        );
    }

    boolean hasUsableToken() {
        return token.startsWith("tr_live_") && token.length() >= 24 && !token.contains("PASTE_");
    }

    private static int bound(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String clean(String input, String fallback, int maximumLength) {
        String result = input == null ? "" : input.trim();
        if (result.isEmpty()) result = fallback;
        return result.substring(0, Math.min(maximumLength, result.length()));
    }
}
