package net.townyreborn.journey;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

final class LegacyCleanup {
   static void run(RebornPlugin var0) throws Exception {
      Path var1 = var0.getDataFolder().toPath().resolve("standalone-cleanup-v2.done");
      if (!Files.exists(var1)) {
         Path var2 = var0.getDataFolder().toPath().resolve("legacy-backup").resolve(Long.toString(Instant.now().toEpochMilli()));
         ArrayList<LegacyCleanup.Change> var3 = new ArrayList<>();
         ArrayList<LegacyCleanup.Move> var4 = new ArrayList<>();
         Plugin var5 = var0.getServer().getPluginManager().getPlugin("Quests");
         if (var5 != null) {
            Path var6 = var5.getDataFolder().toPath();
            Path var7 = var6.resolve("categories.yml");
            if (Files.isRegularFile(var7)) {
               YamlConfiguration var8 = load(var7);
               if (var8.contains("categories.reborn")) {
                  var8.set("categories.reborn", null);
                  var3.add(new LegacyCleanup.Change(var7, var8.saveToString(), var2.resolve("Quests/categories.yml")));
               }
            }

            Path var24 = var6.resolve("config.yml");
            if (Files.isRegularFile(var24)) {
               YamlConfiguration var9 = load(var24);
               ConfigurationSection var10 = var9.getConfigurationSection("custom-elements.categories.49");
               if (var10 != null
                  && (
                     var10.getStringList("commands").stream().anyMatch(var0x -> var0x.contains("reborn"))
                        || var10.getStringList("display.lore")
                           .stream()
                           .anyMatch(var0x -> var0x.contains("trprogress") || var0x.contains("rank path") || var0x.contains("progression is being rebuilt"))
                  )) {
                  var10.set("commands", null);
                  var10.set("display.name", "&d&lTOWNY &a&lREBORN &8• &fQuest Log");
                  var10.set(
                     "display.lore",
                     List.of("&7Complete quests to earn rewards.", "", "&7Completed: &a%quests_completed%&8/&f%quests_all%", "&7Active: &e%quests_started%")
                  );
                  var3.add(new LegacyCleanup.Change(var24, var9.saveToString(), var2.resolve("Quests/config.yml")));
               }
            }

            Path var29 = var6.resolve("quests/reborn");
            if (Files.exists(var29)) {
               var4.add(new LegacyCleanup.Move(var29, var2.resolve("Quests/quests/reborn")));
            }
         }

         Plugin var20 = var0.getServer().getPluginManager().getPlugin("DeluxeMenus");
         if (var20 != null) {
            Path var21 = var20.getDataFolder().toPath();
            Path var25 = var21.resolve("config.yml");
            if (Files.isRegularFile(var25)) {
               YamlConfiguration var30 = load(var25);
               ConfigurationSection var36 = var30.getConfigurationSection("gui_menus");
               boolean var11 = false;
               if (var36 != null) {
                  for (String var13 : new HashSet<>(var36.getKeys(false))) {
                     String var14 = var36.getString(var13 + ".file", "");
                     if (var13.startsWith("reborn_") || Path.of(var14).getFileName().toString().startsWith("reborn_")) {
                        var36.set(var13, null);
                        var11 = true;
                     }
                  }
               }

               if (var11) {
                  var3.add(new LegacyCleanup.Change(var25, var30.saveToString(), var2.resolve("DeluxeMenus/config.yml")));
               }
            }

            Path var31 = var21.resolve("gui_menus");
            if (Files.isDirectory(var31)) {
               try (Stream<Path> var37 = Files.walk(var31)) {
                  var37.filter(var0x -> Files.isRegularFile(var0x))
                     .filter(var0x -> var0x.getFileName().toString().startsWith("reborn_") && var0x.toString().endsWith(".yml"))
                     .forEach(var3x -> var4.add(new LegacyCleanup.Move(var3x, var2.resolve("DeluxeMenus").resolve(var21.relativize(var3x)))));
               }
            }
         }

         for (LegacyCleanup.Change var26 : var3) {
            Files.createDirectories(var26.backup().getParent());
            Files.copy(var26.file(), var26.backup());
         }

         ArrayList<LegacyCleanup.Move> var23 = new ArrayList<>();

         try {
            for (LegacyCleanup.Move var34 : var4) {
               Files.createDirectories(var34.to().getParent());
               Files.move(var34.from(), var34.to());
               var23.add(var34);
            }

            for (LegacyCleanup.Change var35 : var3) {
               Store.atomic(var35.file(), var35.replacement());
            }

            Store.atomic(var1, "Old Reborn quests and menus archived; existing playerdata unchanged.\nBackup: " + var2 + "\n");
         } catch (Exception var19) {
            for (LegacyCleanup.Change var38 : var3) {
               try {
                  Files.copy(var38.backup(), var38.file(), StandardCopyOption.REPLACE_EXISTING);
               } catch (Exception var16) {
               }
            }

            Collections.reverse(var23);

            for (LegacyCleanup.Move var39 : var23) {
               try {
                  Files.move(var39.to(), var39.from());
               } catch (Exception var15) {
               }
            }

            throw var19;
         }

         var0.getLogger()
            .info("Standalone cleanup: " + var3.size() + " config edits, " + var4.size() + " legacy paths archived. Player files were not changed.");
      }
   }

   static YamlConfiguration load(Path var0) throws Exception {
      YamlConfiguration var1 = new YamlConfiguration();
      var1.loadFromString(Files.readString(var0));
      return var1;
   }

   record Change(Path file, String replacement, Path backup) {
   }

   record Move(Path from, Path to) {
   }
}
