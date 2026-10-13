package net.townyreborn.journey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import org.bukkit.configuration.ConfigurationSection;

public final class Journey {
   public static final int[] PATH = new int[]{
      9, 18, 27, 36, 45, 46, 47, 38, 29, 20, 11, 12, 13, 22, 31, 40, 49, 50, 51, 42, 33, 24, 15, 16, 17, 26, 35, 44, 53
   };
   public final int steps;
   public final long base;
   public final long increase;
   public final Map<String, Journey.Perk> perks = new LinkedHashMap<>();
   public final Map<String, Journey.Gift> gifts = new LinkedHashMap<>();
   public final NavigableMap<Integer, String> titles = new TreeMap<>();

   public Journey(ConfigurationSection var1) {
      this.steps = var1.getInt("journey.steps", 116);
      this.base = var1.getLong("journey.points-first", 30L);
      this.increase = var1.getLong("journey.points-increase", 15L);
      if (this.steps >= 2
         && this.steps <= 10000
         && this.base >= 1L
         && this.base <= 1000000L
         && this.increase >= 0L
         && this.increase <= 100000L
         && this.threshold(this.steps) <= 1000000000000L) {
         ConfigurationSection var2 = var1.getConfigurationSection("journey.titles");
         if (var2 != null) {
            for (String var4 : var2.getKeys(false)) {
               int var5 = Integer.parseInt(var4);
               String var6 = var2.getString(var4);
               if (var5 < 1 || var5 > this.steps || var6 == null || var6.isBlank()) {
                  throw new IllegalArgumentException("Invalid rank title " + var4);
               }

               this.titles.put(var5, var6);
            }
         }

         this.titles.putIfAbsent(1, "Newcomer");
         ConfigurationSection var10 = var1.getConfigurationSection("perks");
         if (var10 != null) {
            for (String var16 : var10.getKeys(false)) {
               validateId(var16);
               ConfigurationSection var22 = var10.getConfigurationSection(var16);
               int var7 = var22.getInt("level");
               double var8 = var22.getDouble("price");
               if (var7 < 1 || var7 > this.steps || !Double.isFinite(var8) || var8 <= 0.0) {
                  throw new IllegalArgumentException("Invalid perk " + var16);
               }

               if (var22.getStringList("permissions").isEmpty()) {
                  throw new IllegalArgumentException("Perk has no permissions: " + var16);
               }

               this.perks
                  .put(
                     var16,
                     new Journey.Perk(
                        var16,
                        var22.getString("name", var16),
                        var7,
                        var8,
                        List.copyOf(var22.getStringList("permissions")),
                        List.copyOf(var22.getStringList("legacy-markers")),
                        var22.getString("requires", ""),
                        List.copyOf(var22.getStringList("help-commands"))
                     )
                  );
            }
         }

         for (Journey.Perk var17 : this.perks.values()) {
            if (!var17.requires().isEmpty() && !this.perks.containsKey(var17.requires())) {
               throw new IllegalArgumentException("Unknown predecessor for " + var17.id());
            }
         }

         for (Journey.Perk var18 : this.perks.values()) {
            if (!var18.requires().isEmpty() && this.perks.get(var18.requires()).level() > var18.level()) {
               throw new IllegalArgumentException("Predecessor unlocks too late for " + var18.id());
            }
         }

         for (Journey.Perk var19 : this.perks.values()) {
            HashSet var23 = new HashSet();

            for (Journey.Perk var26 = var19; var26 != null && !var26.requires().isEmpty(); var26 = this.perks.get(var26.requires())) {
               if (!var23.add(var26.id())) {
                  throw new IllegalArgumentException("Cyclic perk chain");
               }
            }
         }

         ConfigurationSection var15 = var1.getConfigurationSection("gifts");
         if (var15 != null) {
            for (String var24 : var15.getKeys(false)) {
               validateId(var24);
               ConfigurationSection var27 = var15.getConfigurationSection(var24);
               int var29 = var27.getInt("level");
               int var9 = var27.getInt("town-blocks", 0);
               if (var29 < 1 || var29 > this.steps || var9 < 0 || var9 > 100) {
                  throw new IllegalArgumentException("Invalid gift " + var24);
               }

               this.gifts
                  .put(
                     var24,
                     new Journey.Gift(
                        var24,
                        var27.getString("name", var24),
                        var29,
                        List.copyOf(var27.getStringList("permissions")),
                        List.copyOf(var27.getStringList("commands")),
                        var27.getBoolean("town-required", false) || var9 > 0,
                        var9
                     )
                  );
            }
         }

         for (int var21 = 1; var21 <= this.steps; var21++) {
            int var25 = var21;
            long var28 = this.perks.values().stream().filter(var1x -> var1x.level() == var25).count()
               + this.gifts.values().stream().filter(var1x -> var1x.level() == var25).count();
            if (var28 > 28L) {
               throw new IllegalArgumentException("Too many rewards at level " + var21);
            }
         }
      } else {
         throw new IllegalArgumentException("Invalid progression curve");
      }
   }

