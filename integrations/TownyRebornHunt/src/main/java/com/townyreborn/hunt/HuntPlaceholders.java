package com.townyreborn.hunt;

import com.townyreborn.hunt.HuntProgress;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

public final class HuntPlaceholders
extends PlaceholderExpansion {
    private final Supplier<HuntProgress> current;
    private final String version;
    private final Consumer<IOException> errors;

    HuntPlaceholders(Supplier<HuntProgress> supplier, String string, Consumer<IOException> consumer) {
        this.current = supplier;
        this.version = string;
        this.errors = consumer;
    }

    public String getIdentifier() {
        return "townyrebornhunt";
    }

    public String getAuthor() {
        return "Towny Reborn";
    }

    public String getVersion() {
        return this.version;
    }

    public boolean persist() {
        return true;
    }

    public String onRequest(OfflinePlayer offlinePlayer, String string) {
        try {
            return this.current.get().request(offlinePlayer == null ? null : offlinePlayer.getUniqueId(), string);
        }
        catch (IOException iOException) {
            this.errors.accept(iOException);
            return "?";
        }
    }
}

