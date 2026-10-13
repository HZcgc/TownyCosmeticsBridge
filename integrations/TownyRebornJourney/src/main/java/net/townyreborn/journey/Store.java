package net.townyreborn.journey;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.Map.Entry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class Store {
   private final Path folder;
   private final Path legacy;
   private final Journey journey;
   private final Map<UUID, State> cache = new HashMap<>();

   public Store(Path var1, Journey var2) throws IOException {
      this.folder = var1.resolve("journeydata");
      this.legacy = var1.resolve("playerdata");
      this.journey = var2;
      Files.createDirectories(this.folder);
   }

   public synchronized State get(UUID var1, String var2) throws Exception {
      State var3 = this.cache.get(var1);
      if (var3 != null) {
         return var3;
      }

      State var4 = new State(var1, var2);
      Path var5 = this.folder.resolve(var1 + ".yml");
      if (Files.exists(var5)) {
         YamlConfiguration var6 = read(var5);
         if (var6.getInt("schema", 0) != 2 || !var1.toString().equals(var6.getString("uuid"))) {
            throw new IOException("Unsupported/corrupt journey profile " + var1);
         }

         var4.points = Math.max(0L, var6.getLong("points"));
         var4.minutes = Math.max(0L, var6.getLong("active-minutes"));
         var4.owned.addAll(var6.getStringList("owned-perks"));
         var4.claimed.addAll(var6.getStringList("claimed-gifts"));
         var4.achievements.addAll(var6.getStringList("achievements"));
         loadNumbers(var6, "high-water", var4.high);
         loadNumbers(var6, "daily-awards", var4.daily);
         var4.day = var6.getString("day", "");
         var4.pendingGift = var6.getString("pending-gift", "");
         var4.pendingGiftTown = var6.getString("pending-gift-town", "");
         var4.pendingGiftCommands = List.copyOf(var6.getStringList("pending-gift-commands"));
         var4.pendingGiftTownBlocks = var6.getInt("pending-gift-town-blocks", 0);
         var4.pendingGiftMoney = var6.getDouble("pending-gift-money", 0);
         if (!Double.isFinite(var4.pendingGiftMoney) || var4.pendingGiftMoney < 0 || var4.pendingGiftMoney > 1000000)
            throw new IOException("Invalid money reward receipt " + var1);
         if (var6.isConfigurationSection("pending-purchase")) {
            var4.pending = new State.Purchase(
               var6.getString("pending-purchase.perk"),
               var6.getString("pending-purchase.phase"),
               var6.getDouble("pending-purchase.price"),
               var6.getDouble("pending-purchase.balance-before"),
               var6.getLong("pending-purchase.created")
            );
            if (var4.pending.perk() == null
               || var4.pending.phase() == null
               || !Set.of("PREPARED", "DEBITED").contains(var4.pending.phase())
               || !Double.isFinite(var4.pending.price())
               || var4.pending.price() <= 0.0) {
               throw new IOException("Invalid purchase receipt " + var1);
            }
         }

         var4.persisted = true;
      } else if (Files.isRegularFile(this.legacy.resolve(var1 + ".yml"))) {
         YamlConfiguration var7 = read(this.legacy.resolve(var1 + ".yml"));
         var4.minutes = Math.max(0L, var7.getLong("active-minutes"));
         var4.points = this.journey.legacyPoints(var7.getInt("level", 1), var4.minutes, var7.getInt("quest-points", 0));
      }

      this.journey.migrateGiftReceipts(var4);
      this.journey.inheritLowerPerks(var4.owned);
      this.cache.put(var1, var4);
      return var4;
   }

   private static YamlConfiguration read(Path var0) throws Exception {
      YamlConfiguration var1 = new YamlConfiguration();
      var1.loadFromString(Files.readString(var0));
      return var1;
   }

   private static void loadNumbers(YamlConfiguration var0, String var1, Map<String, Long> var2) {
      ConfigurationSection var3 = var0.getConfigurationSection(var1);
      if (var3 != null) {
         for (Entry var6 : var3.getValues(false).entrySet()) {
            if (var6.getValue() instanceof Number var4) {
               var2.put((String)var6.getKey(), Math.max(0L, var4.longValue()));
            }
         }
      }
   }

   public synchronized void save(State var1) throws IOException {
      YamlConfiguration var2 = new YamlConfiguration();
      var2.set("schema", 2);
      var2.set("uuid", var1.uuid.toString());
      var2.set("name", var1.name);
      var2.set("points", var1.points);
      var2.set("active-minutes", var1.minutes);
      var2.set("owned-perks", var1.owned.stream().sorted().toList());
      var2.set("claimed-gifts", var1.claimed.stream().sorted().toList());
      var2.set("achievements", var1.achievements.stream().sorted().toList());
      var2.set("high-water", new TreeMap<>(var1.high));
      var2.set("daily-awards", new TreeMap<>(var1.daily));
      var2.set("day", var1.day);
      if (!var1.pendingGift.isEmpty()) {
         var2.set("pending-gift", var1.pendingGift);
         var2.set("pending-gift-town", var1.pendingGiftTown);
         var2.set("pending-gift-commands", var1.pendingGiftCommands);
         var2.set("pending-gift-town-blocks", var1.pendingGiftTownBlocks);
         var2.set("pending-gift-money", var1.pendingGiftMoney);
      }

      if (var1.pending != null) {
         var2.set("pending-purchase.perk", var1.pending.perk());
         var2.set("pending-purchase.phase", var1.pending.phase());
         var2.set("pending-purchase.price", var1.pending.price());
         var2.set("pending-purchase.balance-before", var1.pending.balanceBefore());
         var2.set("pending-purchase.created", var1.pending.created());
      }

      atomic(this.folder.resolve(var1.uuid + ".yml"), var2.saveToString());
      var1.dirty = false;
      var1.persisted = true;
      var1.blocked = false;
   }

   public static void atomic(Path var0, String var1) throws IOException {
      Files.createDirectories(var0.getParent());
      Path var2 = var0.resolveSibling(var0.getFileName() + ".tmp");

      try (FileChannel var3 = FileChannel.open(var2, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
         ByteBuffer var4 = ByteBuffer.wrap(var1.getBytes(StandardCharsets.UTF_8));

         while (var4.hasRemaining()) {
            var3.write(var4);
         }

         var3.force(true);
      }

      try {
         Files.move(var2, var0, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException var8) {
         Files.move(var2, var0, StandardCopyOption.REPLACE_EXISTING);
      }

      try (FileChannel var12 = FileChannel.open(var0.getParent(), StandardOpenOption.READ)) {
         var12.force(true);
      } catch (IOException var10) {
      }
   }

   public synchronized Collection<State> cached() {
      return List.copyOf(this.cache.values());
   }

   public synchronized void unload(State var1) {
      if (!var1.busy()) {
         this.cache.remove(var1.uuid);
      }
   }
}
