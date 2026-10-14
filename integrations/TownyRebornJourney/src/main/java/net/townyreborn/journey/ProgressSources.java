package net.townyreborn.journey;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

final class ProgressSources implements Listener {
   private final RebornPlugin plugin;
   final Map<UUID, ProgressSources.Session> sessions = new HashMap<>();

   ProgressSources(RebornPlugin var1) {
      this.plugin = var1;
   }

   boolean eligible(Player var1) {
      if (this.plugin.access(var1) && !var1.hasMetadata("NPC") && (var1.getGameMode() == GameMode.SURVIVAL || var1.getGameMode() == GameMode.ADVENTURE)) {
         if (this.plugin.getConfig().getStringList("sources.ignored-name-prefixes").stream().anyMatch(var1x -> var1.getName().startsWith(var1x))) {
            return false;
         }

         String var2 = var1.getWorld().getName().toLowerCase(Locale.ROOT);
         return this.plugin.getConfig().getStringList("sources.ignored-worlds").stream().noneMatch(var1x -> var2.equals(var1x.toLowerCase(Locale.ROOT)));
      } else {
         return false;
      }
   }

   void active(Player var1) {
      if (this.eligible(var1)) {
         long var2 = System.currentTimeMillis();
         ProgressSources.Session var4 = this.sessions.get(var1.getUniqueId());
         this.sessions.put(var1.getUniqueId(), new ProgressSources.Session(var2, var4 == null ? var2 : var4.minute()));
      }
   }

   void award(Player var1, String var2, long var3, long var5) {
      if (this.eligible(var1) && var3 > 0L) {
         try {
            State var7 = this.plugin.state(var1);
            if (var7.blocked) {
               return;
            }

            int var8 = this.plugin.journey.level(var7.points);
            var7.capped(DailyTime.today(), var2, var3, var5);
            int var9 = this.plugin.journey.level(var7.points);
            if (var9 > var8) {
               this.plugin.store.save(var7);
               this.plugin.levelReached(var1, var7, var8);
            }
         } catch (Exception var10) {
            this.plugin.profileError(var1, var10);
         }
      }
   }

   long number(Player var1, String var2) {
      String var3 = ChatColor.stripColor(PlaceholderAPI.setPlaceholders(var1, var2)).replace(",", "").trim();

      try {
         return Long.parseLong(var3);
      } catch (NumberFormatException var5) {
         return -1L;
      }
   }

   void poll() {
      long var1 = System.currentTimeMillis();

      for (Player var4 : this.plugin.getServer().getOnlinePlayers()) {
         if (!this.eligible(var4)) {
            this.plugin.menu.refresh(var4);
         } else {
            try {
               State var5 = this.plugin.state(var4);
               this.plugin.activate(var4, var5);
               if (!var5.blocked) {
                  ProgressSources.Session var6 = this.sessions.get(var4.getUniqueId());
                  if (var6 != null) {
                     if (var1 - var6.active() <= this.plugin.getConfig().getLong("sources.active-timeout-seconds", 90L) * 1000L) {
                        long var7 = Math.min(1L, Math.max(0L, (var1 - var6.minute()) / 60000L));
                        if (var7 > 0L) {
                           var5.minutes += var7;
                           var5.journeyMinutes += var7;
                           var5.dirty = true;
                           if (this.plugin.getConfig().getBoolean("sources.playtime.enabled", true))
                              this.award(var4, "playtime", var7 * this.plugin.getConfig().getLong("sources.playtime.points-per-minute", 1L),
                                 this.plugin.getConfig().getLong("sources.playtime.daily-cap", 360L));
                           this.sessions.put(var4.getUniqueId(), new ProgressSources.Session(var6.active(), var1));
                        }
                     } else {
                        this.sessions.put(var4.getUniqueId(), new ProgressSources.Session(var6.active(), var1));
                     }
                  }

                  if (this.plugin.getConfig().getBoolean("sources.mcmmo.enabled", true)) {
                     long var12 = var5.advanceHighWater(
                        "mcmmo", this.number(var4, "%mcmmo_power_level%"), this.plugin.getConfig().getLong("sources.mcmmo.points-per-level", 5L)
                     );
                     this.award(var4, "mcmmo", var12, this.plugin.getConfig().getLong("sources.mcmmo.daily-cap", 500L));
                  }

                  if (this.plugin.getConfig().getBoolean("sources.jobs.enabled", true)) {
                     for (Entry var8 : this.plugin.integrations.jobsLevels(var4).entrySet()) {
                        long var9 = var5.advanceHighWater(
                           (String)var8.getKey(), (Long)var8.getValue(), this.plugin.getConfig().getLong("sources.jobs.points-per-level", 12L)
                        );
                        this.award(var4, "jobs", var9, this.plugin.getConfig().getLong("sources.jobs.daily-cap", 500L));
                     }
                  }

                  // Quests completion events award each actual run, including repeatable quests.
                  // The display counter falls when a quest is restarted and must not award JP.

                  if (this.plugin.getConfig().getBoolean("sources.exploration.enabled", true)) {
                     String var15 = "biome:" + var4.getLocation().getBlock().getBiome().getKey();
                     if (var5.achievements.add(var15)) {
                        var5.dirty = true;
                        this.award(
                           var4,
                           "exploration",
                           this.plugin.getConfig().getLong("sources.exploration.points-per-biome", 8L),
                           this.plugin.getConfig().getLong("sources.exploration.daily-cap", 200L)
                        );
                     }
                  }

                  if (var5.dirty) {
                     this.plugin.store.save(var5);
                  }

                  this.plugin.menu.refresh(var4);
               }
            } catch (Exception var11) {
               this.plugin.profileError(var4, var11);
            }
         }
      }
   }

