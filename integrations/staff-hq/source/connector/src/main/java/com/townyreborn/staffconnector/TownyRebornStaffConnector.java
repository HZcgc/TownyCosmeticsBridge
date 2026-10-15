package com.townyreborn.staffconnector;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class TownyRebornStaffConnector extends JavaPlugin implements Listener, CommandExecutor {
    private final Map<String, String> integrations = new ConcurrentHashMap<>();
    private final Map<String, Long> recentPunishments = new ConcurrentHashMap<>();
    private final Map<String, Long> recentVulcanAlerts = new ConcurrentHashMap<>();
    private final Map<UUID, PendingChannelMessage> pendingChannelMessages = new ConcurrentHashMap<>();
    private final Map<UUID, String> privateReplyTargets = new ConcurrentHashMap<>();
    private final Map<UUID, ChatCapture> chatCaptures = new ConcurrentHashMap<>();
    private final Set<Long> executedRemoteActions = ConcurrentHashMap.newKeySet();

    private Settings settings;
    private PortalClient portal;
    private TelemetryBuffer telemetry;
    private GameplayTelemetry gameplay;
    private InsightsOutbox insightsOutbox;
    private GameInsights insights;
    private PluginUpdates updates;
    private LogDiagnostics diagnostics;
    private volatile List<Map<String,Object>> updateInventory=List.of();
    private volatile String updateGameVersion="unknown";
    private Listener integrationListener = new Listener() { };
    private int playerSyncTask = -1;
    private int serverSnapshotTask = -1;
    private boolean executingRemoteAction;
    private Object liteBansEvents;
    private Object liteBansListener;
    private Method liteBansUnregister;
    private boolean liteBansNative;
    private Path executedActionsFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadExecutedRemoteActions();
        Bukkit.getPluginManager().registerEvents(this, this);
        PluginCommand command = getCommand("trstaff");
        if (command != null) command.setExecutor(this);

        if (!startConnector(false)) {
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Towny Reborn Staff Connector enabled for server '" + settings.serverName() + "'.");
        if (!settings.hasUsableToken()) {
            getLogger().warning("No valid Staff Portal API token is configured. Rotate it in the portal, paste the NEW token into config.yml, then run /trstaff reload.");
        }
    }

    @Override
    public void onDisable() {
        stopTelemetry();
        cancelPlayerSync();
        cancelServerSnapshot();
        unregisterIntegrations();
        if (portal != null) {
            if (settings != null && settings.trackPlayers()) {
                for (Player player : Bukkit.getOnlinePlayers()) portal.enqueue(playerPayload(player, "player.quit", false));
            }
            portal.close();
            portal = null;
        }
    }

    private boolean startConnector(boolean reload) {
        try {
            if (reload) reloadConfig();
            settings = Settings.load(this);
            if (getConfig().getInt("portal.maximum-queued-events", Settings.MINIMUM_QUEUED_EVENTS) < Settings.MINIMUM_QUEUED_EVENTS)
                getLogger().info("Staff Portal queue capacity raised to 50,000 for this older config; endpoint, token and queued events are retained.");
            portal = new PortalClient(getDataFolder().toPath(), settings, getLogger(), this::queueRemoteAction);
            portal.start();
        } catch (IllegalArgumentException | IOException exception) {
            getLogger().log(Level.SEVERE, "Could not start Staff Portal connector: " + exception.getMessage(), exception);
            return false;
        }

        telemetry=new TelemetryBuffer(p -> portal != null && portal.tryEnqueueDurable(p),getDataFolder().toPath(),getLogger());
        if(getConfig().getBoolean("tracking.gameplay",true)){gameplay=new GameplayTelemetry(this,telemetry);gameplay.start();}
        if(getConfig().getBoolean("plugin-updates.enabled",true)){updates=new PluginUpdates(getDataFolder().toPath(),p->{if(portal!=null)portal.enqueue(p);},()->updateInventory,()->updateGameVersion,"paper");updates.start();}
        if(getConfig().getBoolean("tracking.diagnostics",true))diagnostics=new LogDiagnostics(p->{if(portal!=null)portal.enqueue(p);},telemetry);
        if(getConfig().getBoolean("tracking.game-insights",true)) {
            insightsOutbox=new InsightsOutbox(getDataFolder().toPath(),p -> portal!=null && portal.tryEnqueueDurable(p),getLogger());
            insights=new GameInsights(this,insightsOutbox);insights.start();
        }
        schedulePlayerSync();
        scheduleServerSnapshot();
        Bukkit.getScheduler().runTask(this, () -> {
            registerIntegrations();
            if (settings.trackPlayers()) {
                for (Player player : Bukkit.getOnlinePlayers()) portal.enqueue(playerPayload(player, "player.update", true));
            }
        });
        return true;
    }

    private boolean reloadConnector() {
        stopTelemetry();
        cancelPlayerSync();
        cancelServerSnapshot();
        unregisterIntegrations();
        if (portal != null) portal.close();
        integrations.clear();
        return startConnector(true);
    }

    private void stopTelemetry(){if(insights!=null){insights.close();insights=null;}if(insightsOutbox!=null){insightsOutbox.close();insightsOutbox=null;}if(updates!=null){updates.close();updates=null;}if(diagnostics!=null){diagnostics.close();diagnostics=null;}if(gameplay!=null){gameplay.close();gameplay=null;}if(telemetry!=null){telemetry.close();telemetry=null;}}
    private void schedulePlayerSync() {
        if (!settings.trackPlayers()) return;
        long ticks = settings.playerSyncIntervalSeconds() * 20L;
        playerSyncTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            PortalClient current = portal;
            if (current == null) return;
            for (Player player : Bukkit.getOnlinePlayers()) current.enqueue(playerPayload(player, "player.update", true));
        }, ticks, ticks);
    }

    private void cancelPlayerSync() {
        if (playerSyncTask >= 0) {
            Bukkit.getScheduler().cancelTask(playerSyncTask);
            playerSyncTask = -1;
        }
    }

    private void scheduleServerSnapshot() {
        long ticks = settings.serverSnapshotIntervalSeconds() * 20L;
        serverSnapshotTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            PortalClient current = portal;
            if (current != null) current.enqueue(serverSnapshotPayload());
        }, 40L, ticks);
    }

    private void cancelServerSnapshot() {
        if (serverSnapshotTask >= 0) {
            Bukkit.getScheduler().cancelTask(serverSnapshotTask);
            serverSnapshotTask = -1;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (portal == null || !settings.trackPlayers()) return;
        Bukkit.getScheduler().runTaskLater(this,
                () -> portal.enqueue(playerPayload(event.getPlayer(), "player.join", true)), 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingChannelMessages.remove(uuid);
        privateReplyTargets.remove(uuid);
        chatCaptures.remove(uuid);
        if (portal == null || !settings.trackPlayers()) return;
        portal.enqueue(playerPayload(event.getPlayer(), "player.quit", false));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        CommandAudit.Parsed parsed = CommandAudit.parse(event.getMessage());
        if (parsed == null) return;
        String channel = channelFromCommandLabel(parsed.label());
        boolean channelMessage = channel != null && !parsed.arguments().isEmpty();
        handleCommand(player.getName(), player.getUniqueId(), parsed, "player", channelMessage);
        if (settings.trackMessages() && (!CommandAudit.containsSecret(parsed.label()) || channelMessage)) {
            if (CommandAudit.isPrivateMessage(parsed.label())) {
                String body = CommandAudit.privateMessageBody(parsed);
                if (body != null) {
                    String recipient = parsed.arguments().get(0);
                    rememberPrivateConversation(player.getUniqueId(), recipient);
                    emitMessage(player, "private", recipient, body, "command");
                }
            } else if (CommandAudit.isReply(parsed.label())) {
                String body = CommandAudit.replyBody(parsed);
                if (body != null) {
                    String recipient = privateReplyTargets.get(player.getUniqueId());
                    if (recipient == null) recipient = essentialsReplyTarget(player);
                    emitMessage(player, "private", recipient, body, "reply");
                }
            } else if (channelMessage) {
                pendingChannelMessages.put(player.getUniqueId(), new PendingChannelMessage(
                        channel, String.join(" ", parsed.arguments()), System.currentTimeMillis()));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerCommand(ServerCommandEvent event) {
        if (executingRemoteAction) return;
        CommandAudit.Parsed parsed = CommandAudit.parse(event.getCommand());
        if (parsed != null) handleCommand(event.getSender().getName(), null, parsed, "console", false);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void capturePlayerChat(AsyncPlayerChatEvent event) {
        if (portal == null || !settings.trackMessages()) return;
        chatCaptures.put(event.getPlayer().getUniqueId(),
                new ChatCapture(event.getMessage(), System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (portal == null || !settings.trackMessages()) return;
        UUID uuid = event.getPlayer().getUniqueId();
        ChatCapture captured = chatCaptures.remove(uuid);
        PendingChannelMessage pending = pendingChannelMessages.remove(uuid);
        if (event.isCancelled()) return;

        long now = System.currentTimeMillis();
        String channel;
        if (pending != null && now - pending.createdAt() <= 10_000L) {
            channel = pending.channel();
        } else if (captured != null && now - captured.createdAt() <= 10_000L
                && captured.message().startsWith("!")) {
            channel = "global";
        } else {
            channel = currentChatChannel(event.getPlayer());
        }
        emitMessage(event.getPlayer(), channel, null, event.getMessage(), "chat");
    }

    private void queueRemoteAction(PortalClient.RemoteAction action) {
        Bukkit.getScheduler().runTask(this, () -> executeRemoteAction(action));
    }

    private void executeRemoteAction(PortalClient.RemoteAction action) {
        PortalClient current = portal;
        if (current == null) return;
        if (executedRemoteActions.contains(action.id())) {
            current.reportActionResult(action.id(), true,
                    "Already dispatched earlier; duplicate execution prevented locally");
            return;
        }
        String commandLine;
        try {
            commandLine = remoteCommand(action);
        } catch (IllegalArgumentException exception) {
            current.reportActionResult(action.id(), false, exception.getMessage());
            return;
        }
        boolean dispatched;
        try {
            recentPunishments.put(punishmentKey(action.type(), action.playerName()), System.currentTimeMillis());
            executingRemoteAction = true;
            Object reflectedSender = Reflect.call(Bukkit.getServer(), "getConsoleSender");
            if (!(reflectedSender instanceof CommandSender consoleSender)) {
                throw new IllegalStateException("Paper did not expose a compatible console sender");
            }
            Object reflectedResult = Reflect.callStatic(Bukkit.class, "dispatchCommand", consoleSender, commandLine);
            if (!(reflectedResult instanceof Boolean result)) {
                throw new IllegalStateException("Paper did not expose a compatible command dispatcher");
            }
            dispatched = result;
            if (dispatched) {
                rememberExecutedRemoteAction(action.id());
                getLogger().info("Executed Staff HQ action #" + action.id() + " (" + action.type()
                        + ") for " + action.playerName() + ".");
            }
        } catch (RuntimeException | LinkageError exception) {
            getLogger().log(Level.WARNING, "Could not execute Staff HQ action #" + action.id()
                    + " (" + action.type() + ") for " + action.playerName(), exception);
            current.reportActionResult(action.id(), false, "Command dispatch failed: " + exception.getMessage());
            return;
        } finally {
            executingRemoteAction = false;
        }
        current.reportActionResult(action.id(), dispatched,
                dispatched ? "Dispatched safely by TownyRebornStaffConnector" : "Bukkit rejected the generated command");
    }

    private void loadExecutedRemoteActions() {
        executedActionsFile = getDataFolder().toPath().resolve("executed-remote-actions.txt");
        try {
            Files.createDirectories(getDataFolder().toPath());
            if (!Files.isRegularFile(executedActionsFile)) return;
            List<Long> ids = new ArrayList<>();
            for (String line : Files.readAllLines(executedActionsFile, StandardCharsets.UTF_8)) {
                try {
                    long id = Long.parseLong(line.trim());
                    if (id > 0) ids.add(id);
                } catch (NumberFormatException ignored) {
                }
            }
            int first = Math.max(0, ids.size() - 5000);
            executedRemoteActions.addAll(ids.subList(first, ids.size()));
            if (first > 0) compactExecutedRemoteActions();
        } catch (IOException exception) {
            getLogger().log(Level.WARNING, "Could not load the remote-action duplicate guard", exception);
        }
    }

    private synchronized void rememberExecutedRemoteAction(long id) {
        if (!executedRemoteActions.add(id)) return;
        try {
            Files.writeString(executedActionsFile, Long.toString(id) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            if (executedRemoteActions.size() > 5500) compactExecutedRemoteActions();
        } catch (IOException exception) {
            getLogger().log(Level.SEVERE,
                    "Could not persist remote action " + id + "; do not delete its queue row until this is fixed", exception);
        }
    }

    private synchronized void compactExecutedRemoteActions() throws IOException {
        List<Long> ids = new ArrayList<>(executedRemoteActions);
        ids.sort(Comparator.naturalOrder());
        if (ids.size() > 5000) ids = new ArrayList<>(ids.subList(ids.size() - 5000, ids.size()));
        executedRemoteActions.clear();
        executedRemoteActions.addAll(ids);
        List<String> lines = ids.stream().map(String::valueOf).toList();
        Files.write(executedActionsFile, lines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private String remoteCommand(PortalClient.RemoteAction action) {
        String player = action.playerName().trim();
        if (!player.matches("[A-Za-z0-9_.-]{1,32}")) throw new IllegalArgumentException("Unsafe player name rejected");
        String reason = action.reason().replaceAll("[\\r\\n\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        if (reason.isBlank()) throw new IllegalArgumentException("Empty reason rejected");
        if (reason.length() > 1000) reason = reason.substring(0, 1000);
        Long duration = action.durationSeconds();
        return switch (action.type().toLowerCase(Locale.ROOT)) {
            case "warning" -> "warn " + player + " " + reason;
            case "kick" -> "kick " + player + " " + reason;
            case "mute" -> duration != null && duration > 0
                    ? "tempmute " + player + " " + duration + "s " + reason
                    : "mute " + player + " " + reason;
            case "tempban" -> {
                if (duration == null || duration <= 0) throw new IllegalArgumentException("Temporary ban has no duration");
                yield "tempban " + player + " " + duration + "s " + reason;
            }
            case "ban" -> "ban " + player + " " + reason;
            default -> throw new IllegalArgumentException("Unsupported remote action type");
        };
    }

    private Map<String, Object> serverSnapshotPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "server.snapshot");
        payload.put("online_players", Bukkit.getOnlinePlayers().size());
        Object server = Bukkit.getServer();
        payload.put("max_players", (int) Reflect.number(server, 0, "getMaxPlayers"));
        Object tps = Reflect.call(server, "getTPS");
        if (tps instanceof double[] values && values.length > 0) payload.put("tps", Math.round(values[0] * 100.0) / 100.0);
        double mspt = Reflect.number(server, Double.NaN, "getAverageTickTime");
        if (Double.isFinite(mspt)) payload.put("mspt", Math.round(mspt * 100.0) / 100.0);
        Runtime runtime = Runtime.getRuntime();
        payload.put("memory_used_mb", Math.max(0L, (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576L));
        payload.put("memory_max_mb", Math.max(0L, runtime.maxMemory() / 1_048_576L));
        payload.put("uptime_seconds", Math.max(0L, ManagementFactory.getRuntimeMXBean().getUptime() / 1000L));
        payload.put("minecraft_version", Reflect.string(server, "unknown", "getMinecraftVersion", "getBukkitVersion", "getVersion"));
        payload.put("java_version", System.getProperty("java.version", "unknown"));
        Object worlds = Reflect.call(server, "getWorlds");
        payload.put("world_count", worlds instanceof Collection<?> collection ? collection.size() : 0);

        List<Map<String,Object>> plugins=new ArrayList<>();
        for(org.bukkit.plugin.Plugin p:Bukkit.getPluginManager().getPlugins())addPluginSnapshot(plugins,p);
        updateInventory=List.copyOf(plugins);updateGameVersion=Reflect.string(Bukkit.getServer(),"unknown","getMinecraftVersion","getBukkitVersion","getVersion");
        payload.put("plugins", plugins);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("processors", runtime.availableProcessors());
        PortalClient.Status health=portal.status();metadata.put("queued_events",health.queuedEvents());metadata.put("connection_state",health.state());metadata.put("last_http_status",health.lastHttpStatus());metadata.put("last_success",health.lastSuccess()==null?null:health.lastSuccess().toString());metadata.put("dropped_events",portal.droppedEvents());metadata.put("telemetry_buffered",telemetry==null?0:telemetry.buffered());metadata.put("telemetry_rejected",telemetry==null?0:telemetry.rejected());
        metadata.put("connector_version", getDescription().getVersion());
        metadata.put("last_heartbeat_success",portal.heartbeatSuccess()==null?null:portal.heartbeatSuccess().toString());
        Map<String,Integer> pluginNames=new java.util.HashMap<>();for(Map<String,Object> p:plugins)pluginNames.merge(String.valueOf(p.get("name")),1,Integer::sum);metadata.put("duplicate_plugin_names",pluginNames.entrySet().stream().filter(e->e.getValue()>1).map(Map.Entry::getKey).toList());
        payload.put("metadata", metadata);
        return payload;
    }

    private void addPluginSnapshot(List<Map<String,Object>> plugins,Object object){if(!(object instanceof org.bukkit.plugin.Plugin p))return;Map<String,Object> row=new LinkedHashMap<>();row.put("name",p.getName());row.put("version",p.getDescription().getVersion());row.put("enabled",p.isEnabled());row.put("website",p.getDescription().getWebsite()==null?"":p.getDescription().getWebsite());row.putAll(gameplay==null?Map.of("coverage","technical","category",GameplayTelemetry.category(p.getName())):gameplay.coverage(p));if(insights!=null)row.putAll(insights.coverage(p.getName()));String state=integrations.get(p.getName());if(state!=null){row.put("integration",state);if(state.startsWith("native"))row.put("coverage","native");}plugins.add(row);}

    private void handleCommand(String staffName, UUID actorUuid, CommandAudit.Parsed parsed,
                               String origin, boolean channelMessage) {
        if (portal == null) return;
        // The first argument is not a player target for credentials or chat.
        // Masking only details.command would otherwise leak it via target_id.
        String target = CommandAudit.containsSecret(parsed.label()) || channelMessage
                || CommandAudit.isReply(parsed.label()) ? null : parsed.target();
        if ("player".equals(origin) && settings.trackPlayerCommands()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("origin", origin);
            boolean redacted = CommandAudit.containsSecret(parsed.label());
            details.put("command", CommandAudit.safeCommand(parsed, settings.includeCommandArguments(), channelMessage));
            details.put("redacted", redacted);
            if (redacted) details.put("redaction_reason", "credentials_or_account_secret");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "command.action");
            payload.put("staff", staffName);
            if (actorUuid != null) payload.put("actor_uuid", actorUuid.toString());
            payload.put("actor_kind", "player");
            payload.put("action", "command." + parsed.label());
            payload.put("target_type", target == null ? "network" : "player");
            if (target != null) payload.put("target_id", target);
            payload.put("details", details);
            portal.enqueue(payload);
        } else if ("console".equals(origin) && settings.trackStaffCommands()
                && settings.auditedCommands().contains(parsed.label())) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("origin", origin);
            details.put("command", CommandAudit.safeCommand(parsed, settings.includeCommandArguments(), false));
            portal.enqueue(staffAction(staffName, "command." + parsed.label(),
                    target == null ? "network" : "player", target, details));
        }

        if (settings.trackLiteBans() && parsed.punishmentType() != null && parsed.target() != null) {
            Bukkit.getScheduler().runTaskLater(this, () -> emitCommandPunishment(staffName, parsed), 20L);
        }
    }

    private void emitMessage(Player player, String channel, String recipientName, String message, String origin) {
        PortalClient current = portal;
        if (current == null || message == null) return;
        String cleaned = message.replaceAll("[\\r\\n\\p{Cntrl}]", " ").trim();
        if (cleaned.isEmpty()) return;
        if (cleaned.length() > 4096) cleaned = cleaned.substring(0, 4096);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "chat.message");
        payload.put("uuid", player.getUniqueId().toString());
        payload.put("username", player.getName());
        payload.put("channel", cleanChannel(channel));
        payload.put("message", cleaned);
        payload.put("origin", origin);
        payload.put("delivered", true);
        if (recipientName != null && !recipientName.isBlank()) {
            String cleanRecipient = recipientName.trim();
            payload.put("recipient_name", cleanRecipient.substring(0, Math.min(96, cleanRecipient.length())));
            Player recipient = onlinePlayer(cleanRecipient);
            if (recipient != null) payload.put("recipient_uuid", recipient.getUniqueId().toString());
        }
        current.enqueue(payload);
    }

    private void rememberPrivateConversation(UUID senderUuid, String recipientName) {
        if (recipientName == null || recipientName.isBlank()) return;
        String cleanRecipient = recipientName.trim();
        privateReplyTargets.put(senderUuid, cleanRecipient);
        Player recipient = onlinePlayer(cleanRecipient);
        if (recipient != null) privateReplyTargets.put(recipient.getUniqueId(), playerName(senderUuid));
    }

    private Player onlinePlayer(String name) {
        Object result = Reflect.callOneArgument(Bukkit.getServer(), name, "getPlayerExact", "getPlayer");
        return result instanceof Player player ? player : null;
    }

    private String playerName(UUID uuid) {
        Object result = Reflect.callOneArgument(Bukkit.getServer(), uuid, "getPlayer");
        return result instanceof Player player ? player.getName() : uuid.toString();
    }

    private String essentialsReplyTarget(Player player) {
        Plugin essentials = Bukkit.getPluginManager().getPlugin("Essentials");
        if (essentials == null || !essentials.isEnabled()) return null;
        Object user = Reflect.callOneArgument(essentials, player, "getUser");
        Object target = Reflect.call(user, "getReplyTo", "getLastMessageRecipient", "getLastRecipient");
        if (target instanceof UUID uuid) return playerName(uuid);
        String name = Reflect.string(target, null, "getName", "getDisplayName");
        return name == null || name.isBlank() ? null : name;
    }

    private String channelFromCommandLabel(String label) {
        Plugin townyChat = Bukkit.getPluginManager().getPlugin("TownyChat");
        if (townyChat != null && townyChat.isEnabled()) {
            Object channels = TownyChatBridge.channels(townyChat);
            if (channels instanceof Map<?, ?> map) {
                for (Object channel : map.values()) {
                    Object commands = TownyChatBridge.commands(townyChat, channel);
                    if (commands instanceof Iterable<?> iterable) {
                        for (Object supplied : iterable) {
                            if (label.equalsIgnoreCase(String.valueOf(supplied))) {
                                return cleanChannel(TownyChatBridge.channelName(townyChat, channel));
                            }
                        }
                    }
                }
            }
        }
        return switch (label) {
            case "g", "global", "gc" -> "global";
            case "l", "lc", "local" -> "local";
            case "tc", "townchat" -> "town";
            case "nc", "nationchat" -> "nation";
            case "ac", "alliancechat" -> "alliance";
            case "sc", "staffchat" -> "staff";
            case "adc", "adminchat" -> "admin";
            default -> null;
        };
    }

    private String currentChatChannel(Player player) {
        Plugin townyChat = Bukkit.getPluginManager().getPlugin("TownyChat");
        if (townyChat != null && townyChat.isEnabled()) {
            Object channel = TownyChatBridge.playerChannel(townyChat, player);
            String name = TownyChatBridge.channelName(townyChat, channel);
            if (name != null && !name.isBlank()) return cleanChannel(name);
        }
        return "global";
    }

    private String cleanChannel(String supplied) {
        String result = supplied == null ? "global" : supplied.trim().toLowerCase(Locale.ROOT);
        result = result.replaceAll("[^a-z0-9_.-]", "-").replaceAll("-+", "-");
        if (result.isBlank()) result = "global";
        return result.substring(0, Math.min(32, result.length()));
    }

    private void emitCommandPunishment(String staffName, CommandAudit.Parsed parsed) {
        String key = punishmentKey(parsed.punishmentType(), parsed.target());
        if (!reservePunishment(key)) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "punishment.created");
        payload.put("username", parsed.target());
        payload.put("punishment_type", parsed.punishmentType());
        payload.put("reason", parsed.reason());
        if (parsed.durationSeconds() != null) payload.put("duration_seconds", parsed.durationSeconds());
        payload.put("source", "LiteBans");
        payload.put("external_id", "command-" + UUID.randomUUID());
        payload.put("issued_by", staffName);
        portal.enqueue(payload);
    }

    private Map<String, Object> playerPayload(Player player, String type, boolean online) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("uuid", player.getUniqueId().toString());
        payload.put("username", player.getName());
        payload.put("platform", isBedrock(player.getUniqueId()) ? "bedrock" : "java");
        payload.put("online", online);
        payload.put("playtime_seconds", Math.max(0L, player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L));
        if (player.getFirstPlayed() > 0) payload.put("first_seen_at", Instant.ofEpochMilli(player.getFirstPlayed()).toString());

        Double balance = balance(player);
        if (balance != null && Double.isFinite(balance)) payload.put("balance", balance);
        String town = town(player);
        if (town != null) payload.put("town", town);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("world", player.getWorld().getName());
        metadata.put("vanished", isVanished(player));
        Object ping = Reflect.call(player, "getPing");
        if (ping instanceof Number number) metadata.put("ping", number.intValue());
        String clientBrand = Reflect.string(player, null, "getClientBrandName");
        if (clientBrand != null) metadata.put("client_brand", clientBrand);
        payload.put("metadata", metadata);
        return payload;
    }

    private boolean isBedrock(UUID uuid) {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi", false, getClassLoader());
            Object api = Reflect.callStatic(apiClass, "getInstance");
            Object result = Reflect.callOneArgument(api, uuid, "isFloodgatePlayer");
            return result instanceof Boolean bool && bool;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    private Double balance(Player player) {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy", false, getClassLoader());
            Object registration = Reflect.callOneArgument(Bukkit.getServicesManager(), economyClass, "getRegistration");
            Object provider = Reflect.call(registration, "getProvider");
            Object result = Reflect.callOneArgument(provider, player, "getBalance");
            return result instanceof Number number ? number.doubleValue() : null;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }

    private String town(Player player) {
        if (!settings.townPlaceholder().isBlank()) {
            try {
                Class<?> placeholderApi = Class.forName("me.clip.placeholderapi.PlaceholderAPI", false, getClassLoader());
                Object result = Reflect.callStatic(placeholderApi, "setPlaceholders", player, settings.townPlaceholder());
                String cleaned = cleanTownLabel(result == null ? null : String.valueOf(result));
                if (cleaned != null) return cleaned;
            } catch (ClassNotFoundException | LinkageError ignored) {
            }
        }
        try {
            Class<?> townyApi = Class.forName("com.palmergames.bukkit.towny.TownyAPI", false, getClassLoader());
            Object api = Reflect.callStatic(townyApi, "getInstance");
            Object resident = Reflect.callOneArgument(api, player.getUniqueId(), "getResident");
            Object town = Reflect.call(resident, "getTownOrNull", "getTown");
            return cleanTownLabel(Reflect.string(town, null, "getName"));
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }

    static String cleanTownLabel(String value) {
        if (value == null) return null;
        String cleaned = value.trim()
                .replaceAll("(?i)[§&][0-9A-FK-ORX]", "")
                .replaceAll("(?i)<#[0-9A-F]{6}>", "")
                .trim();
        if (cleaned.length() >= 2 && cleaned.startsWith("[") && cleaned.endsWith("]")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        if (cleaned.isEmpty() || cleaned.contains("%") || cleaned.equalsIgnoreCase("none")
                || cleaned.equalsIgnoreCase("no town") || cleaned.equalsIgnoreCase("wilderness")) return null;
        return cleaned.length() > 96 ? cleaned.substring(0, 96) : cleaned;
    }

    private boolean isVanished(Player player) {
        try {
            Class<?> api = Class.forName("de.myzelyam.api.vanish.VanishAPI", false, getClassLoader());
            Object result = Reflect.callStatic(api, "isInvisible", player);
            if (result instanceof Boolean bool) return bool;
        } catch (ClassNotFoundException | LinkageError ignored) {
        }
        Object values = Reflect.callOneArgument(player, "vanished", "getMetadata");
        if (values instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                if (Reflect.bool(value, false, "asBoolean")) return true;
            }
        }
        return false;
    }

    private void registerIntegrations() {
        if (settings.trackVulcan()) registerVulcan(); else integrations.put("Vulcan", "disabled in config");
        if (settings.trackPremiumVanish()) registerPremiumVanish(); else integrations.put("PremiumVanish", "disabled in config");
        if (settings.trackLiteBans()) registerLiteBans(); else integrations.put("LiteBans", "disabled in config");
    }

    private void registerVulcan() {
        if (!pluginEnabled("Vulcan")) {
            integrations.put("Vulcan", "not installed");
            return;
        }
        boolean flag = registerDynamicEvent("me.frep.vulcan.api.event.VulcanFlagEvent", this::handleVulcanFlag);
        boolean punishment = registerDynamicEvent("me.frep.vulcan.api.event.VulcanPunishEvent", this::handleVulcanPunishment);
        integrations.put("Vulcan", flag ? (punishment ? "native alerts + punishments" : "native alerts") : "API unavailable");
        if (!flag) getLogger().warning("Vulcan is installed but its API event was not found. Confirm settings.enable-api: true and restart the server.");
    }

    private void handleVulcanFlag(Object event) {
        if (portal == null) return;
        Object playerObject = Reflect.call(event, "getPlayer");
        if (!(playerObject instanceof Player player)) return;
        Object check = Reflect.call(event, "getCheck");
        String checkName = Reflect.string(check, "Unknown", "getName");
        String checkType = Reflect.string(check, "", "getType");
        String fullCheck = checkType.isBlank() ? checkName : checkName + " Type " + checkType;
        if (!reserveVulcanAlert(player.getUniqueId() + ":" + fullCheck.toLowerCase(Locale.ROOT))) return;
        double vl = Reflect.number(event, Double.NaN, "getVl", "getVL", "getViolationLevel", "getViolation");
        if (!Double.isFinite(vl)) vl = Reflect.number(check, 1.0, "getVl", "getVL", "getViolationLevel", "getViolations");

        Map<String, Object> evidence = new LinkedHashMap<>();
        Object ping = Reflect.call(player, "getPing");
        if (ping instanceof Number number) evidence.put("ping", number.intValue());
        Object tps = Reflect.call(Bukkit.getServer(), "getTPS");
        if (tps instanceof double[] values && values.length > 0) evidence.put("tps", Math.round(values[0] * 100.0) / 100.0);
        String clientBrand = Reflect.string(player, null, "getClientBrandName");
        if (clientBrand != null) evidence.put("client_brand", clientBrand);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "anticheat.alert");
        payload.put("uuid", player.getUniqueId().toString());
        payload.put("username", player.getName());
        payload.put("check", fullCheck);
        payload.put("vl", vl);
        payload.put("severity", severity(vl));
        payload.put("message", player.getName() + " failed " + fullCheck + " (VL " + formatNumber(vl) + ")");
        payload.put("evidence", evidence);
        portal.enqueue(payload);
    }

    private void handleVulcanPunishment(Object event) {
        if (portal == null) return;
        Object playerObject = Reflect.call(event, "getPlayer");
        if (!(playerObject instanceof Player player)) return;
        Object check = Reflect.call(event, "getCheck");
        String checkName = Reflect.string(check, "Unknown", "getName");
        String checkType = Reflect.string(check, "", "getType");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("check", checkType.isBlank() ? checkName : checkName + " Type " + checkType);
        portal.enqueue(staffAction("Vulcan", "anticheat.punishment", "player", player.getName(), details));
    }

    private void registerPremiumVanish() {
        if (!pluginEnabled("PremiumVanish")) {
            integrations.put("PremiumVanish", "not installed");
            return;
        }
        boolean registered = registerDynamicEvent("de.myzelyam.api.vanish.PlayerVanishStateChangeEvent", this::handleVanishChange);
        integrations.put("PremiumVanish", registered ? "native state changes" : "API unavailable");
    }

    private void handleVanishChange(Object event) {
        if (portal == null) return;
        String name = Reflect.string(event, "Unknown", "getName");
        UUID uuid = Reflect.uuid(event, "getUUID", "getUuid");
        boolean vanishing = Reflect.bool(event, false, "isVanishing");
        String cause = Reflect.string(event, name, "getCause");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("vanished", vanishing);
        if (uuid != null) details.put("uuid", uuid.toString());
        portal.enqueue(staffAction(cause, vanishing ? "vanish.enabled" : "vanish.disabled", "player", name, details));
    }

    private void registerLiteBans() {
        if (!pluginEnabled("LiteBans")) {
            integrations.put("LiteBans", "not installed");
            return;
        }
        liteBansNative = false;
        try {
            Class<?> eventsClass = Class.forName("litebans.api.Events", true, getClassLoader());
            Class<?> listenerClass = Class.forName("litebans.api.Events$Listener", true, getClassLoader());
            Object events = Reflect.callStatic(eventsClass, "get");
            if (events == null) throw new IllegalStateException("Events.get() returned null");
            Object listener;
            if (listenerClass.isInterface()) {
                listener = Proxy.newProxyInstance(listenerClass.getClassLoader(), new Class<?>[]{listenerClass},
                        (proxy, method, arguments) -> {
                            if (method.getDeclaringClass() == Object.class) {
                                return switch (method.getName()) {
                                    case "toString" -> "TownyRebornLiteBansListener";
                                    case "hashCode" -> System.identityHashCode(proxy);
                                    case "equals" -> proxy == (arguments == null ? null : arguments[0]);
                                    default -> null;
                                };
                            }
                            if ("entryAdded".equals(method.getName()) && arguments != null && arguments.length > 0) {
                                handleLiteBansEntry(arguments[0]);
                            }
                            return null;
                        });
            } else {
                Class<?> hookClass = Class.forName("com.townyreborn.staffconnector.LiteBansClassHook", true, getClassLoader());
                listener = hookClass.getDeclaredConstructor(java.util.function.Consumer.class)
                        .newInstance((java.util.function.Consumer<Object>) this::handleLiteBansEntry);
            }
            Method register = findSingleArgumentMethod(eventsClass, "register", listenerClass);
            if (register == null) throw new NoSuchMethodException("Events.register(Listener)");
            register.invoke(events, listener);
            liteBansEvents = events;
            liteBansListener = listener;
            liteBansUnregister = findSingleArgumentMethod(eventsClass, "unregister", listenerClass);
            liteBansNative = true;
            integrations.put("LiteBans", "native events + command audit");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            integrations.put("LiteBans", "command audit fallback");
            getLogger().log(Level.INFO, "LiteBans native listener unavailable; command audit fallback is active: " + exception.getMessage());
        }
    }

    private void handleLiteBansEntry(Object entry) {
        if (portal == null) return;
        String rawType = Reflect.string(entry, "", "getType").toLowerCase(Locale.ROOT);
        long duration = liteBansDurationSeconds(entry);
        String punishmentType = normalizeLiteBansType(rawType, duration);
        if (punishmentType == null) return;
        String username = Reflect.string(entry, null, "getName", "getUsername", "getPlayerName");
        if (username == null) return;
        if (!reservePunishment(punishmentKey(punishmentType, username))) return;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "punishment.created");
        UUID uuid = Reflect.uuid(entry, "getUuid", "getUUID");
        if (uuid != null) payload.put("uuid", uuid.toString());
        payload.put("username", username);
        payload.put("punishment_type", punishmentType);
        payload.put("reason", Reflect.string(entry, "No reason supplied", "getReason"));
        if (duration > 0) payload.put("duration_seconds", duration);
        payload.put("source", "LiteBans");
        payload.put("external_id", String.valueOf(Reflect.longNumber(entry, System.nanoTime(), "getId", "getID")));
        String executor = Reflect.string(entry, "Unknown", "getExecutorName", "getExecutor");
        payload.put("issued_by", executor);
        portal.enqueue(payload);
    }

    static String normalizeLiteBansType(String rawType, long duration) {
        if (rawType == null) return null;
        return switch (rawType.trim().toLowerCase(Locale.ROOT)) {
            case "ban" -> duration > 0 ? "tempban" : "ban";
            case "warn", "warning" -> "warning";
            case "mute" -> "mute";
            case "kick" -> "kick";
            default -> null;
        };
    }

    private long liteBansDurationSeconds(Object entry) {
        long start = Reflect.longNumber(entry, -1, "getDateStart", "getStart", "getStartTime");
        long end = Reflect.longNumber(entry, -1, "getDateEnd", "getEnd", "getEndTime");
        if (start > 0 && end > start) {
            long difference = end - start;
            return start > 100_000_000_000L || end > 100_000_000_000L ? difference / 1000L : difference;
        }
        String durationText = Reflect.string(entry, null, "getDurationString");
        Long parsed = CommandAudit.parseDuration(durationText);
        return parsed == null ? 0 : parsed;
    }

    @SuppressWarnings("unchecked")
    private boolean registerDynamicEvent(String className, java.util.function.Consumer<Object> handler) {
        try {
            Class<?> rawClass = Class.forName(className, true, getClassLoader());
            if (!Event.class.isAssignableFrom(rawClass)) return false;
            Class<? extends Event> eventClass = (Class<? extends Event>) rawClass;
            EventExecutor executor = (listener, event) -> {
                try {
                    handler.accept(event);
                } catch (Throwable throwable) {
                    getLogger().log(Level.WARNING, "Integration event failed for " + className, throwable);
                }
            };
            Bukkit.getPluginManager().registerEvent(eventClass, integrationListener, EventPriority.MONITOR,
                    executor, this, true);
            return true;
        } catch (ClassNotFoundException | LinkageError | RuntimeException exception) {
            return false;
        }
    }

    private void unregisterIntegrations() {
        HandlerList.unregisterAll(integrationListener);
        integrationListener = new Listener() { };
        if (liteBansUnregister != null && liteBansEvents != null && liteBansListener != null) {
            try {
                liteBansUnregister.invoke(liteBansEvents, liteBansListener);
            } catch (ReflectiveOperationException exception) {
                getLogger().log(Level.FINE, "Could not unregister LiteBans listener", exception);
            }
        }
        liteBansEvents = null;
        liteBansListener = null;
        liteBansUnregister = null;
        liteBansNative = false;
    }

    private boolean pluginEnabled(String name) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        return plugin != null && plugin.isEnabled();
    }

    private Method findSingleArgumentMethod(Class<?> owner, String name, Class<?> argument) {
        for (Method method : Reflect.publicMethods(owner)) {
            if (method.getName().equals(name) && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(argument)) return method;
        }
        return null;
    }

    private boolean reservePunishment(String key) {
        long now = System.currentTimeMillis();
        long window = settings.deduplicationWindowSeconds() * 1000L;
        recentPunishments.entrySet().removeIf(entry -> now - entry.getValue() > window);
        return recentPunishments.putIfAbsent(key, now) == null;
    }

    private boolean reserveVulcanAlert(String key) {
        long now = System.currentTimeMillis();
        long window = settings.vulcanAlertCooldownSeconds() * 1000L;
        recentVulcanAlerts.entrySet().removeIf(entry -> now - entry.getValue() > window);
        Long previous = recentVulcanAlerts.get(key);
        if (previous != null && now - previous <= window) return false;
        recentVulcanAlerts.put(key, now);
        return true;
    }

    private String punishmentKey(String type, String target) {
        String group = switch (type.toLowerCase(Locale.ROOT)) {
            case "ban", "tempban" -> "ban";
            case "mute", "tempmute" -> "mute";
            default -> type.toLowerCase(Locale.ROOT);
        };
        return group + ':' + target.toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> staffAction(String staff, String action, String targetType,
                                            String targetId, Map<String, Object> details) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "staff.action");
        payload.put("staff", staff == null || staff.isBlank() ? "Server staff" : staff);
        payload.put("action", action);
        payload.put("target_type", targetType);
        if (targetId != null && !targetId.isBlank()) payload.put("target_id", targetId);
        payload.put("details", details == null ? Collections.emptyMap() : details);
        return payload;
    }

    private record PendingChannelMessage(String channel, String message, long createdAt) { }

    private record ChatCapture(String message, long createdAt) { }

    private String severity(double vl) {
        if (vl >= 15) return "critical";
        if (vl >= 8) return "high";
        if (vl >= 3) return "medium";
        return "low";
    }

    private String formatNumber(double value) {
        if (Math.rint(value) == value) return Long.toString((long) value);
        return String.format(Locale.ROOT, "%.2f", value);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("townyreborn.staffconnector.admin")) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }
        String subcommand = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "updates" -> {if(updates!=null)updates.requestCheck();sender.sendMessage("Public plugin versions are being checked in the background.");}
            case "status" -> sendStatus(sender);
            case "test" -> {
                if (portal == null) {
                    sender.sendMessage("[TR Staff] Connector is not running.");
                } else {
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("plugin_version", getDescription().getVersion());
                    portal.enqueue(staffAction(sender.getName(), "connector.test", "network", settings.serverName(), details));
                    sender.sendMessage("[TR Staff] Test event queued. Check the portal audit log in a few seconds.");
                }
            }
            case "reload" -> {
                sender.sendMessage("[TR Staff] Reloading connector...");
                if (reloadConnector()) {
                    sender.sendMessage("[TR Staff] Reloaded successfully.");
                    sendStatus(sender);
                } else {
                    sender.sendMessage("[TR Staff] Reload failed. Check the console; the connector is stopped.");
                }
            }
            default -> sender.sendMessage("Usage: /trstaff <status|test|reload|updates>");
        }
        return true;
    }

    private void sendStatus(CommandSender sender) {
        if (portal == null) {
            sender.sendMessage("[TR Staff] Connector is not running.");
            return;
        }
        PortalClient.Status status = portal.status();
        sender.sendMessage("[TR Staff] Server: " + status.serverName());
        sender.sendMessage("[TR Staff] Endpoint: " + status.endpoint());
        sender.sendMessage("[TR Staff] API token configured: " + (status.tokenConfigured() ? "yes" : "NO"));
        sender.sendMessage("[TR Staff] State: " + status.state() + " | queued: " + status.queuedEvents() + " / " + status.queueCapacity());
        sender.sendMessage("[TR Staff] HQ API: " + (status.apiVersion() > 0 ? status.apiVersion() : "not yet verified"));
        if (status.lastHttpStatus() > 0) sender.sendMessage("[TR Staff] Last HTTP status: " + status.lastHttpStatus());
        if (status.lastSuccess() != null) sender.sendMessage("[TR Staff] Last success: " + status.lastSuccess());
        for (Map.Entry<String, String> integration : integrations.entrySet()) {
            sender.sendMessage("[TR Staff] " + integration.getKey() + ": " + integration.getValue());
        }
        if (liteBansNative) sender.sendMessage("[TR Staff] LiteBans native listener is active.");
    }
}
