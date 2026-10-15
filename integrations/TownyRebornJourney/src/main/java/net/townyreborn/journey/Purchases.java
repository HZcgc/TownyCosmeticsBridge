package net.townyreborn.journey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class Purchases {
   private final Journey journey;
   private final Store store;
   private final Purchases.Backend backend;
   private final Set<UUID> granting = new HashSet<>();

   public Purchases(Journey var1, Store var2, Purchases.Backend var3) {
      this.journey = var1;
      this.store = var2;
      this.backend = var3;
   }

   public String buy(State var1, Journey.Perk var2, Consumer<String> var3) {
      if (var1.blocked) {
         return "Storage is unavailable. Please contact staff.";
      }

      if (var1.busy()) {
         return "A previous transaction needs to finish before another purchase.";
      }

      if (!this.journey.purchasable(var1, var2)) {
         return "This perk is owned, locked or needs its previous upgrade.";
      }

      double var4 = this.backend.balance(var1.uuid);
      if (Double.isFinite(var4) && !(var4 < var2.price())) {
         var1.pending = new State.Purchase(var2.id(), "PREPARED", var2.price(), var4, System.currentTimeMillis());

         try {
            this.store.save(var1);
         } catch (Exception var11) {
            var1.pending = null;
            var1.blocked = true;
            return "Could not save the purchase. No payment was requested.";
         }

         boolean var6;
         try {
            var6 = this.backend.withdraw(var1.uuid, var2.price());
         } catch (RuntimeException var10) {
            return "Payment needs a staff check. It will not be charged again.";
         }

         if (!var6) {
            var1.pending = null;

            try {
               this.store.save(var1);
            } catch (Exception var8) {
               var1.blocked = true;
            }

            return "Payment was declined. The perk was not unlocked.";
         } else {
            var1.pending = new State.Purchase(var2.id(), "DEBITED", var2.price(), var4, var1.pending.created());

            try {
               this.store.save(var1);
            } catch (Exception var9) {
               var1.pending = new State.Purchase(var2.id(), "PREPARED", var2.price(), var4, var1.pending.created());
               var1.blocked = true;
               return "Payment needs a staff check. It will not be charged again.";
            }

            this.finish(var1, var2, var3);
            return "Processing your purchase...";
         }
      } else {
         return "You need $" + String.format(Locale.US, "%,.0f", var2.price()) + " for this perk.";
      }
   }

   public void recover(State var1, Consumer<String> var2) {
      if (var1.pending != null && var1.pending.phase().equals("DEBITED")) {
         Journey.Perk var3 = this.journey.perks.get(var1.pending.perk());
         if (var3 != null) {
            this.finish(var1, var3, var2);
         }
      }
   }

   public boolean processing(UUID var1) {
      return this.granting.contains(var1);
   }

   private void finish(State var1, Journey.Perk var2, Consumer<String> var3) {
      if (this.granting.add(var1.uuid)) {
         ArrayList var4 = new ArrayList<>(var2.permissions());
         var4.add(Journey.perkMarker(var2.id()));

         try {
            this.backend.grant(var1.uuid, var4).whenComplete((var4x, var5) -> this.backend.main(() -> {
               this.granting.remove(var1.uuid);
               if (var1.pending != null && var1.pending.perk().equals(var2.id()) && var1.pending.phase().equals("DEBITED")) {
                  if (var5 != null) {
                     var3.accept("Your payment is recorded. Staff can finish the perk unlock safely.");
                  } else {
                     State.Purchase paidReceipt = var1.pending;
                     var1.owned.add(var2.id());
                     this.journey.inheritLowerPerks(var1.owned);
                     var1.pending = null;

                     try {
                        this.store.save(var1);
                     } catch (Exception var6x) {
                        var1.pending = paidReceipt;
                        var1.blocked = true;
                        var3.accept("The perk was granted. Its receipt needs a staff check.");
                        return;
                     }
                     // A disconnected UI cannot reopen an already committed purchase.
                     var3.accept("Permanently unlocked " + var2.name() + "!");
                  }
               }
            }));
         } catch (RuntimeException var6) {
            this.granting.remove(var1.uuid);
            var3.accept("The paid receipt is saved. Staff can finish the unlock.");
         }
      }
   }

   public void resolve(State var1, boolean var2, Consumer<String> var3) throws Exception {
      if (this.processing(var1.uuid)) {
         throw new IllegalArgumentException("Permission save is still running; try again after it finishes");
      }

      if (var1.pending == null) {
         throw new IllegalArgumentException("No pending purchase");
      }

      if (var2) {
         Journey.Perk var4 = this.journey.perks.get(var1.pending.perk());
         if (var4 == null) {
            throw new IllegalArgumentException("Unknown receipt perk");
         }

         State.Purchase original = var1.pending;
         var1.pending = new State.Purchase(var4.id(), "DEBITED", original.price(), original.balanceBefore(), original.created());
         try {
            this.store.save(var1);
         } catch (Exception error) {
            var1.pending = original;
            var1.blocked = true;
            throw error;
         }
         this.finish(var1, var4, var3);
      } else {
         State.Purchase var7 = var1.pending;
         var1.pending = null;

         try {
            this.store.save(var1);
         } catch (Exception var6) {
            var1.pending = var7;
            var1.blocked = true;
            throw var6;
         }

         var3.accept("Receipt closed without another debit or a refund.");
      }
   }

   public interface Backend {
      double balance(UUID var1);

      boolean withdraw(UUID var1, double var2);

      CompletableFuture<Void> grant(UUID var1, List<String> var2);

      void main(Runnable var1);
   }
}
