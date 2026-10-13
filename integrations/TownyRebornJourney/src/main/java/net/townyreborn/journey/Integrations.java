package net.townyreborn.journey;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.node.types.PermissionNode.Builder;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

final class Integrations implements Purchases.Backend {
   private final RebornPlugin plugin;
   final LuckPerms luck = LuckPermsProvider.get();
   final Economy economy;

   Integrations(RebornPlugin var1) {
      this.plugin = var1;
      RegisteredServiceProvider var2 = var1.getServer().getServicesManager().getRegistration(Economy.class);
      if (var2 == null) {
         throw new IllegalStateException("Vault economy provider is missing");
      }

      this.economy = (Economy)var2.getProvider();
   }

   @Override
   public double balance(UUID var1) {
      return this.economy.getBalance(Bukkit.getOfflinePlayer(var1));
   }

   @Override
   public boolean withdraw(UUID var1, double var2) {
      return this.economy.withdrawPlayer(Bukkit.getOfflinePlayer(var1), var2).transactionSuccess();
   }

   public boolean deposit(UUID id, double amount) {
      return this.economy.depositPlayer(Bukkit.getOfflinePlayer(id), amount).transactionSuccess();
   }

   @Override
   public void main(Runnable var1) {
      if (this.plugin.isEnabled()) {
         Bukkit.getScheduler().runTask(this.plugin, var1);
      }
   }

   @Override
   public CompletableFuture<Void> grant(UUID var1, List<String> var2) {
      User var3 = this.luck.getUserManager().getUser(var1);
      return var3 == null ? this.luck.getUserManager().loadUser(var1).thenCompose(var2x -> this.grantTo(var2x, var2)) : this.grantTo(var3, var2);
   }

   private CompletableFuture<Void> grantTo(User var1, List<String> var2) {
      String var3 = this.plugin.getConfig().getString("permission-context-server", "smp");

      for (String var5 : var2) {
         Builder var6 = (Builder)PermissionNode.builder(var5).value(true);
         if (!var3.isBlank()) {
            var6.withContext("server", var3);
         }

         var1.data().add(var6.build());
      }

      return this.luck.getUserManager().saveUser(var1);
   }

   Set<String> explicitPermanentNodes(UUID var1) {
      User var2 = this.luck.getUserManager().getUser(var1);
      if (var2 == null) {
         return Set.of();
      }

      String var3 = this.plugin.getConfig().getString("permission-context-server", "smp");
      HashSet var4 = new HashSet();

      for (Node var6 : var2.getNodes()) {
         if (var6.getValue() && !var6.hasExpiry()) {
            Set var7 = var6.getContexts().getValues("server");
            if ((var7.isEmpty() || var7.contains(var3)) && !var6.getContexts().toMap().keySet().stream().anyMatch(var0 -> !var0.equals("server"))) {
               var4.add(var6.getKey());
            }
         }
      }

      return var4;
   }

   List<String> importOwned(State var1) {
      Set var2 = this.explicitPermanentNodes(var1.uuid);

      for (Journey.Perk var4 : this.plugin.journey.perks.values()) {
         if (var2.contains(Journey.perkMarker(var4.id())) || var4.legacy().stream().anyMatch(var2::contains)) {
            var1.owned.add(var4.id());
         }
      }

      this.plugin.journey.importGiftMarkers(var1, var2);

      this.plugin.journey.inheritLowerPerks(var1.owned);
      LinkedHashSet var8 = new LinkedHashSet();

      for (String var5 : var1.owned) {
         Journey.Perk var6 = this.plugin.journey.perks.get(var5);
         if (var6 != null) {
            var8.addAll(var6.permissions());
            var8.add(Journey.perkMarker(var5));
         }
      }

      for (String var12 : var1.claimed) {
         Journey.Gift var13 = this.plugin.journey.gifts.get(var12);
         if (var13 != null) {
            var8.addAll(var13.permissions());
            var8.add(Journey.giftMarker(var12));
         }
      }

      var8.removeAll(var2);
      return List.copyOf(var8);
   }

   Map<String, Long> jobsLevels(Player var1) {
      HashMap var2 = new HashMap();
      Plugin var3 = Bukkit.getPluginManager().getPlugin("Jobs");
      if (var3 == null) {
         return var2;
      }

      try {
         Object var4 = var3.getClass().getMethod("getPlayerManager").invoke(var3);
         Object var5 = var4.getClass().getMethod("getJobsPlayer", Player.class).invoke(var4, var1);
         if (var5 == null) {
            return var2;
         }

         for (Object var8 : (List)var5.getClass().getMethod("getJobProgression").invoke(var5)) {
            Object var9 = var8.getClass().getMethod("getJob").invoke(var8);
            String var10 = (String)var9.getClass().getMethod("getName").invoke(var9);
            long var11 = ((Number)var8.getClass().getMethod("getLevel").invoke(var8)).longValue();
            var2.put("job:" + var10, var11);
         }
      } catch (ReflectiveOperationException | LinkageError var13) {
         this.plugin.warnOnce("jobs-api", "Jobs level integration is unavailable: " + var13.getClass().getSimpleName());
      }

      return var2;
   }

   private Object townyApi() throws ReflectiveOperationException {
      Plugin var1 = Bukkit.getPluginManager().getPlugin("Towny");
      if (var1 != null && var1.isEnabled()) {
         Class var2 = Class.forName("com.palmergames.bukkit.towny.TownyAPI", true, var1.getClass().getClassLoader());
         return var2.getMethod("getInstance").invoke(null);
      } else {
         throw new IllegalStateException("Towny is unavailable");
      }
   }

   String townName(Player var1) {
      try {
         Object var2 = this.townyApi();
         Object var3 = var2.getClass().getMethod("getTown", Player.class).invoke(var2, var1);
         return var3 == null ? "" : (String)var3.getClass().getMethod("getName").invoke(var3);
      } catch (ReflectiveOperationException | RuntimeException | LinkageError var4) {
         this.plugin.warnOnce("towny-api", "Town rewards are unavailable until the Towny integration is ready.");
         return "";
      }
   }

   void townBonus(String var1, int var2) throws Exception {
      Object var3 = this.townyApi();
      Object var4 = var3.getClass().getMethod("getTown", String.class).invoke(var3, var1);
      if (var4 == null) {
         throw new IllegalStateException("The reward town no longer exists");
      }

      var4.getClass().getMethod("addBonusBlocks", int.class).invoke(var4, var2);
      var4.getClass().getMethod("save").invoke(var4);
   }
}
