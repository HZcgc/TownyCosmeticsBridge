package com.townyreborn.compat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;
import su.nightexpress.engine.action.ActionResult;
import su.nightexpress.engine.id.Identifier;
import su.nightexpress.excellentcrates.api.ExcellentCratesAPI;
import su.nightexpress.excellentcrates.api.crate.Crate;
import su.nightexpress.excellentcrates.api.crate.CratesAPI;
import su.nightexpress.excellentcrates.api.crate.registry.CrateRegistry;
import su.nightexpress.excellentcrates.api.crate.interact.InteractionAPI;
import su.nightexpress.excellentcrates.api.crate.interact.context.CrateInteractContext;
import su.nightexpress.excellentcrates.api.key.KeysAPI;
import su.nightexpress.excellentcrates.api.key.CrateKey;
import su.nightexpress.excellentcrates.api.key.registry.KeyRegistry;
import su.nightexpress.excellentcrates.api.key.data.model.KeyBase;
import su.nightexpress.excellentcrates.api.key.balance.KeyBalanceAPI;
import su.nightexpress.excellentcrates.api.reward.Reward;
import su.nightexpress.excellentcrates.api.reward.RewardsAPI;
import su.nightexpress.excellentcrates.api.reward.data.model.RewardPreview;

class ExcellentCratesApiTest {
    final ExcellentCratesAPI service=mock(ExcellentCratesAPI.class);
    ExcellentCrates7 adapter() throws Exception {
        Plugin plugin=mock(Plugin.class);
        Server server=mock(Server.class);
        ServicesManager services=mock(ServicesManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getServicesManager()).thenReturn(services);
        when(services.load(ExcellentCratesAPI.class)).thenReturn(service);
        return new ExcellentCrates7(plugin);
    }

    @Test void nativeVirtualKeyIsResolvedCountedAndGrantedOnce() throws Exception {
        KeysAPI keys=mock(KeysAPI.class);
        KeyRegistry registry=mock(KeyRegistry.class);
        KeyBalanceAPI balance=mock(KeyBalanceAPI.class);
        CrateKey key=mock(CrateKey.class);
        KeyBase base=mock(KeyBase.class);
        Player player=mock(Player.class);
        when(service.getKeysAPI()).thenReturn(Optional.of(keys));
        when(keys.getRegistry()).thenReturn(registry);
        when(keys.getBalanceAPI()).thenReturn(balance);
        when(registry.resolveKey(new Identifier("halloween2026"))).thenReturn(key);
        when(key.getBase()).thenReturn(base);when(base.isVirtual()).thenReturn(true);
        when(player.isOnline()).thenReturn(true);
        when(balance.countVirtualKeys(player,key)).thenReturn(7);
        ActionResult result=mock(ActionResult.class);when(result.success()).thenReturn(true);
        when(balance.addKey(player,key,1)).thenReturn(result);
        ExcellentCrates7 api=adapter();
        assertSame(key,api.virtualKey("halloween2026"));assertEquals(7,api.keyBalance(player,key));
        api.giveOneKey(player,key);verify(balance,times(1)).addKey(player,key,1);
        when(result.success()).thenReturn(false);
        assertThrows(IllegalStateException.class,()->api.giveOneKey(player,key));
        when(base.isVirtual()).thenReturn(false);
        assertThrows(IllegalStateException.class,()->api.virtualKey("halloween2026"));
    }

    @Test void modelClickPassesItsLocationToNativePipeline() throws Exception {
        CratesAPI crates=mock(CratesAPI.class);
        CrateRegistry registry=mock(CrateRegistry.class);
        InteractionAPI interactions=mock(InteractionAPI.class);
        Crate crate=mock(Crate.class);Player player=mock(Player.class);
        when(service.getCratesAPI()).thenReturn(crates);when(crates.registry()).thenReturn(registry);
        when(crates.interaction()).thenReturn(interactions);
        when(registry.resolveCrate(new Identifier("vote"))).thenReturn(crate);
        when(crate.getId()).thenReturn(new Identifier("vote"));when(registry.crates()).thenReturn(Set.of(crate));
        Location location=new Location(null,161,81,44);
        ExcellentCrates7 api=adapter();api.interact(player,"vote",false,location);
        var context=org.mockito.ArgumentCaptor.forClass(CrateInteractContext.class);
        verify(interactions).performAction(context.capture(),any(Identifier.class));
        assertSame(player,context.getValue().player());
        assertSame(location,((su.nightexpress.excellentcrates.api.crate.interact.context.LocatedCrateInteractContext)context.getValue()).location());
        assertEquals(java.util.List.of("vote"),api.crateIds());
        assertTrue(org.bukkit.event.Cancellable.class.isAssignableFrom(api.openingEvent()));
    }

    @Test void rewardLabelUsesNativePreviewAndDisabledModuleFailsClearly() throws Exception {
        RewardsAPI rewards=mock(RewardsAPI.class);Reward reward=mock(Reward.class);RewardPreview preview=mock(RewardPreview.class);
        when(service.getRewardAPI()).thenReturn(Optional.of(rewards));
        when(rewards.getReward(new Identifier("cosmetic"),new Identifier("random_cosmetic"))).thenReturn(reward);
        when(reward.getPreview()).thenReturn(preview);
        ExcellentCrates7 api=adapter();api.setRewardName("cosmetic","random_cosmetic","Unlocked!");
        verify(preview).setName("Unlocked!");
        when(service.getKeysAPI()).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class,()->api.virtualKey("vote"));
    }
}
