package com.townyreborn.hunt;

import com.townyreborn.hunt.HuntPlaceholders;
import com.townyreborn.hunt.HuntProgress;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

final class PlaceholderSupport {
    private PlaceholderSupport() {
    }

    static Runnable register(Supplier<HuntProgress> supplier, String string, Consumer<IOException> consumer, Logger logger) {
        HuntPlaceholders huntPlaceholders = new HuntPlaceholders(supplier, string, consumer);
        if (huntPlaceholders.register()) {
            logger.info("Registered PlaceholderAPI expansion townyrebornhunt (found, total, remaining, progress).");
            return () -> huntPlaceholders.unregister();
        }
        logger.warning("Could not register townyrebornhunt placeholders; check for a duplicate hunt plugin JAR.");
        return () -> {};
    }
}

