package com.townyreborn.hunt;

import com.townyreborn.hunt.HuntLedger;
import java.io.IOException;
import java.util.UUID;

final class ClaimService {
    private final HuntLedger ledger;

    ClaimService(HuntLedger huntLedger) {
        this.ledger = huntLedger;
    }

    synchronized Result claim(UUID uUID, String string, String string2, int n, String string3, Gateway gateway) {
        Payment object;
        HuntLedger.Summary summary;
        try {
            summary = this.ledger.summary(uUID);
            String state = this.ledger.state(uUID, string2);
            if (state.equals("FOUND")) {
                return new Result(Kind.ALREADY_FOUND, summary.found(), null);
            }
            if (state.equals("PENDING")) {
                return new Result(Kind.PENDING, summary.found(), null);
            }
            if (summary.claimed() >= n) {
                return new Result(Kind.LIMIT, summary.found(), null);
            }
        }
        catch (IOException | RuntimeException exception) {
            return new Result(Kind.STORAGE_ERROR, 0, exception);
        }
        try {
            object = gateway.prepare();
        }
        catch (Exception exception) {
            return new Result(Kind.UNAVAILABLE, summary.found(), exception);
        }
        try {
            HuntLedger.Begin begin = this.ledger.reserve(uUID, string, string2, n, string3, object.balanceBefore());
            if (begin != HuntLedger.Begin.RESERVED) {
                Kind kind = switch (begin) {
                    case HuntLedger.Begin.ALREADY_FOUND -> Kind.ALREADY_FOUND;
                    case HuntLedger.Begin.PENDING -> Kind.PENDING;
                    case HuntLedger.Begin.LIMIT -> Kind.LIMIT;
                    default -> throw new IllegalStateException();
                };
                return new Result(kind, summary.found(), null);
            }
        }
        catch (IOException | RuntimeException exception) {
            return new Result(Kind.STORAGE_ERROR, summary.found(), exception);
        }
        try {
            object.giveOne();
        }
        catch (Exception exception) {
            try {
                if (object.definitelyNotGiven()) {
                    this.ledger.rollback(uUID, string2);
                    return new Result(Kind.UNAVAILABLE, summary.found(), exception);
                }
            }
            catch (Exception exception2) {
                exception.addSuppressed(exception2);
            }
            return new Result(Kind.PENDING, summary.found(), exception);
        }
        try {
            this.ledger.finish(uUID, string2);
        }
        catch (IOException | RuntimeException exception) {
            return new Result(Kind.PENDING, summary.found(), exception);
        }
        return new Result(Kind.FOUND, summary.found() + 1, null);
    }

    record Result(Kind kind, int found, Exception error) {
    }

    static enum Kind {
        FOUND,
        ALREADY_FOUND,
        LIMIT,
        PENDING,
        UNAVAILABLE,
        STORAGE_ERROR;

    }

    static interface Gateway {
        public Payment prepare() throws Exception;
    }

    static interface Payment {
        public int balanceBefore();

        public void giveOne() throws Exception;

        public boolean definitelyNotGiven() throws Exception;
    }
}

