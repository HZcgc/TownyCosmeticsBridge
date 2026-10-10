package com.townyreborn.hunt;

import com.townyreborn.hunt.HuntLedger;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class HuntProgress {
    private final HuntLedger ledger;
    private final int maximum;
    private final ConcurrentHashMap<UUID, Cached> cache = new ConcurrentHashMap();

    HuntProgress(HuntLedger huntLedger, int n) {
        this.ledger = huntLedger;
        this.maximum = n;
    }

    void invalidate(UUID uUID) {
        this.cache.remove(uUID);
    }

    String request(UUID uUID, String string) throws IOException {
        String string2 = string.toLowerCase(Locale.ROOT);
        if (string2.equals("total")) {
            return Integer.toString(this.maximum);
        }
        if (!(string2.equals("found") || string2.equals("remaining") || string2.equals("progress"))) {
            return null;
        }
        int n = 0;
        if (uUID != null) {
            long l = System.nanoTime();
            Cached cached = this.cache.get(uUID);
            if (cached == null || cached.until() <= l) {
                cached = new Cached(this.ledger.summary(uUID).found(), l + 1000000000L);
                this.cache.put(uUID, cached);
            }
            n = cached.found();
        }
        return switch (string2) {
            case "found" -> Integer.toString(n);
            case "remaining" -> Integer.toString(Math.max(0, this.maximum - n));
            default -> n + "/" + this.maximum;
        };
    }

    private record Cached(int found, long until) {
    }
}

