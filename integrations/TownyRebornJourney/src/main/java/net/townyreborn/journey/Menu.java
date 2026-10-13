package net.townyreborn.journey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

final class Menu implements Listener {
   private final RebornPlugin plugin;
   private boolean refreshing;

   Menu(RebornPlugin var1) {
      this.plugin = var1;
   }

   ItemStack item(Material var1, String var2, List<String> var3) {
      ItemStack var4 = new ItemStack(var1);
      ItemMeta var5 = var4.getItemMeta();
      var5.setDisplayName(RebornPlugin.color(var2));
      var5.setLore(var3.stream().map(RebornPlugin::color).toList());
      var4.setItemMeta(var5);
      return var4;
   }

   Menu.View create(String var1, int var2, int var3, String var4, String var5) {
      Menu.View var6 = new Menu.View(var1, var2, var3, var4);
      var6.inventory = Bukkit.createInventory(var6, 54, RebornPlugin.color(var5));
      ItemStack var7 = this.item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());

      for (int var8 = 0; var8 < 54; var8++) {
         var6.inventory.setItem(var8, var7);
      }

      return var6;
   }

   void button(Menu.View var1, int var2, Material var3, String var4, List<String> var5, Menu.Action var6) {
      var1.inventory.setItem(var2, this.item(var3, var4, var5));
      if (var6 != null) {
         var1.actions.put(var2, var6);
      }
   }

   void show(Player var1, Menu.View var2) {
      if (var1.isOnline() && this.plugin.access(var1)) {
         if (this.refreshing && var1.getOpenInventory().getTopInventory().getHolder() instanceof Menu.View var3) {
            var3.actions.clear();
            var3.actions.putAll(var2.actions);
            var3.inventory.setContents(var2.inventory.getContents());
         } else {
            var1.openInventory(var2.inventory);
         }
      }
   }

   void journey(Player var1, int var2) throws Exception {
      if (this.plugin.access(var1)) {
         State var3 = this.plugin.state(var1);
         this.plugin.activate(var1, var3);
         int var4 = this.plugin.journey.level(var3.points);
         var2 = Math.max(0, Math.min(this.plugin.journey.pages() - 1, var2));
         Menu.View var5 = this.create("journey", var2, 0, "", "&d&lTOWNY&a&lREBORN &8| &fJourney " + (var2 + 1) + "/" + this.plugin.journey.pages());
         if (var2 > 0) {
            this.button(var5, 0, Material.ARROW, "&dPrevious page", List.of(), new Menu.Action("journey", "", var2 - 1, 0));
         }

         if (var2 + 1 < this.plugin.journey.pages()) {
            this.button(var5, 8, Material.ARROW, "&aNext page", List.of(), new Menu.Action("journey", "", var2 + 1, 0));
         }

         this.button(
            var5,
            2,
            Material.EXPERIENCE_BOTTLE,
            "&dYour progress",
            List.of(
               "&7Level: &a" + var4,
               "&7Title: &f" + this.plugin.journey.title(var4),
               "&7Journey Points: &d" + var3.points,
               "&7Next level: &f" + this.plugin.journey.percent(var3.points) + "%",
               "",
               "&7Earn points through active play, mcMMO,",
               "&7Jobs, fishing, exploration and advancements.",
               "&7Perk purchases never block your next level."
            ),
            null
         );
         ItemStack var6 = this.item(
            Material.PLAYER_HEAD,
            "&f" + var1.getName(),
            List.of("&d" + this.plugin.journey.title(var4), "&7Active time: &f" + String.format(Locale.US, "%.1fh", var3.minutes / 60.0))
         );
         if (var6.getItemMeta() instanceof SkullMeta var7) {
            var7.setOwningPlayer(var1);
            var6.setItemMeta(var7);
         }

         var5.inventory.setItem(4, var6);
         this.button(
            var5,
            6,
            Material.ENDER_CHEST,
            "&aPermanent perks",
            List.of("&7Buy unlocked supporter features with game money.", "&7Return to buy them whenever you are ready."),
            new Menu.Action("perks", "", 0, 0)
         );

         for (int var12 = 0; var12 < Journey.PATH.length; var12++) {
            int var13 = var2 * Journey.PATH.length + var12 + 1;
            if (var13 > this.plugin.journey.steps) {
               break;
            }

            ArrayList var9 = new ArrayList();
            var9.add("&7Journey Points: &d" + Math.min(var3.points, this.plugin.journey.threshold(var13)) + "&8/&f" + this.plugin.journey.threshold(var13));
            var9.add("&7Rank: &f" + this.plugin.journey.title(var13));
            this.plugin
               .journey
               .perks
               .values()
               .stream()
               .filter(var1x -> var1x.level() == var13)
               .forEach(var2x -> var9.add((var3.owned.contains(var2x.id()) ? "&a✓ " : "&dPerk: &f") + var2x.name() + " &8($" + money(var2x.price()) + ")"));
            this.plugin
               .journey
               .gifts
               .values()
               .stream()
               .filter(var1x -> var1x.level() == var13)
               .forEach(var2x -> var9.add((var3.claimed.contains(var2x.id()) ? "&a✓ " : "&aFree reward: &f") + var2x.name()));
            var9.add("");
            var9.add(var13 <= var4 ? "&aClick to view this milestone." : "&7Keep earning points to reach this milestone.");
            Material var10 = var13 <= var4
               ? Material.LIME_STAINED_GLASS_PANE
               : (var13 == var4 + 1 ? Material.YELLOW_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE);
            this.button(var5, Journey.PATH[var12], var10, (var13 <= var4 ? "&a" : "&c") + "Level " + var13, var9, new Menu.Action("step", "", 0, var13));
         }

         this.show(var1, var5);
      }
   }

   void perks(Player var1, int var2, int var3) throws Exception {
      if (var1.isOnline() && this.plugin.access(var1)) {
         State var4 = this.plugin.state(var1);
         List var5 = this.plugin.journey.perks.values().stream().filter(var1x -> var3 == 0 || var1x.level() == var3).toList();
         int var6 = Math.max(1, (var5.size() + 35) / 36);
         var2 = Math.max(0, Math.min(var6 - 1, var2));
         Menu.View var7 = this.create("perks", var2, var3, "", "&d&lREBORN &8| &fPermanent perks");

         for (int var8 = 0; var8 < 36; var8++) {
            int var9 = var2 * 36 + var8;
            if (var9 >= var5.size()) {
               break;
            }

            Journey.Perk var10 = (Journey.Perk)var5.get(var9);
            boolean var11 = var4.owned.contains(var10.id());
            boolean var12 = this.plugin.journey.level(var4.points) >= var10.level();
            ArrayList var13 = new ArrayList<>(
               List.of("&7Available at Level &d" + var10.level(), "&7Price: &a$" + money(var10.price()), "&7Unlock: &fPermanent")
            );
            if (!var10.requires().isEmpty()) {
               var13.add("&7Previous upgrade: &f" + this.plugin.journey.perks.get(var10.requires()).name());
            }

            for (String var15 : var10.commands()) {
               var13.add("&7Use: &f" + var15);
            }

            var13.add("");
            var13.add(var11 ? "&aAlready permanently unlocked." : (var12 ? "&aClick to review the purchase." : "&cReach the required level first."));
            this.button(
               var7,
               9 + var8,
               var11 ? Material.LIME_DYE : (var12 ? Material.CHEST : Material.RED_DYE),
               var11 ? "&a" + var10.name() : "&f" + var10.name(),
               var13,
               new Menu.Action("confirm", var10.id(), 0, var3)
            );
         }

         this.button(
            var7,
            4,
            Material.GOLD_INGOT,
            "&a$" + money(this.plugin.integrations.balance(var1.getUniqueId())),
            List.of("&7Purchases are optional.", "&7Your Journey Points are never spent."),
            null
         );
         if (var2 > 0) {
            this.button(var7, 48, Material.ARROW, "&dPrevious", List.of(), new Menu.Action("perks", "", var2 - 1, var3));
         }

         if (var2 + 1 < var6) {
            this.button(var7, 50, Material.ARROW, "&aNext", List.of(), new Menu.Action("perks", "", var2 + 1, var3));
         }

         this.button(
            var7,
            45,
            Material.ARROW,
            "&dBack to journey",
            List.of(),
            new Menu.Action("journey", "", this.plugin.journey.page(this.plugin.journey.level(var4.points)), 0)
         );
         this.show(var1, var7);
      }
   }

   void step(Player var1, int var2) throws Exception {
      if (var1.isOnline() && this.plugin.access(var1)) {
         State var3 = this.plugin.state(var1);
         Menu.View var4 = this.create("step", 0, var2, "", "&d&lREBORN &8| &fLevel " + var2);
         this.button(
            var4,
            4,
            Material.NETHER_STAR,
            "&d" + this.plugin.journey.title(var2),
            List.of(
               "&7Required points: &f" + this.plugin.journey.threshold(var2),
               "&7Your points: &a" + var3.points,
               "&7Status: " + (this.plugin.journey.level(var3.points) >= var2 ? "&aReached" : "&cLocked")
            ),
            null
         );
         int var5 = 10;

         for (Journey.Perk var7 : this.plugin.journey.perks.values()) {
            if (var7.level() == var2) {
               this.button(
                  var4,
                  var5++,
                  Material.CHEST,
                  "&f" + var7.name(),
                  List.of(
                     "&7Optional permanent unlock",
                     "&7Price: &a$" + money(var7.price()),
                     var3.owned.contains(var7.id()) ? "&aAlready owned" : "&aClick to review"
                  ),
                  new Menu.Action("confirm", var7.id(), 0, var2)
               );
            }
         }

         for (Journey.Gift var9 : this.plugin.journey.gifts.values()) {
            if (var9.level() == var2) {
               this.button(
                  var4,
                  var5++,
                  Material.EXPERIENCE_BOTTLE,
                  "&a" + var9.name(),
                  List.of(
                     "&aFree milestone reward",
                     "&7No money requirement.",
                     var9.townRequired() ? "&7Join a town to claim; you can claim later." : "&7Claim once when you reach this level.",
                     var3.claimed.contains(var9.id()) ? "&aAlready claimed" : "&aClick to claim"
                  ),
                  new Menu.Action("gift", var9.id(), 0, var2)
               );
            }
         }

         if (var5 == 10) {
            this.button(
               var4,
               22,
               Material.NAME_TAG,
               "&dYour next milestone",
               List.of("&7Every level contributes to your rank journey.", "&7Browse the next nodes for rewards and perks."),
               null
            );
         }

         this.button(var4, 45, Material.ARROW, "&dBack", List.of(), new Menu.Action("journey", "", this.plugin.journey.page(var2), 0));
         this.show(var1, var4);
      }
   }

   void confirm(Player var1, String var2) throws Exception {
      if (var1.isOnline() && this.plugin.access(var1)) {
         State var3 = this.plugin.state(var1);
         Journey.Perk var4 = this.plugin.journey.perks.get(var2);
         if (var4 != null) {
            Menu.View var5 = this.create("confirm", 0, var4.level(), var2, "&d&lREBORN &8| &fConfirm purchase");
            this.button(
               var5,
               13,
               Material.CHEST,
               "&f" + var4.name(),
               List.of(
                  "&7Price: &a$" + money(var4.price()),
                  "&7Balance: &f$" + money(this.plugin.integrations.balance(var1.getUniqueId())),
                  "&7Permanent access to this feature.",
                  "&7Your level and points are unchanged."
               ),
               null
            );
            this.button(var5, 30, Material.RED_CONCRETE, "&cCancel", List.of(), new Menu.Action("step", "", 0, var4.level()));
            this.button(
               var5,
               32,
               Material.LIME_CONCRETE,
               var3.owned.contains(var2) ? "&aAlready owned" : "&aConfirm purchase",
               List.of("&7Click once to buy."),
               new Menu.Action("buy", var2, 0, var4.level())
            );
            this.show(var1, var5);
         }
      }
   }

   void refresh(Player var1) {
      if (var1.isOnline() && var1.getOpenInventory().getTopInventory().getHolder() instanceof Menu.View var2) {
         if (!this.plugin.access(var1)) {
            var1.closeInventory();
         } else {
            try {
               this.refreshing = true;
               switch (var2.type) {
                  case "journey":
                     this.journey(var1, var2.page);
                     break;
                  case "perks":
                     this.perks(var1, var2.page, var2.step);
                     break;
                  case "step":
                     this.step(var1, var2.step);
                     break;
                  case "confirm":
                     this.confirm(var1, var2.id);
               }
            } catch (Exception var8) {
            } finally {
               this.refreshing = false;
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void click(InventoryClickEvent var1) {
      if (var1.getView().getTopInventory().getHolder() instanceof Menu.View var2) {
         var1.setCancelled(true);
         if (var1.getWhoClicked() instanceof Player var10) {
            if (!this.plugin.access(var10)) {
               var10.closeInventory();
            } else {
               Menu.Action var11 = var2.actions.get(var1.getRawSlot());
               if (var11 != null) {
                  try {
                     switch (var11.type()) {
                        case "journey":
                           this.journey(var10, var11.page());
                           break;
                        case "perks":
                           this.perks(var10, var11.page(), var11.step());
                           break;
                        case "step":
                           this.step(var10, var11.step());
                           break;
                        case "confirm":
                           this.confirm(var10, var11.id());
                           break;
                        case "gift":
                           this.plugin.gifts.claim(var10, this.plugin.state(var10), this.plugin.journey.gifts.get(var11.id()));
                           break;
                        case "buy":
                           State var7 = this.plugin.state(var10);
                           Journey.Perk var8 = this.plugin.journey.perks.get(var11.id());
                           this.plugin.message(var10, this.plugin.purchases.buy(var7, var8, var3 -> {
                              this.plugin.message(var10, var3);

                              try {
                                 this.step(var10, var8.level());
                              } catch (Exception var5) {
                              }
                           }));
                     }
                  } catch (Exception var9) {
                     this.plugin.profileError(var10, var9);
                  }
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void drag(InventoryDragEvent var1) {
      if (var1.getView().getTopInventory().getHolder() instanceof Menu.View) {
         var1.setCancelled(true);
      }
   }

   static String money(double var0) {
      return String.format(Locale.US, "%,.0f", var0);
   }

   record Action(String type, String id, int page, int step) {
   }

   static final class View implements InventoryHolder {
      Inventory inventory;
      final String type;
      final int page;
      final int step;
      final String id;
      final Map<Integer, Menu.Action> actions = new HashMap<>();

      View(String var1, int var2, int var3, String var4) {
         this.type = var1;
         this.page = var2;
         this.step = var3;
         this.id = var4;
      }

      public Inventory getInventory() {
         return this.inventory;
      }
   }
}