   @EventHandler
   public void join(PlayerJoinEvent var1) {
      Player var2 = var1.getPlayer();
      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
         if (var2.isOnline()) {
            try {
               State var2x = this.plugin.state(var2);
               if (this.plugin.access(var2)) {
                  this.plugin.activate(var2, var2x);
                  this.active(var2);
               }

               if (var2x.pending != null && var2x.pending.phase().equals("DEBITED")) {
                  this.plugin.purchases.recover(var2x, var2xx -> this.plugin.message(var2, var2xx));
               }
            } catch (Exception var3) {
               this.plugin.profileError(var2, var3);
            }
         }
      }, 20L);
   }

   @EventHandler
   public void quit(PlayerQuitEvent var1) {
      this.sessions.remove(var1.getPlayer().getUniqueId());
      this.plugin.deactivate(var1.getPlayer().getUniqueId());

      try {
         State var2 = this.plugin.state(var1.getPlayer());
         if (var2.persisted && (var2.dirty || var2.busy())) {
            this.plugin.store.save(var2);
         }

         this.plugin.store.unload(var2);
      } catch (Exception var3) {
         this.plugin.profileError(var1.getPlayer(), var3);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void move(PlayerMoveEvent var1) {
      if (var1.hasChangedBlock()) {
         this.active(var1.getPlayer());
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void interact(PlayerInteractEvent var1) {
      this.active(var1.getPlayer());
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void blockBreak(BlockBreakEvent var1) {
      this.active(var1.getPlayer());
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void blockPlace(BlockPlaceEvent var1) {
      this.active(var1.getPlayer());
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void damage(EntityDamageByEntityEvent var1) {
      if (var1.getDamager() instanceof Player var2) {
         this.active(var2);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void fish(PlayerFishEvent var1) {
      if (var1.getState() == org.bukkit.event.player.PlayerFishEvent.State.CAUGHT_FISH && this.plugin.getConfig().getBoolean("sources.fishing.enabled", true)) {
         this.active(var1.getPlayer());
         this.award(
            var1.getPlayer(),
            "fishing",
            this.plugin.getConfig().getLong("sources.fishing.points-per-catch", 3L),
            this.plugin.getConfig().getLong("sources.fishing.daily-cap", 300L)
         );
      }
   }

   @EventHandler
   public void advancement(PlayerAdvancementDoneEvent var1) {
      if (this.eligible(var1.getPlayer())
         && this.plugin.getConfig().getBoolean("sources.advancements.enabled", true)
         && !var1.getAdvancement().getKey().getKey().startsWith("recipes/")) {
         try {
            State var2 = this.plugin.state(var1.getPlayer());
            String var3 = "advancement:" + var1.getAdvancement().getKey();
            if (var2.achievements.add(var3)) {
               var2.dirty = true;
               this.award(
                  var1.getPlayer(),
                  "advancements",
                  this.plugin.getConfig().getLong("sources.advancements.points-per-advancement", 15L),
                  this.plugin.getConfig().getLong("sources.advancements.daily-cap", 300L)
               );
            }
         } catch (Exception var4) {
            this.plugin.profileError(var1.getPlayer(), var4);
         }
      }
   }

   record Session(long active, long minute) {
   }
}
