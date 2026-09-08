package net.townysmp.cosmetics;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class TownyCosmeticsBridge extends JavaPlugin {
   private static final String CRATE_ID = "cosmetic";
   private static final String REWARD_ID = "random_cosmetic";
   private static final String COSMETIC_KEY_ID = "cosmetic";
   private static final String COMPENSATION_KEY_ID = "patron";
   private static final String DEFAULT_REWARD_NAME = "<#ff55ff><b>Random</b></#ff55ff> <#55ff55><b>Cosmetic</b></#55ff55>";
   private static final String COMPLETE_REWARD_NAME = "<#ff55ff><b>Collection Complete</b></#ff55ff> <dark_gray>»</dark_gray> <#55ff55>Patron Key</#55ff55>";
   private static final String ERROR_REWARD_NAME = "<red><b>Reward Error</b></red> <dark_gray>»</dark_gray> <#55ff55>Cosmetic Key Refunded</#55ff55>";
   private static final Pattern LEGACY_HEX = Pattern.compile("(?i)§x(?:§[0-9a-f]){6}");
   private static final Pattern LEGACY_CODE = Pattern.compile("(?i)§[0-9a-fk-or]");
   private final AtomicLong rewardNameGeneration = new AtomicLong();
   private Plugin ultraCosmetics;
   private Plugin excellentCrates;

   public void onEnable() {
      this.ultraCosmetics = this.getServer().getPluginManager().getPlugin("UltraCosmetics");
      this.excellentCrates = this.getServer().getPluginManager().getPlugin("ExcellentCrates");
      if (this.ultraCosmetics != null && this.excellentCrates != null) {
         try {
            this.validateUltraCosmeticsProfileApi();
            this.setExcellentCratesRewardName("<#ff55ff><b>Random</b></#ff55ff> <#55ff55><b>Cosmetic</b></#55ff55>");
            this.getLogger().info("Profile-based cosmetic rewards and Patron compensation are active.");
         } catch (ReflectiveOperationException | IllegalStateException var2) {
            this.getLogger().severe("Could not connect UltraCosmetics to ExcellentCrates: " + var2.getMessage());
            this.getServer().getPluginManager().disablePlugin(this);
         }
      } else {
         this.getLogger().severe("UltraCosmetics and ExcellentCrates must both be enabled.");
         this.getServer().getPluginManager().disablePlugin(this);
      }
   }

   public void onDisable() {
      if (this.excellentCrates != null) {
         try {
            this.setExcellentCratesRewardName("<#ff55ff><b>Random</b></#ff55ff> <#55ff55><b>Cosmetic</b></#55ff55>");
         } catch (ReflectiveOperationException | IllegalStateException var2) {
         }
      }
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if (!var2.getName().equalsIgnoreCase("townycosmeticsreward")) {
         return false;
      }

      if (var1 instanceof Player) {
         var1.sendMessage("§cThis command can only be executed by the server.");
         return true;
      }

      if (var4.length != 1) {
         var1.sendMessage("Usage: townycosmeticsreward <player>");
         return true;
      }

      Player var5 = this.getServer().getPlayerExact(var4[0]);
      if (var5 == null) {
         var1.sendMessage("Player is not online: " + var4[0]);
         return true;
      }

      try {
         Object var6 = this.chooseLockedProfileCosmetic(var5);
         if (var6 == null) {
            this.handleCompleteCollection(var5);
            return true;
         }

         this.grantProfileCosmetic(var5, var6);
         String var7 = this.readCosmeticName(var6);
         this.showRewardName("<#ff55ff><b>" + escapeMiniMessage(var7) + "</b></#ff55ff>");
         var5.sendMessage("§d§lTOWNY§a§lREBORN§r §8» §7You unlocked §d" + var7 + "§7.");
      } catch (ReflectiveOperationException | IllegalStateException var8) {
         this.refundCosmeticKeyAfterError(var5, var8);
      }

      return true;
   }

   private void validateUltraCosmeticsProfileApi() throws ReflectiveOperationException {
      Object var1 = this.ultraCosmetics.getClass().getMethod("getPlayerManager").invoke(this.ultraCosmetics);
      this.ultraCosmetics.getClass().getMethod("getPermissionManager").invoke(this.ultraCosmetics);
      this.ultraCosmetics.getClass().getMethod("getConfig").invoke(this.ultraCosmetics);
      Class var2 = Class.forName("be.isach.ultracosmetics.cosmetics.Category", true, this.ultraCosmetics.getClass().getClassLoader());
      if (var1 == null || var2.getEnumConstants() == null) {
         throw new IllegalStateException("UltraCosmetics profile API is unavailable");
      }
   }

   private Object chooseLockedProfileCosmetic(Player var1) throws ReflectiveOperationException {
      Object var2 = this.getProfile(var1);
      if (!(var2.getClass().getMethod("getAllUnlocked").invoke(var2) instanceof Collection var4)) {
         throw new IllegalStateException("UltraCosmetics returned an invalid unlocked-cosmetics collection");
      } else {
         Object var5 = this.ultraCosmetics.getClass().getMethod("getConfig").invoke(this.ultraCosmetics);
         Method var6 = var5.getClass().getMethod("getBoolean", String.class);
         Method var7 = var5.getClass().getMethod("getInt", String.class);
         Class var8 = Class.forName("be.isach.ultracosmetics.cosmetics.Category", true, this.ultraCosmetics.getClass().getClassLoader());
         Method var9 = var8.getMethod("isEnabled");
         Method var10 = var8.getMethod("getConfigPath");
         Method var11 = var8.getMethod("getEnabled");
         ArrayList var12 = new ArrayList();

         for (Object var16 : var8.getEnumConstants()) {
            if (Boolean.TRUE.equals(var9.invoke(var16))) {
               String var17 = String.valueOf(var10.invoke(var16));
               String var18 = "TreasureChests.Loots." + var17;
               if (Boolean.TRUE.equals(var6.invoke(var5, var18 + ".Enabled"))) {
                  int var19 = ((Number)var7.invoke(var5, var18 + ".Chance")).intValue();
                  if (var19 > 0 && var11.invoke(var16) instanceof Collection var21) {
                     ArrayList var22 = new ArrayList();

                     for (Object var24 : var21) {
                        if (!var4.contains(var24) && Boolean.TRUE.equals(var24.getClass().getMethod("isEnabled").invoke(var24))) {
                           int var25 = ((Number)var24.getClass().getMethod("getChestWeight").invoke(var24)).intValue();
                           if (var25 > 0) {
                              var22.add(new TownyCosmeticsBridge.WeightedCosmetic(var24, var25));
                           }
                        }
                     }

                     if (!var22.isEmpty()) {
                        var12.add(new TownyCosmeticsBridge.WeightedCategory(var22, var19));
                     }
                  }
               }
            }
         }

         if (var12.isEmpty()) {
            return null;
         }

         TownyCosmeticsBridge.WeightedCategory var26 = pickWeightedCategory(var12);
         return pickWeightedCosmetic(var26.cosmetics()).cosmetic();
      }
   }

   private Object getProfile(Player var1) throws ReflectiveOperationException {
      Object var2 = this.ultraCosmetics.getClass().getMethod("getPlayerManager").invoke(this.ultraCosmetics);
      Method var3 = findCompatibleMethod(var2.getClass(), "getUltraPlayer", var1);
      Object var4 = var3.invoke(var2, var1);
      if (var4 == null) {
         throw new IllegalStateException("UltraCosmetics player profile is not loaded");
      } else {
         Object var5 = var4.getClass().getMethod("getProfile").invoke(var4);
         if (var5 == null) {
            throw new IllegalStateException("UltraCosmetics player profile is unavailable");
         } else {
            return var5;
         }
      }
   }

   private void grantProfileCosmetic(Player var1, Object var2) throws ReflectiveOperationException {
      Object var3 = this.ultraCosmetics.getClass().getMethod("getPermissionManager").invoke(this.ultraCosmetics);
      Method var4 = findCompatibleMethod(var3.getClass(), "setPermission", var1, var2);
      var4.invoke(var3, var1, var2);
      Object var5 = this.getProfile(var1);
      if (!(var5.getClass().getMethod("getAllUnlocked").invoke(var5) instanceof Collection var7 && var7.contains(var2))) {
         throw new IllegalStateException("UltraCosmetics did not persist the cosmetic in the player profile");
      }
   }

   private String readCosmeticName(Object var1) throws ReflectiveOperationException {
      Object var2 = var1.getClass().getMethod("getName").invoke(var1);
      Class var3 = Class.forName("be.isach.ultracosmetics.config.MessageManager", true, this.ultraCosmetics.getClass().getClassLoader());

      for (Method var7 : var3.getMethods()) {
         if (var7.getName().equals("toLegacy")
            && Modifier.isStatic(var7.getModifiers())
            && var7.getParameterCount() == 1
            && var7.getParameterTypes()[0].isInstance(var2)
            && var7.invoke(null, var2) instanceof String var9) {
            return stripLegacyFormatting(var9).trim();
         }
      }

      Object var10 = var1.getClass().getMethod("getConfigName").invoke(var1);
      return humanize(String.valueOf(var10));
   }

   private void handleCompleteCollection(Player var1) {
      if (this.giveVirtualKey(var1, "patron", "Patron Key")) {
         this.showRewardName("<#ff55ff><b>Collection Complete</b></#ff55ff> <dark_gray>»</dark_gray> <#55ff55>Patron Key</#55ff55>");
         var1.sendMessage("§d§lTOWNY§a§lREBORN§r §8» §7Collection complete. §aYou received 1 virtual Patron Key.§7");
      } else {
         this.giveVirtualKey(var1, "cosmetic", "Cosmetic Key");
         this.showRewardName("<red><b>Reward Error</b></red> <dark_gray>»</dark_gray> <#55ff55>Cosmetic Key Refunded</#55ff55>");
         var1.sendMessage("§d§lTOWNY§a§lREBORN§r §8» §cThe Patron Key could not be granted. §aYour virtual Cosmetic Key was returned.§7");
      }
   }

   private void refundCosmeticKeyAfterError(Player var1, Exception var2) {
      this.giveVirtualKey(var1, "cosmetic", "Cosmetic Key");
      this.showRewardName("<red><b>Reward Error</b></red> <dark_gray>»</dark_gray> <#55ff55>Cosmetic Key Refunded</#55ff55>");
      var1.sendMessage("§d§lTOWNY§a§lREBORN§r §8» §cThe cosmetic reward could not be processed. §aYour virtual Cosmetic Key was returned.§7");
      this.getLogger().severe("Could not process a cosmetic reward for " + var1.getName() + ": " + var2.getMessage());
   }

   private boolean giveVirtualKey(Player var1, String var2, String var3) {
      boolean var4 = this.getServer().dispatchCommand(this.getServer().getConsoleSender(), "crates key give " + var1.getName() + " " + var2 + " 1 -sf");
      if (!var4) {
         this.getLogger().severe("Could not grant the virtual " + var3 + " for " + var1.getName() + ".");
      }

      return var4;
   }

   private void showRewardName(String var1) {
      long var2 = this.rewardNameGeneration.incrementAndGet();

      try {
         this.setExcellentCratesRewardName(var1);
      } catch (ReflectiveOperationException | IllegalStateException var5) {
         this.getLogger().severe("Could not update the crate reward name: " + var5.getMessage());
         return;
      }

      this.getServer().getScheduler().runTask(this, () -> {
         if (this.rewardNameGeneration.get() == var2) {
            try {
               this.setExcellentCratesRewardName("<#ff55ff><b>Random</b></#ff55ff> <#55ff55><b>Cosmetic</b></#55ff55>");
            } catch (ReflectiveOperationException | IllegalStateException var4) {
               this.getLogger().warning("Could not restore the default crate reward name: " + var4.getMessage());
            }
         }
      });
   }

   private void setExcellentCratesRewardName(String var1) throws ReflectiveOperationException {
      Object var2 = this.excellentCrates.getClass().getMethod("getCrateManager").invoke(this.excellentCrates);
      Object var3 = var2.getClass().getMethod("getCrateById", String.class).invoke(var2, "cosmetic");
      if (var3 == null) {
         throw new IllegalStateException("Missing ExcellentCrates crate: cosmetic");
      }

      Object var4 = var3.getClass().getMethod("getReward", String.class).invoke(var3, "random_cosmetic");
      if (var4 == null) {
         throw new IllegalStateException("Missing ExcellentCrates reward: random_cosmetic");
      }

      var4.getClass().getMethod("setName", String.class).invoke(var4, var1);
   }

   private static Method findCompatibleMethod(Class<?> var0, String var1, Object... var2) throws NoSuchMethodException {
      for (Method var6 : var0.getMethods()) {
         if (var6.getName().equals(var1) && var6.getParameterCount() == var2.length) {
            Class[] var7 = var6.getParameterTypes();
            boolean var8 = true;

            for (int var9 = 0; var9 < var2.length; var9++) {
               if (var2[var9] != null && !var7[var9].isInstance(var2[var9])) {
                  var8 = false;
                  break;
               }
            }

            if (var8) {
               return var6;
            }
         }
      }

      throw new NoSuchMethodException(var0.getName() + "." + var1);
   }

   private static TownyCosmeticsBridge.WeightedCategory pickWeightedCategory(List<TownyCosmeticsBridge.WeightedCategory> var0) {
      long var1 = var0.stream().mapToLong(TownyCosmeticsBridge.WeightedCategory::weight).sum();
      long var3 = ThreadLocalRandom.current().nextLong(var1);

      for (TownyCosmeticsBridge.WeightedCategory var6 : var0) {
         var3 -= var6.weight();
         if (var3 < 0L) {
            return var6;
         }
      }

      throw new IllegalStateException("Could not select a cosmetic category");
   }

   private static TownyCosmeticsBridge.WeightedCosmetic pickWeightedCosmetic(List<TownyCosmeticsBridge.WeightedCosmetic> var0) {
      long var1 = var0.stream().mapToLong(TownyCosmeticsBridge.WeightedCosmetic::weight).sum();
      long var3 = ThreadLocalRandom.current().nextLong(var1);

      for (TownyCosmeticsBridge.WeightedCosmetic var6 : var0) {
         var3 -= var6.weight();
         if (var3 < 0L) {
            return var6;
         }
      }

      throw new IllegalStateException("Could not select a cosmetic");
   }

   private static String stripLegacyFormatting(String var0) {
      String var1 = LEGACY_HEX.matcher(var0).replaceAll("");
      return LEGACY_CODE.matcher(var1).replaceAll("");
   }

   private static String escapeMiniMessage(String var0) {
      return var0.replace("<", "").replace(">", "").replace("\\", "");
   }

   private static String humanize(String var0) {
      return var0.replace('_', ' ').replace('-', ' ').replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ").trim();
   }

   private record WeightedCategory(List<TownyCosmeticsBridge.WeightedCosmetic> cosmetics, int weight) {
   }

   private record WeightedCosmetic(Object cosmetic, int weight) {
   }
}
