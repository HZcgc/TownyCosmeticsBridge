package com.townyreborn.hunt;

import com.townyreborn.compat.ExcellentCrates7;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class CrateBridge {
    private final ExcellentCrates7 api;

    CrateBridge(Object plugin) throws ReflectiveOperationException {
        api = new ExcellentCrates7((Plugin)plugin);
    }

    void check(String id) throws ReflectiveOperationException {
        api.virtualKey(id);
    }

    int balance(Player player, String id) throws ReflectiveOperationException {
        return api.keyBalance(player, api.virtualKey(id));
    }

    ClaimService.Payment prepare(Player player, String id) throws ReflectiveOperationException {
        if (!player.isOnline()) throw new IllegalStateException("The player is offline.");
        Object key = api.virtualKey(id);
        int before = api.keyBalance(player, key);
        if (before < 0 || before == Integer.MAX_VALUE) throw new IllegalStateException("Invalid key balance.");
        return new ClaimService.Payment() {
            public int balanceBefore() { return before; }
            public void giveOne() throws ReflectiveOperationException {
                api.giveOneKey(player, key);
                if (api.keyBalance(player, key) != before + 1) {
                    throw new IllegalStateException("Native key balance did not increase by exactly one.");
                }
            }
            public boolean definitelyNotGiven() throws ReflectiveOperationException {
                return api.keyBalance(player, key) == before;
            }
        };
    }
}
