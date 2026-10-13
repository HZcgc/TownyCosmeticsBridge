package net.townyreborn.journey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class GiftClaims {
   private final Journey journey;
   private final Store store;
   private final GiftClaims.Backend backend;
   private final Set<UUID> processing = new HashSet<>();

   public GiftClaims(Journey var1, Store var2, GiftClaims.Backend var3) {
      this.journey = var1;
      this.store = var2;
      this.backend = var3;
   }

   public boolean processing(UUID var1) {
      return this.processing.contains(var1);
   }

   public String claim(State var1, Journey.Gift var2, String var3, Consumer<String> var4) throws Exception {
      if (!var1.blocked && !var1.busy()) {
         if (var2 == null || this.journey.level(var1.points) < var2.level() || var1.claimed.contains(var2.id())) {
            return "This reward is claimed or still locked.";
         }

         if (var2.townRequired() && var3.isBlank()) {
            return "Join a town first; you can claim this reward later.";
         }

         if (!var1.name.matches("[A-Za-z0-9_.-]{1,32}")) {
            return "Your player name needs a staff check before claiming this reward.";
         }

         List<String> var5 = var2.commands().stream().map(var1x -> var1x.replace("%player%", var1.name).replace("%uuid%", var1.uuid.toString())).toList();
         var1.pendingGift = var2.id();
         var1.pendingGiftTown = var3;
         var1.pendingGiftCommands = var5;
         var1.pendingGiftTownBlocks = var2.townBlocks();

         try {
            this.store.save(var1);
         } catch (Exception var9) {
            clear(var1);
            var1.blocked = true;
            throw var9;
         }

         ArrayList var6 = new ArrayList<>(var2.permissions());
         var6.add(Journey.giftMarker(var2.id()));
         this.processing.add(var1.uuid);

         try {
            this.backend.grant(var1.uuid, var6).whenComplete((var6x, var7) -> this.backend.main(() -> {
               this.processing.remove(var1.uuid);
               if (var1.pendingGift.equals(var2.id())) {
                  if (var7 != null) {
                     var4.accept("This reward needs a staff check. It will not be claimed twice.");
                  } else {
                     try {
                        for (String var8x : var5) {
                           if (!this.backend.dispatch(var8x)) {
                              throw new IllegalStateException("Reward command did not confirm completion");
                           }
                        }

                        if (var2.townBlocks() > 0) {
                           this.backend.townBonus(var3, var2.townBlocks());
                        }

                        var1.claimed.add(var2.id());
                        clear(var1);
                        this.store.save(var1);
                        var4.accept("Claimed " + var2.name() + "!");
                     } catch (Exception var9x) {
                        var1.pendingGift = var2.id();
                        var1.pendingGiftTown = var3;
                        var1.pendingGiftCommands = var5;
                        var1.pendingGiftTownBlocks = var2.townBlocks();
                        var1.claimed.remove(var2.id());
                        var1.dirty = true;
                        var4.accept("This reward needs a staff check. It will not be claimed twice.");
                     }
                  }
               }
            }));
            return "Claiming your free reward...";
         } catch (RuntimeException var8) {
            this.processing.remove(var1.uuid);
            return "This reward needs a staff check. It will not be claimed twice.";
         }
      } else {
         return "A previous transaction needs to finish first.";
      }
   }

   public void delivered(State var1) throws Exception {
      if (this.processing(var1.uuid)) {
         throw new IllegalArgumentException("Reward save is still running; try again after it finishes");
      }

      if (var1.pendingGift.isEmpty()) {
         throw new IllegalArgumentException("No pending reward");
      }

      String var2 = var1.pendingGift;
      String var3 = var1.pendingGiftTown;
      List var4 = var1.pendingGiftCommands;
      int var5 = var1.pendingGiftTownBlocks;
      var1.claimed.add(var2);
      clear(var1);

      try {
         this.store.save(var1);
      } catch (Exception var7) {
         var1.claimed.remove(var2);
         var1.pendingGift = var2;
         var1.pendingGiftTown = var3;
         var1.pendingGiftCommands = var4;
         var1.pendingGiftTownBlocks = var5;
         var1.blocked = true;
         throw var7;
      }
   }

   private static void clear(State var0) {
      var0.pendingGift = "";
      var0.pendingGiftTown = "";
      var0.pendingGiftCommands = List.of();
      var0.pendingGiftTownBlocks = 0;
   }

   public interface Backend {
      CompletableFuture<Void> grant(UUID var1, List<String> var2);

      boolean dispatch(String var1);

      void townBonus(String var1, int var2) throws Exception;

      void main(Runnable var1);
   }
}
