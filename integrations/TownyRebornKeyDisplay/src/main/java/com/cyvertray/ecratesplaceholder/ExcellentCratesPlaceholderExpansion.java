package com.cyvertray.ecratesplaceholder;

import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import su.nightexpress.engine.id.IdentifierParser;
import su.nightexpress.excellentcrates.api.key.CrateKey;
import su.nightexpress.excellentcrates.api.key.KeysAPI;
import su.nightexpress.excellentcrates.api.key.balance.KeyBalanceAPI;
import su.nightexpress.excellentcrates.api.key.registry.KeyResolver;

/** EC7 replacement for Cyvertray_'s MIT-licensed ExcellentCratesAddon 2.1. */
public final class ExcellentCratesPlaceholderExpansion extends PlaceholderExpansion {
    private final KeyResolver resolver;
    private final KeyBalanceAPI balance;

    public ExcellentCratesPlaceholderExpansion(KeysAPI keys) {
        this.resolver = Objects.requireNonNull(keys.getRegistry());
        this.balance = Objects.requireNonNull(keys.getBalanceAPI());
    }

    @Override public String getIdentifier() { return "ec"; }
    @Override public String getAuthor() { return "Cyvertray_ / Towny Reborn"; }
    @Override public String getVersion() { return "2.2-ec7"; }
    @Override public boolean persist() { return true; }

    @Override public String onPlaceholderRequest(Player player, String params) {
        if (params.startsWith("totalkeys_")) {
            if (player == null) return "0";
            long total = 0;
            var seen = new HashSet<String>();
            for (String token : params.substring("totalkeys_".length()).split(",")) {
                String id = token.trim().toLowerCase(Locale.ROOT);
                if (!id.isEmpty() && seen.add(id)) total += count(player, id, false);
            }
            return Long.toString(total);
        }
        if (params.startsWith("keyz_")) {
            return player == null ? "0" : Integer.toString(count(player, params.substring("keyz_".length()), false));
        }
        if (params.startsWith("physicalkeyz_")) {
            return player == null ? "0" : Integer.toString(count(player, params.substring("physicalkeyz_".length()), true));
        }
        return null;
    }

    private int count(Player player, String keyId, boolean physical) {
        var id = IdentifierParser.parse(keyId.trim().toLowerCase(Locale.ROOT)).orElse(null);
        CrateKey key = id == null ? null : resolver.resolveKey(id);
        if (key == null || key.getBase().isVirtual() == physical) return 0;
        return physical ? balance.countPhysicalKeys(player, key) : balance.countVirtualKeys(player, key);
    }
}
