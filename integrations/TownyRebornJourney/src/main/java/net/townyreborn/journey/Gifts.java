package net.townyreborn.journey;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

final class Gifts {
   private final RebornPlugin plugin;
   private final GiftClaims claims;

   Gifts(final RebornPlugin var1) {
      this.plugin = var1;
      this.claims = new GiftClaims(var1.journey, var1.store, new GiftClaims.Backend() {
         @Override
         public CompletableFuture<Void> grant(UUID var1x, List<String> var2) {
            return var1.integrations.grant(var1x, var2);
         }

         @Override
         public boolean dispatch(String var1x) {
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), var1x);
         }

         @Override
         public boolean deposit(UUID id, double amount) {
            return var1.integrations.deposit(id, amount);
         }

         @Override
         public void townBonus(String var1x, int var2) throws Exception {
            var1.integrations.townBonus(var1x, var2);
         }

         @Override
         public void main(Runnable var1x) {
            var1.integrations.main(var1x);
         }
      });
   }

   void claim(Player var1, State var2, Journey.Gift var3) throws Exception {
      if (this.plugin.access(var1)) {
         String var4 = var3 != null && var3.townRequired() ? this.plugin.integrations.townName(var1) : "";
         this.plugin.message(var1, this.claims.claim(var2, var3, var4, var3x -> {
            this.plugin.message(var1, var3x);
            this.plugin.menu.refresh(var1);
            if (!var2.pendingGift.isEmpty()) {
               this.plugin.getLogger().warning("Reward receipt needs review for " + var2.uuid + " / " + var2.pendingGift);
            }
         }));
      }
   }

   void delivered(State var1) throws Exception {
      this.claims.delivered(var1);
   }
}
