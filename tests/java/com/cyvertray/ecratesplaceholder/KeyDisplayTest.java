package com.cyvertray.ecratesplaceholder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import me.clip.placeholderapi.replacer.CharsReplacer;
import su.nightexpress.engine.id.Identifier;
import su.nightexpress.excellentcrates.api.key.KeysAPI;
import su.nightexpress.excellentcrates.api.key.CrateKey;
import su.nightexpress.excellentcrates.api.key.balance.KeyBalanceAPI;
import su.nightexpress.excellentcrates.api.key.data.model.KeyBase;
import su.nightexpress.excellentcrates.api.key.registry.KeyRegistry;

class KeyDisplayTest {
    final KeysAPI keys = mock(KeysAPI.class);
    final KeyRegistry registry = mock(KeyRegistry.class);
    final KeyBalanceAPI balance = mock(KeyBalanceAPI.class);
    final Player player = mock(Player.class);

    ExcellentCratesPlaceholderExpansion expansion() {
        when(keys.getRegistry()).thenReturn(registry);
        when(keys.getBalanceAPI()).thenReturn(balance);
        when(player.getPlayer()).thenReturn(player);
        when(player.isOnline()).thenReturn(true);
        return new ExcellentCratesPlaceholderExpansion(keys);
    }

    CrateKey key(String id, boolean virtual) {
        CrateKey key = mock(CrateKey.class); KeyBase base = mock(KeyBase.class);
        when(registry.resolveKey(new Identifier(id))).thenReturn(key);
        when(key.getBase()).thenReturn(base); when(base.isVirtual()).thenReturn(virtual);
        return key;
    }

    @Test void legacyHologramUsesCurrentPlayerBalanceAndRefreshes() {
        var vote = key("vote", true); var expansion = expansion();
        when(balance.countVirtualKeys(player, vote)).thenReturn(7, 8);
        Player other = mock(Player.class);
        when(balance.countVirtualKeys(other, vote)).thenReturn(21);
        var parser = new CharsReplacer(CharsReplacer.Closure.PERCENT);
        assertEquals("Your Keys: 7", parser.apply("Your Keys: %ec_keyz_vote%", player, id -> id.equals("ec") ? expansion : null));
        assertEquals("Your Keys: 8", parser.apply("Your Keys: %ec_keyz_vote%", player, id -> id.equals("ec") ? expansion : null));
        assertEquals("21", expansion.onPlaceholderRequest(other, "keyz_vote"));
        verify(balance, times(2)).countVirtualKeys(player, vote);
        verify(balance).countVirtualKeys(other, vote);
        verifyNoMoreInteractions(balance);
    }

    @Test void physicalAndVirtualCountersRemainSeparate() {
        var virtual = key("vote", true); var physical = key("paper", false); var expansion = expansion();
        when(balance.countVirtualKeys(player, virtual)).thenReturn(5);
        when(balance.countPhysicalKeys(player, physical)).thenReturn(3);
        assertEquals("5", expansion.onPlaceholderRequest(player, "keyz_vote"));
        assertEquals("0", expansion.onPlaceholderRequest(player, "physicalkeyz_vote"));
        assertEquals("0", expansion.onPlaceholderRequest(player, "keyz_paper"));
        assertEquals("3", expansion.onPlaceholderRequest(player, "physicalkeyz_paper"));
        verify(balance).countVirtualKeys(player, virtual);
        verify(balance).countPhysicalKeys(player, physical);
        verifyNoMoreInteractions(balance);
    }

    @Test void totalIsUnformattedAndDoesNotOverflowOrDoubleCountKeys() {
        var expansion = expansion();
        for (var entry : Map.of("daily", 1234, "vote", 7, "patron", 4, "cosmetic", 0).entrySet()) {
            var key = key(entry.getKey(), true); when(balance.countVirtualKeys(player, key)).thenReturn(entry.getValue());
        }
        var parser = new CharsReplacer(CharsReplacer.Closure.PERCENT);
        assertEquals("Keys: 1245", parser.apply("Keys: %ec_totalkeys_daily,vote,patron,cosmetic%", player, id -> expansion));
        assertEquals("1245", expansion.onPlaceholderRequest(player, "totalkeys_daily,vote,patron,cosmetic,daily,missing"));
        var daily = registry.resolveKey(new Identifier("daily")); var vote = registry.resolveKey(new Identifier("vote"));
        when(balance.countVirtualKeys(player, daily)).thenReturn(Integer.MAX_VALUE);
        when(balance.countVirtualKeys(player, vote)).thenReturn(Integer.MAX_VALUE);
        assertEquals("4294967294", expansion.onPlaceholderRequest(player, "totalkeys_daily,vote"));
    }

    @Test void missingKeyAndAbsentPlayerReturnZeroAndExpansionPersists() {
        var expansion = expansion();
        assertEquals("0", expansion.onPlaceholderRequest(player, "keyz_missing"));
        assertEquals("0", expansion.onPlaceholderRequest(player, "keyz_bad id"));
        assertEquals("0", expansion.onPlaceholderRequest(null, "keyz_vote"));
        assertEquals("0", expansion.onPlaceholderRequest(null, "totalkeys_daily,vote"));
        assertNull(expansion.onPlaceholderRequest(player, "unknown"));
        assertTrue(expansion.persist());
        verifyNoInteractions(balance);
    }
}
