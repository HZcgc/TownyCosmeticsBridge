package com.townyreborn.staffconnector;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

final class PortalClient implements AutoCloseable {
    private final Settings settings;
    private final Logger logger;
    private final Path queueFile;
    private final Path rejectedFile;
    private final Deque<String> queue = new ArrayDeque<>();
    private final ScheduledExecutorService worker;
    private final HttpClient httpClient;
    private final Consumer<RemoteAction> remoteActionHandler;
    private final AtomicBoolean persistenceScheduled = new AtomicBoolean();
    private final AtomicBoolean backlogDrainScheduled = new AtomicBoolean();

    private volatile String state = "starting";
    private volatile int lastHttpStatus;
    private volatile Instant lastSuccess;
    private volatile boolean closed;
    private volatile long rejectedEvents;
    private long nextAttemptNanos;
    private int consecutiveFailures;
    private boolean queueFullWarningSent;
    private int effectiveBatchSize;
    private int actionPollFailures;
    private boolean isolatingInvalidEvent;

    PortalClient(Path dataFolder, Settings settings, Logger logger, Consumer<RemoteAction> remoteActionHandler) throws IOException {
        this.settings = settings;
        this.logger = logger;
        this.remoteActionHandler = remoteActionHandler;
        Files.createDirectories(dataFolder);
        this.queueFile = dataFolder.resolve("pending-events.jsonl");
        this.rejectedFile = dataFolder.resolve("rejected-events.jsonl");
        loadQueue();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "TownyReborn-StaffPortal");
            thread.setDaemon(true);
            return thread;
        };
        this.worker = Executors.newSingleThreadScheduledExecutor(factory);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(settings.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.effectiveBatchSize = settings.batchSize();
    }

    void start() {
        state = settings.hasUsableToken() ? "ready" : "waiting for API token";
        worker.scheduleWithFixedDelay(this::flushSafely, 1, settings.flushIntervalSeconds(), TimeUnit.SECONDS);
        if (settings.remoteActionsEnabled()) {
            worker.scheduleWithFixedDelay(this::pollActionsSafely, 3, settings.remoteActionPollSeconds(), TimeUnit.SECONDS);
        }
    }

    void enqueue(Map<String,Object> payload){tryEnqueue(payload);}
    long droppedEvents(){return rejectedEvents;}
    boolean tryEnqueueDurable(Map<String,Object> payload){if(closed)return false;try{return worker.submit(()->tryEnqueue(payload)&&persistQueue()).get(12,java.util.concurrent.TimeUnit.SECONDS);}catch(Exception e){return false;}}
    boolean tryEnqueue(Map<String, Object> suppliedPayload) {
        if (closed) return false;
        Map<String, Object> payload = new LinkedHashMap<>(suppliedPayload);
        String type = String.valueOf(payload.getOrDefault("type", "staff.action"));
        payload.putIfAbsent("event_id", eventId(type));
        payload.putIfAbsent("occurred_at", Instant.now().toString());
        payload.putIfAbsent("server", settings.serverName());
        String json = Json.encode(payload);

        synchronized (queue) {
            if (queue.size() >= settings.maximumQueuedEvents()) {
                if (!queueFullWarningSent) {
                    queueFullWarningSent = true;
                    logger.severe("Staff Portal queue is full; new events are being dropped. Fix the connection or token.");
                }
                rejectedEvents++; return false;
            }
            queue.addLast(json);
        }
        schedulePersistence();
        return true;
    }

    Status status() {
        int size;
        synchronized (queue) {
            size = queue.size();
        }
        return new Status(settings.hasUsableToken(), settings.endpoint().toString(), settings.serverName(), size,
                state, lastHttpStatus, lastSuccess);
    }

    void reportActionResult(long actionId, boolean succeeded, String message) {
        if (closed || actionId <= 0) return;
        worker.execute(() -> sendActionResult(actionId, succeeded, message, 0));
    }

    private void pollActionsSafely() {
        if (closed || !settings.hasUsableToken() || !settings.remoteActionsEnabled()) return;
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("operation", "actions.pull");
        requestBody.put("server", settings.serverName());
        try {
            HttpResponse<String> response = httpClient.send(apiRequest(Json.encode(requestBody)),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                actionPollFailed("HTTP " + response.statusCode());
                return;
            }
            Object decoded = Json.decode(response.body());
            if (!(decoded instanceof Map<?, ?> root) || !(root.get("actions") instanceof List<?> actions)) {
                actionPollFailed("invalid response");
                return;
            }
            actionPollFailures = 0;
            for (Object supplied : actions) {
                if (!(supplied instanceof Map<?, ?> action)) continue;
                long id = number(action.get("id"), 0L);
                String type = text(action.get("action_type"));
                String playerName = text(action.get("player_name"));
                String playerUuid = text(action.get("player_uuid"));
                String reason = text(action.get("reason"));
                long duration = number(action.get("duration_seconds"), 0L);
                if (id > 0 && !type.isBlank() && !playerName.isBlank() && !reason.isBlank()) {
                    remoteActionHandler.accept(new RemoteAction(id, type, playerUuid, playerName, reason,
                            duration > 0 ? duration : null));
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException exception) {
            actionPollFailed(exception.getMessage());
        }
    }

    private void sendActionResult(long actionId, boolean succeeded, String message, int attempt) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operation", "actions.result");
        result.put("server", settings.serverName());
        result.put("action_id", actionId);
        result.put("status", succeeded ? "succeeded" : "failed");
        result.put("message", message == null ? "" : message);
        try {
            HttpResponse<String> response = httpClient.send(apiRequest(Json.encode(result)),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                if (response.statusCode() == HttpURLConnection.HTTP_CONFLICT) {
                    logger.warning("Staff Portal no longer owns remote action " + actionId
                            + "; its local execution record prevents a duplicate command.");
                    return;
                }
                retryActionResult(actionId, succeeded, message, attempt,
                        "HTTP " + response.statusCode());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException exception) {
            retryActionResult(actionId, succeeded, message, attempt, exception.getMessage());
        }
    }

    private void retryActionResult(long actionId, boolean succeeded, String message, int attempt, String detail) {
        if (closed) return;
        if (attempt >= 20) {
            logger.warning("Staff Portal could not acknowledge remote action " + actionId
                    + " after 21 attempts (" + detail + "). Its local execution record remains active.");
            return;
        }
        long delay = Math.min(60L, 5L << Math.min(attempt, 4));
        if (attempt == 0 || attempt == 5 || attempt == 10 || attempt == 15) {
            logger.warning("Staff Portal action " + actionId + " acknowledgement failed (" + detail
                    + "); retrying in " + delay + "s.");
        }
        worker.schedule(() -> sendActionResult(actionId, succeeded, message, attempt + 1),
                delay, TimeUnit.SECONDS);
    }

    private HttpRequest apiRequest(String body) {
        return HttpRequest.newBuilder(settings.endpoint())
                .timeout(settings.requestTimeout())
                .header("Authorization", "Bearer " + settings.token())
                .header("X-API-Token", settings.token())
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .header("User-Agent", "TownyRebornStaffConnector/1.3.1")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    private void actionPollFailed(String detail) {
        actionPollFailures++;
        if (actionPollFailures == 1 || actionPollFailures % 20 == 0) {
            logger.warning("Staff Portal remote-action poll failed (" + detail + "). Event uploads continue normally.");
        }
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private static long number(Object value, long fallback) { return value instanceof Number number ? number.longValue() : fallback; }

    private void loadQueue() throws IOException {
        if (!Files.isRegularFile(queueFile)) return;
        for (String line : Files.readAllLines(queueFile, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && true) queue.addLast(trimmed);
        }
    }

    private void schedulePersistence() {
        if (persistenceScheduled.compareAndSet(false, true)) {
            worker.schedule(() -> {
                try {
                    persistQueue();
                } finally {
                    persistenceScheduled.set(false);
                }
            }, 50, TimeUnit.MILLISECONDS);
        }
    }

    private void flushSafely() {
        try {
            flushBatch();
        } catch (Throwable throwable) {
            registerFailure("unexpected connector error", throwable);
        }
    }

    private void flushBatch() {
        if (closed || !settings.hasUsableToken()) {
            if (!closed) state = "waiting for API token";
            return;
        }
        if (System.nanoTime() < nextAttemptNanos) return;

        List<String> batch = snapshotBatch();
        if (batch.isEmpty()) {
            state = "connected (batch mode)";
            return;
        }

        String body = batchBody(batch);
        HttpRequest request = HttpRequest.newBuilder(settings.endpoint())
                .timeout(settings.requestTimeout())
                .header("Authorization", "Bearer " + settings.token())
                // Some Plesk/Apache/FastCGI setups strip Authorization before PHP sees it.
                // The portal intentionally accepts this server-only fallback header as well.
                .header("X-API-Token", settings.token())
                .header("X-TR-Protocol", "batch-v1")
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .header("User-Agent", "TownyRebornStaffConnector/1.3.1")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            lastHttpStatus = response.statusCode();
            if (response.statusCode() >= 200 && response.statusCode() < 300 && acknowledgesBatch(response.body(), batch.size())) {
                removeBatch(batch);
                lastSuccess = Instant.now();
                state = "connected (batch mode, " + batch.size() + " sent)";
                consecutiveFailures = 0;
                nextAttemptNanos = 0;
                effectiveBatchSize = isolatingInvalidEvent ? 1 : settings.batchSize();
                scheduleBacklogDrain();
            } else if (response.statusCode() >= 200 && response.statusCode() < 300) {
                registerHttpFailure(response.statusCode(), 60, "portal did not acknowledge events; queue retained");
            } else if (response.statusCode() == HttpURLConnection.HTTP_ENTITY_TOO_LARGE && effectiveBatchSize > 1) {
                effectiveBatchSize = Math.max(1, effectiveBatchSize / 2);
                registerHttpFailure(response.statusCode(), 60,
                        "portal rejected the batch size; reduced to " + effectiveBatchSize);
            } else if (response.statusCode() == 400 || response.statusCode() == 422) {
                handleValidationFailure(response, batch);
            } else {
                registerHttpFailure(response.statusCode(), retryDelayForStatus(response.statusCode()), null);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException exception) {
            registerFailure("connection failed", exception);
        }
    }

    private List<String> snapshotBatch() {
        List<String> batch = new ArrayList<>();
        synchronized (queue) {
            int remaining = Math.min(effectiveBatchSize, queue.size());
            for (String event : queue) {
                if (remaining-- <= 0) break;
                int bytes=batch.stream().mapToInt(v->v.getBytes(StandardCharsets.UTF_8).length).sum();
                if(!batch.isEmpty() && bytes+event.getBytes(StandardCharsets.UTF_8).length>700000)break;
                batch.add(event);
            }
        }
        return batch;
    }

    private String batchBody(List<String> batch) {
        StringBuilder body = new StringBuilder(128 + batch.stream().mapToInt(String::length).sum());
        body.append("{\"events\":[");
        for (int index = 0; index < batch.size(); index++) {
            if (index > 0) body.append(',');
            body.append(batch.get(index));
        }
        return body.append("]}").toString();
    }

    private void removeBatch(List<String> expected) {
        synchronized (queue) {
            for (String event : expected) {
                if (!event.equals(queue.peekFirst())) break;
                queue.removeFirst();
            }
            queueFullWarningSent = false;
        }
        persistQueueQuietly();
    }

    private void scheduleBacklogDrain() {
        boolean hasMore;
        synchronized (queue) {
            hasMore = !queue.isEmpty();
        }
        if (!hasMore || closed || !backlogDrainScheduled.compareAndSet(false, true)) return;
        worker.schedule(() -> {
            backlogDrainScheduled.set(false);
            flushSafely();
        }, 3, TimeUnit.SECONDS);
    }

    private static Map<?, ?> responseObject(String body) {
        try {
            Object value = Json.decode(body);
            return value instanceof Map<?, ?> object ? object : Map.of();
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private static boolean acknowledgesBatch(String body, int expectedCount) {
        Map<?, ?> result = responseObject(body);
        if (!Boolean.TRUE.equals(result.get("accepted"))) return false;
        if (result.containsKey("event_count") && number(result.get("event_count"), -1) != expectedCount) return false;
        if (result.containsKey("accepted_count") || result.containsKey("duplicate_count")) {
            long accepted = number(result.get("accepted_count"), -1);
            long duplicates = number(result.get("duplicate_count"), -1);
            return accepted >= 0 && duplicates >= 0 && accepted + duplicates == expectedCount;
        }
        return true; // Earlier portals acknowledge the whole batch with accepted=true.
    }

    private void handleValidationFailure(HttpResponse<String> response, List<String> batch) {
        Map<?, ?> result = responseObject(response.body());
        String error = safeCode(result.get("error"));
        String validation = safeCode(result.get("validation_error"));
        long apiVersion;
        try {
            apiVersion = Long.parseLong(response.headers().firstValue("X-TR-API-Version").orElse("0"));
        } catch (NumberFormatException ignored) {
            apiVersion = 0;
        }
        if (response.statusCode() == 422 && "invalid_event_data".equals(error)) {
            if (apiVersion < 6 || validation.isEmpty()) {
                registerHttpFailure(422, 300, "portal rejected event data; install the Staff 1.3.1 website API fix; queue retained");
                return;
            }
            if ("unsupported_event_type".equals(validation)) {
                registerHttpFailure(422, 300, "portal does not support this event type; align website and connector versions; queue retained");
                return;
            }
            if (batch.size() > 1) {
                long index = number(result.get("event_index"), -1);
                if (index < 0 || index >= batch.size()) {
                    registerHttpFailure(422, 300, "portal rejection does not identify the queued event; queue retained");
                    return;
                }
                Map<?, ?> identified = responseObject(batch.get((int) index));
                if (!text(identified.get("event_id")).equals(text(result.get("event_id")))
                        || !text(identified.get("type")).equals(text(result.get("event_type")))) {
                    registerHttpFailure(422, 300, "portal rejection does not identify the queued event; queue retained");
                    return;
                }
                isolatingInvalidEvent = true;
                // Send the valid prefix together, then inspect the failing head.
                effectiveBatchSize = Math.max(1, (int) index);
                registerHttpFailure(422, 0, "invalid event (" + validation + "); checking individual events");
                scheduleBacklogDrain();
                return;
            }
            Map<?, ?> event = responseObject(batch.get(0));
            boolean matches = number(result.get("event_index"), -1) == 0
                    && text(event.get("event_id")).equals(text(result.get("event_id")))
                    && text(event.get("type")).equals(text(result.get("event_type")));
            if (!matches) {
                registerHttpFailure(422, 300, "portal rejection does not identify the queued event; queue retained");
                return;
            }
            if (!archiveRejectedEvent(batch.get(0), error, validation)) {
                registerHttpFailure(422, 60, "cannot save rejected event; queue retained");
                return;
            }
            removeBatch(batch);
            isolatingInvalidEvent = false;
            effectiveBatchSize = settings.batchSize();
            consecutiveFailures = 0;
            nextAttemptNanos = 0;
            state = "invalid event saved in rejected-events.jsonl; uploads continue";
            logger.warning("Staff Portal saved one rejected event (" + validation + ") in rejected-events.jsonl; uploads continue.");
            scheduleBacklogDrain();
            return;
        }
        String detail = "portal rejected request (HTTP " + response.statusCode()
                + (error.isEmpty() ? "" : ", " + error) + "); queue retained";
        registerHttpFailure(response.statusCode(), 300, detail);
    }

    private static String safeCode(Object value) {
        String code = text(value);
        return code.matches("[a-z0-9_]{1,80}") ? code : "";
    }

    private boolean archiveRejectedEvent(String event, String error, String validation) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("rejected_at", Instant.now().toString());
        record.put("http_status", 422);
        record.put("error", error);
        record.put("validation_error", validation);
        // Keep the original JSON bytes rather than decoding/re-encoding numbers,
        // identifiers or extension fields from the existing retry queue.
        String metadata = Json.encode(record);
        ByteBuffer bytes = StandardCharsets.UTF_8.encode(metadata.substring(0, metadata.length() - 1)
                + ",\"event\":" + event + "}\n");
        try (FileChannel output = FileChannel.open(rejectedFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            while (bytes.hasRemaining()) output.write(bytes);
            output.force(true);
            return true;
        } catch (IOException exception) {
            logger.warning("Could not save rejected Staff Portal event; it remains queued.");
            return false;
        }
    }

    private long retryDelayForStatus(int status) {
        if (status == 401 || status == 403) return 300;
        if (status == 429) return 180;
        if (status >= 500) return Math.max(60, retryDelaySeconds());
        return Math.max(60, retryDelaySeconds());
    }

    private void registerHttpFailure(int status, long delay, String detail) {
        consecutiveFailures++;
        nextAttemptNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(delay);
        state = detail != null ? detail : ((status == 401 || status == 403)
                ? "API token rejected (HTTP " + status + ")"
                : "portal error (HTTP " + status + ")");
        if (consecutiveFailures == 1 || consecutiveFailures % 10 == 0) {
            logger.warning("Staff Portal request returned HTTP " + status + "; " + state
                    + "; retrying in " + delay + "s.");
        }
    }

    private void registerFailure(String message, Throwable throwable) {
        consecutiveFailures++;
        long delay = retryDelaySeconds();
        nextAttemptNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(delay);
        state = message;
        if (consecutiveFailures == 1 || consecutiveFailures % 10 == 0) {
            logger.log(Level.WARNING, "Staff Portal " + message + "; queued events will retry in " + delay + "s: "
                    + throwable.getMessage());
        }
    }

    private long retryDelaySeconds() {
        int exponent = Math.min(8, Math.max(0, consecutiveFailures - 1));
        return Math.min(900, 15L << exponent);
    }

    private void persistQueueQuietly() {
        try {
            persistQueue();
        } catch (RuntimeException ignored) {
            // persistQueue already logged the IOException.
        }
    }

    private synchronized boolean persistQueue() {
        List<String> snapshot;
        synchronized (queue) {
            snapshot = new ArrayList<>(queue);
        }
        Path temporary = queueFile.resolveSibling(queueFile.getFileName() + ".tmp");
        try {
            Files.write(temporary, snapshot, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, queueFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailed) {
                Files.move(temporary, queueFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Could not persist Staff Portal retry queue", exception);
            return false;
        }
    }

    private String eventId(String type) {
        String safeType = type.replaceAll("[^a-zA-Z0-9]+", "-");
        return settings.serverName() + "-" + safeType + "-" + UUID.randomUUID();
    }

    @Override
    public void close() {
        closed = true;
        persistQueue();
        worker.shutdown();
        try {
            if (!worker.awaitTermination(3, TimeUnit.SECONDS)) worker.shutdownNow();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            worker.shutdownNow();
        }
    }

    record Status(boolean tokenConfigured, String endpoint, String serverName, int queuedEvents,
                  String state, int lastHttpStatus, Instant lastSuccess) {
    }

    record RemoteAction(long id, String type, String playerUuid, String playerName, String reason,
                        Long durationSeconds) {
    }
}
