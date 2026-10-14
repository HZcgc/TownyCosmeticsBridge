package net.townyreborn.journey;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class RebornPlugin extends JavaPlugin implements CommandExecutor {
   public static final String ACCESS = "townyreborn.progression.access";
   volatile Journey journey;
   Store store;
   Integrations integrations;
   Purchases purchases;
   Menu menu;
   Gifts gifts;
   ProgressSources sources;
   RebornExpansion expansion;
   RebornExpansion compatibility;
   private final Set<String> warnings = ConcurrentHashMap.newKeySet();
   private final Set<UUID> activated = new HashSet<>();

   public void onEnable() {
      try {
         this.prepareConfig();
         this.journey = new Journey(this.getConfig());
         this.store = new Store(this.getDataFolder().toPath(), this.journey);
         this.integrations = new Integrations(this);
         this.purchases = new Purchases(this.journey, this.store, this.integrations);
         this.menu = new Menu(this);
         this.gifts = new Gifts(this);
         this.sources = new ProgressSources(this);

         Objects.requireNonNull(this.getCommand("reborn")).setExecutor(this);
         Objects.requireNonNull(this.getCommand("rebornadmin")).setExecutor(this);
         this.getServer().getPluginManager().registerEvents(this.menu, this);
         this.getServer().getPluginManager().registerEvents(this.sources, this);
         this.expansion = new RebornExpansion(this, "reborn");
         this.compatibility = new RebornExpansion(this, "trprogress");
         if (!this.expansion.register() || !this.compatibility.register()) {
            throw new IllegalStateException("Placeholder registration failed");
         }

         this.getServer().getScheduler().runTaskTimer(this, this.sources::poll, 600L, 600L);

         for (Player var2 : this.getServer().getOnlinePlayers()) {
            if (this.access(var2)) {
               this.activate(var2, this.state(var2));
               this.sources.active(var2);
            }
         }

         this.getLogger()
            .info(
               "Standalone Reborn journey ready: "
                  + this.journey.steps
                  + " levels; player access requires townyreborn.progression.access. No Quests dependency or global pause."
            );
      } catch (Exception var3) {
         this.getLogger().log(Level.SEVERE, "Could not enable standalone Reborn progression", var3);
         this.getServer().getPluginManager().disablePlugin(this);
      }
   }

   public void onDisable() {
      if (this.store != null) {
         for (State var2 : this.store.cached()) {
            if (var2.persisted && (var2.dirty || var2.busy())) {
               try {
                  this.store.save(var2);
               } catch (Exception var4) {
                  this.getLogger().severe("Could not save journey profile " + var2.uuid);
               }
            }
         }
      }

      if (this.expansion != null) {
         this.expansion.unregister();
      }

      if (this.compatibility != null) {
         this.compatibility.unregister();
      }
   }

   private void prepareConfig() throws Exception {
      this.saveDefaultConfig();
      Path file = this.getDataFolder().toPath().resolve("config.yml");
      YamlConfiguration raw = new YamlConfiguration();
      raw.loadFromString(Files.readString(file));
      if (!raw.isConfigurationSection("journey"))
         throw new IllegalArgumentException("Install the supplied TownyRebornProgression/config.yml; existing configuration was not changed.");
   }

   boolean access(Player var1) {
      return var1 != null && var1.hasPermission("townyreborn.progression.access") && !var1.hasMetadata("NPC");
   }

   State state(Player var1) throws Exception {
      return this.store.get(var1.getUniqueId(), var1.getName());
   }

   void deactivate(UUID var1) {
      this.activated.remove(var1);
   }

   void activate(Player var1, State var2) throws Exception {
      if (this.access(var1) && this.activated.add(var1.getUniqueId())) {
         try {
            var2.name = var1.getName();
            List var3 = this.integrations.importOwned(var2);
            this.store.save(var2);
            if (!var3.isEmpty()) {
               this.integrations
                  .grant(var2.uuid, var3)
                  .whenComplete(
                     (var2x, var3x) -> {
                        if (var3x != null) {
                           this.warnOnce(
                              "permissions-" + var2.uuid,
                              "Permanent perk permissions need a save retry for " + var2.uuid + "; no purchase or reward command will be repeated."
                           );
                        }
                     }
                  );
            }
         } catch (Exception var4) {
            this.activated.remove(var1.getUniqueId());
            var2.blocked = true;
            throw var4;
         }
      }
   }

   void warnOnce(String var1, String var2) {
      if (this.warnings.add(var1)) {
         this.getLogger().warning(var2);
      }
   }

   void profileError(Player var1, Exception var2) {
      this.warnOnce("profile-" + var1.getUniqueId(), "Profile/transaction needs review for " + var1.getUniqueId() + ": " + var2.getClass().getSimpleName());
      this.message(var1, "Your profile needs a staff check. No additional purchase will be charged automatically.");
   }

   static String color(String var0) {
      return ChatColor.translateAlternateColorCodes('&', var0);
   }

   void message(CommandSender var1, String var2) {
      var1.sendMessage(color("&d&lTOWNY&a&lREBORN &8» &r&7" + var2));
   }

   void levelReached(Player player, State state, int previousLevel) {
      int level = this.journey.level(state.points);
      if (level <= previousLevel || !this.getConfig().getBoolean("notifications.enabled", true)) return;
      String title = this.journey.styledTitle(level);
      this.message(player, "&aYou reached &eLevel " + level + " &8• &r" + title + "&r&7!");
      long rewards = this.journey.gifts.values().stream()
         .filter(g -> g.level() > previousLevel && g.level() <= level && !state.claimed.contains(g.id())).count();
      long perks = this.journey.perks.values().stream()
         .filter(p -> p.level() > previousLevel && p.level() <= level && !state.owned.contains(p.id())).count();
      this.message(player, "&f" + rewards + " new reward" + (rewards == 1 ? "" : "s") + " &8• &f" + perks
         + " new perk" + (perks == 1 ? "" : "s") + " available&7. Open &a/progression &7to view them.");
      player.sendTitle(color("&d&lJOURNEY &a&lLEVEL UP"), title + color("&r &8• &fLevel " + level), 10, 70, 20);
      player.playSound(player.getLocation(), "minecraft:ui.toast.challenge_complete", 1.0F, 1.0F);
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if (var2.getName().equalsIgnoreCase("rebornadmin")) {
         return this.admin(var1, var4);
      }

      if (var1 instanceof Player var5) {
         if (!this.access(var5)) {
            this.message(var5, "You do not have access to Reborn progression yet.");
            return true;
         }

         try {
            State var6 = this.state(var5);
            this.activate(var5, var6);
            if (var4.length > 0 && var4[0].equalsIgnoreCase("perks")) {
               this.menu.perks(var5, 0, 0);
            } else {
               this.menu.journey(var5, this.journey.page(this.journey.level(var6.points)));
            }
         } catch (Exception var7) {
            this.profileError(var5, var7);
         }

         return true;
      } else {
         this.message(var1, "Use /rebornadmin status <player> from the console.");
         return true;
      }
   }

   boolean admin(CommandSender var1, String[] var2) {
      if (!var1.hasPermission("townyreborn.progression.admin")) {
         this.message(var1, "No permission.");
         return true;
      }

      if (var2.length < 2) {
         this.message(var1, "/rebornadmin open <player> | status <player> | receipt <player> grant/cancel | gift <player> delivered | addpoints <player> <amount>");
         return true;
      }

      Player var3 = Bukkit.getPlayerExact(var2[1]);
      if (var3 == null) {
         this.message(var1, "That player must be online.");
         return true;
      }

      try {
         State var4 = this.state(var3);
         switch (var2[0].toLowerCase(Locale.ROOT)) {
            case "open":
               if (!this.access(var3)) {
                  this.message(var3, "You do not have access to Reborn progression yet.");
                  break;
               }
               this.getServer().getScheduler().runTask(this, () -> {
                  if (!var3.isOnline() || !this.access(var3)) return;
                  try {
                     State target = this.state(var3);
                     this.menu.journey(var3, this.journey.page(this.journey.level(target.points)));
                  } catch (Exception error) {
                     this.profileError(var3, error);
                  }
               });
               break;
            case "status":
               this.message(
                  var1,
                  var3.getName()
                     + ": Level "
                     + this.journey.level(var4.points)
                     + ", "
                     + var4.points
                     + " JP; owned="
                     + var4.owned.size()
                     + "; unlocked-for-purchase="
                     + var4.unlocked.size()
                     + "; receipt="
                     + (var4.pending == null ? "none" : var4.pending.perk() + "/" + var4.pending.phase())
                     + "; pending reward="
                     + var4.pendingGift
               );
               break;
            case "receipt":
               if (var2.length == 3 && Set.of("grant", "cancel").contains(var2[2].toLowerCase(Locale.ROOT))) {
                  this.purchases.resolve(var4, var2[2].equalsIgnoreCase("grant"), var2x -> this.message(var1, var2x));
                  this.getLogger().info("Receipt reviewed by " + var1.getName() + " for " + var4.uuid);
                  break;
               }

               throw new IllegalArgumentException("Use grant or cancel after checking the payment");
            case "gift":
               if (var2.length == 3 && var2[2].equalsIgnoreCase("delivered") && !var4.pendingGift.isEmpty()) {
                  this.gifts.delivered(var4);
                  this.message(var1, "Reward receipt closed. No command was replayed.");
                  break;
               }

               throw new IllegalArgumentException("Use delivered only after verifying the reward");
            case "addpoints":
               if (var2.length == 3 && this.access(var3)) {
                  long var7 = Long.parseLong(var2[2]);
                  if (var7 >= 1L && var7 <= 1000000L) {
                     int previousLevel = this.journey.level(var4.points);
                     var4.points = Math.min(1000000000000L, var4.points + var7);
                     this.store.save(var4);
                     this.levelReached(var3, var4, previousLevel);
                     this.message(var1, "Added " + var7 + " Journey Points.");
                     break;
                  }

                  throw new IllegalArgumentException("Amount must be 1..1000000");
               }

               throw new IllegalArgumentException("The player needs journey access");
            default:
               this.message(var1, "Unknown administrative command.");
         }
      } catch (Exception var9) {
         this.message(var1, "Could not complete that action: " + var9.getMessage());
      }

      return true;
   }
}
