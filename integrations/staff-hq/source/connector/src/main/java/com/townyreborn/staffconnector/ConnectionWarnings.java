package com.townyreborn.staffconnector;

import java.util.function.LongSupplier;
import java.util.logging.Logger;

/** One warning per unstable incident; short successful responses do not restart warning spam. */
final class ConnectionWarnings {
    private final Logger log;
    private final LongSupplier clock;
    private boolean incident;
    private long lastWarning, healthySince = -1;
    ConnectionWarnings(Logger log) { this(log, () -> System.nanoTime() / 1_000_000_000L); }
    ConnectionWarnings(Logger log, LongSupplier clock) { this.log = log; this.clock = clock; }
    void failed(String message) {
        long now = clock.getAsLong(); healthySince = -1;
        if (!incident || now - lastWarning >= 900) { log.warning(message); lastWarning = now; }
        incident = true;
    }
    void healthy() {
        if (!incident) return;
        long now = clock.getAsLong();
        if (healthySince < 0) healthySince = now;
        if (now - healthySince >= 120) { log.info("Staff Portal delivery has been stable for two minutes; retained events continue draining."); incident = false; healthySince = -1; }
    }
}
