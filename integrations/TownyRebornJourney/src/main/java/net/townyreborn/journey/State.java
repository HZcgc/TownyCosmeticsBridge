package net.townyreborn.journey;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class State {
   public final UUID uuid;
   public String name;
   public volatile long points;
   public volatile long minutes;
   public volatile long journeyMinutes;
   public String progressRevision = "";
   public long journeyStartedAt;
   public String historyCreditRevision = "";
   public long historyCreditPoints;
   public long historyCreditMinutes;
   public long historyCreditAt;
   public String historyCreditSource = "";
   public boolean historyCreditJustApplied;
   public final Set<String> baselineDue = new HashSet<>();
   public final Set<String> owned = new HashSet<>();
   public final Set<String> unlocked = new HashSet<>();
   public boolean legacyPerksImported;
   public final Set<String> claimed = new HashSet<>();
   public final Set<String> achievements = new HashSet<>();
   public final Map<String, Long> high = new HashMap<>();
   public final Map<String, Long> questCompletions = new HashMap<>();
   public final Map<String, Long> daily = new HashMap<>();
   public String day = "";
   public String pendingGift = "";
   public String pendingGiftTown = "";
   public int pendingGiftTownBlocks;
   public double pendingGiftMoney;
   public List<String> pendingGiftCommands = List.of();
   public State.Purchase pending;
   public boolean dirty;
   public boolean persisted;
   public boolean blocked;

   public State(UUID var1, String var2) {
      this.uuid = var1;
      this.name = var2;
   }

   public boolean busy() {
      return this.pending != null || !this.pendingGift.isEmpty();
   }

   public long advanceHighWater(String var1, long var2, long var4) {
      if (var2 >= 0L && var4 > 0L) {
         Long var6 = this.high.get(var1);
         if (this.baselineDue.remove(var1)) {
            this.high.put(var1, Math.max(var6 == null ? 0L : var6, var2));
            this.dirty = true;
            return 0L;
         }
         if (var6 == null) {
            this.high.put(var1, var2);
            this.dirty = true;
            return 0L;
         }

         if (var2 <= var6) {
            return 0L;
         }

         this.high.put(var1, var2);
         this.dirty = true;
         return Math.min(100000L, (var2 - var6) * Math.min(var4, 1000L));
      } else {
         return 0L;
      }
   }

   public long capped(String var1, String var2, long var3, long var5) {
      if (!var1.equals(this.day)) {
         this.day = var1;
         this.daily.clear();
         this.dirty = true;
      }

      if (var3 > 0L && var5 > 0L) {
         long var7 = Math.min(var3, Math.max(0L, var5 - this.daily.getOrDefault(var2, 0L)));
         if (var7 > 0L) {
            this.daily.merge(var2, var7, Long::sum);
            this.points = Math.min(1000000000000L, this.points + var7);
            this.dirty = true;
         }

         return var7;
      } else {
         return 0L;
      }
   }

   public record Purchase(String perk, String phase, double price, double balanceBefore, long created) {
   }
}