   private static void validateId(String var0) {
      if (!var0.matches("[a-z0-9_]+")) {
         throw new IllegalArgumentException("Invalid stable ID: " + var0);
      }
   }

   public long threshold(int var1) {
      long var2 = Math.max(0L, Math.min(this.steps, var1) - 1L);
      return this.base * var2 + this.increase * var2 * (var2 - 1L) / 2L;
   }

   public int level(long var1) {
      int var3 = 1;
      int var4 = this.steps;

      while (var3 < var4) {
         int var5 = (var3 + var4 + 1) / 2;
         if (this.threshold(var5) <= var1) {
            var3 = var5;
         } else {
            var4 = var5 - 1;
         }
      }

      return var3;
   }

   public String title(int var1) {
      Entry var2 = this.titles.floorEntry(var1);
      return var2 == null ? "Newcomer" : (String)var2.getValue();
   }

   public int pages() {
      return (this.steps + PATH.length - 1) / PATH.length;
   }

   public int page(int var1) {
      return (Math.max(1, var1) - 1) / PATH.length;
   }

   public int percent(long var1) {
      int var3 = this.level(var1);
      return var3 >= this.steps
         ? 100
         : (int)Math.max(0L, Math.min(100L, (var1 - this.threshold(var3)) * 100L / (this.threshold(var3 + 1) - this.threshold(var3))));
   }

   public long legacyPoints(int var1, long var2, int var4) {
      int var5 = var1 <= 1 ? 1 : Math.min(this.steps, (var1 - 1) * 12);
      return Math.max(this.threshold(var5), Math.max(0L, var2) + Math.max(0L, var4) * 25L);
   }

   public void inheritLowerPerks(Set<String> var1) {
      for (String var3 : new ArrayList<>(var1)) {
         for (Journey.Perk var4 = this.perks.get(var3); var4 != null && !var4.requires().isEmpty(); var4 = this.perks.get(var4.requires())) {
            var1.add(var4.requires());
         }
      }
   }

   public boolean purchasable(State var1, Journey.Perk var2) {
      return var2 != null
         && this.level(var1.points) >= var2.level()
         && !var1.owned.contains(var2.id())
         && (var2.requires().isEmpty() || var1.owned.contains(var2.requires()));
   }

   public static String perkMarker(String var0) {
      return "townyreborn.journey.perk." + var0;
   }

   public static String giftMarker(String var0) {
      return "townyreborn.journey.reward." + var0;
   }

   public record Gift(String id, String name, int level, List<String> permissions, List<String> commands, boolean townRequired, int townBlocks) {
   }

   public record Perk(String id, String name, int level, double price, List<String> permissions, List<String> legacy, String requires, List<String> commands) {
   }
}
