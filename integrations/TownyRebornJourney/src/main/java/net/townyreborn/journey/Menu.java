package net.townyreborn.journey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
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
            this.button(var5, 0, Material.ARROW, "&dPrevious page", List.of("&7The same path continues across pages."), new Menu.Action("journey", "", var2 - 1, 0));
         }

         if (var2 + 1 < this.plugin.journey.pages()) {
            this.button(var5, 8, Material.ARROW, "&aNext page",
               List.of("&7Continues at Level " + ((var2 + 1) * Journey.PATH.length + 1),
                  "&7Start on the " + (Journey.slot(var2 + 1, 0) / 9 == 1 ? "top" : "bottom") + " row of the next page."),
               new Menu.Action("journey", "", var2 + 1, 0));
         }

         this.button(
            var5,
            2,
            Material.EXPERIENCE_BOTTLE,
            "&dYour progress",
            this.progressLore(var1, var3),
            new Menu.Action("guide", "", var2, 0)
         );
         this.button(var5, 1, Material.BOOK, "&eHow to reach the next level",
            List.of("&7Choose an activity to earn Journey Points.", "&7Click for exact rates and your progress."),
            new Menu.Action("guide", "", var2, 0));
         this.button(var5, 7, Material.LIME_DYE, "&fPath colour legend", ProgressInfo.legend(), null);
         ItemStack var6 = this.item(
            Material.PLAYER_HEAD,
            "&dYour live stats &8• &f" + var1.getName(),
            this.liveStats(var1, var3)
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

            ArrayList<String> var9 = new ArrayList<>(ProgressInfo.requirement(this.plugin.journey, var3, var13));
            Journey.StepStatus status = this.plugin.journey.status(var3.points, var13);
            var9.add(switch (status) {
               case COMPLETED -> "&aCompleted level";
               case CURRENT -> "&eYour current Journey level";
               case LOCKED -> "&cFuture, locked level";
            });
            var9.add("&7Rank: &r" + this.plugin.journey.styledTitle(var13));
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
            Material var10 = switch (status) {
               case COMPLETED -> Material.LIME_STAINED_GLASS_PANE;
               case CURRENT -> Material.YELLOW_STAINED_GLASS_PANE;
               case LOCKED -> Material.RED_STAINED_GLASS_PANE;
            };
            this.button(var5, Journey.slot(var2, var12), var10,
               (status == Journey.StepStatus.COMPLETED ? "&a" : status == Journey.StepStatus.CURRENT ? "&e" : "&c") + "Level " + var13,
               var9, new Menu.Action("step", "", 0, var13));
         }

         this.show(var1, var5);
      }
   }

   void perks(Player var1, int var2, int var3) throws Exception {
      if (var1.isOnline() && this.plugin.access(var1)) {
         State var4 = this.plugin.state(var1);
         List<Journey.PerkPage> pages = this.plugin.journey.perkPages(var3);
         int var6 = pages.size();
         var2 = Math.max(0, Math.min(var6 - 1, var2));
         Journey.PerkPage selected = pages.get(var2);
         List<Journey.Perk> var5 = selected.items();
         Menu.View var7 = this.create("perks", var2, var3, "", "&d&lREBORN &8| &f" + (var3 == 0 ? selected.tier().label + " perks" : "Level " + var3 + " perks"));
         if (var3 == 0) {
            int slot = 0;
            for (Journey.Tier tier : this.plugin.journey.perkTiers()) {
               int targetPage = 0;
               while (pages.get(targetPage).tier() != tier) targetPage++;
               String name = (tier == selected.tier() ? "&a✓ " : "&d▶ ") + tier.label + " &8• &fClick to browse";
               List<String> lore = List.of("&aClick this head to switch rank perks.",
                  "&7See all " + tier.label + " features and required levels.", "&7The green label marks your selected rank page.",
                  "", "&7Buy individual features with game money.", "&7Higher rank pages can be previewed now.");
               this.button(var7, slot, tierIcon(tier), name, lore, new Menu.Action("perks", "", targetPage, 0));
               if (tier == Journey.Tier.BLAZE) {
                  ItemStack blaze = this.item(Material.PLAYER_HEAD, name, lore);
                  if (blaze.getItemMeta() instanceof SkullMeta skull) {
                     var profile = Bukkit.createPlayerProfile(UUID.nameUUIDFromBytes("TownyReborn:BlazeTab".getBytes(StandardCharsets.UTF_8)));
                     var textures = profile.getTextures();
                     textures.setSkin(URI.create("https://textures.minecraft.net/texture/d06e342f90ec538aaa1552b224f264a040840902e126d91ece6139aa5b3c7cc3").toURL());
                     profile.setTextures(textures);
                     skull.setOwnerProfile(profile);
                     blaze.setItemMeta(skull);
                  }
                  var7.inventory.setItem(slot, blaze);
               }
               slot++;
            }
            this.button(var7, 6, Material.BOOK, "&eClick a rank head &8• &fBrowse all perks",
               List.of("&aThe five heads above are clickable rank pages.", "&7Skeleton → Creeper → Blaze → Dragon → Wither",
                  "&7Preview the later features and their required levels.", "&7Purchases are individual; this does not buy a rank."), null);
         }

         for (int var8 = 0; var8 < 36; var8++) {
            int var9 = var8;
            if (var9 >= var5.size()) {
               break;
            }

            Journey.Perk var10 = (Journey.Perk)var5.get(var9);
            boolean var11 = var4.owned.contains(var10.id());
            boolean var12 = this.plugin.journey.level(var4.points) >= var10.level();
            ArrayList var13 = new ArrayList<>(
               List.of("&7Feature tier: &f" + var10.tier().label, "&7Available at Level &d" + var10.level(), "&7Price: &a$" + money(var10.price()), "&7Unlock: &fPermanent")
            );
            if (!var10.requires().isEmpty()) {
               var13.add("&7Previous upgrade: &f" + this.plugin.journey.perks.get(var10.requires()).name());
            }

            for (String var15 : var10.commands()) {
               var13.add((var15.startsWith("/") ? "&7Command: &f" : "&7") + var15);
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
            7,
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

   private static Material tierIcon(Journey.Tier tier) {
      return switch (tier) {
         case SKELETON -> Material.SKELETON_SKULL;
         case CREEPER -> Material.CREEPER_HEAD;
         case BLAZE -> Material.PLAYER_HEAD;
         case DRAGON -> Material.DRAGON_HEAD;
         case WITHER -> Material.WITHER_SKELETON_SKULL;
         case EXTRA -> Material.CHEST;
      };
   }

   private List<String> progressLore(Player player, State state) {
      return ProgressInfo.summary(this.plugin.journey, state);
   }

   private List<String> liveStats(Player player, State state) {
      int level = this.plugin.journey.level(state.points);
      List<String> lore = new ArrayList<>(List.of("&r" + this.plugin.journey.styledTitle(level),
         "&7Journey level: &a" + level + " &8/ &f" + this.plugin.journey.steps,
         "&7Total Journey Points: &e" + state.points,
         "&7Next level: &a" + this.plugin.journey.percent(state.points) + "% &8• &f" + this.plugin.journey.remainingPercent(state.points) + "% remaining", "",
         "&dYour live stats", "&7Balance: &a$" + money(this.plugin.integrations.balance(player.getUniqueId())),
         "&7Active Journey time: &f" + String.format(Locale.US, "%.1fh", state.journeyMinutes / 60.0),
         "&7Total playtime: &f" + String.format(Locale.US, "%.1fh", player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 72000.0)));
      long mcmmo = this.plugin.sources.number(player, "%mcmmo_power_level%");
      lore.add("&7mcMMO power level: &f" + (mcmmo < 0 ? "Unavailable" : mcmmo));
      Map<String, Long> jobs = this.plugin.integrations.jobsLevels(player);
      lore.add("&7Jobs level total: &f" + (jobs.isEmpty() ? "No active level data" : jobs.values().stream().mapToLong(Long::longValue).sum()));
      jobs.entrySet().stream().sorted(Map.Entry.comparingByKey()).limit(4)
         .forEach(job -> lore.add("&8  &7" + job.getKey().replaceFirst("^job:", "") + ": &f" + job.getValue()));
      lore.add("&7Fish caught: &f" + player.getStatistic(Statistic.FISH_CAUGHT));
      long quests = this.plugin.sources.number(player, "%quests_completed%");
      lore.add("&7Quests completed: &f" + (quests < 0 ? "Unavailable" : quests));
      lore.add("&7Mobs defeated: &f" + player.getStatistic(Statistic.MOB_KILLS));
      long owned = state.owned.stream().filter(this.plugin.journey.perks::containsKey).count();
      lore.add("&7Permanent features owned: &a" + owned + " &8/ &f" + this.plugin.journey.perks.size());
      lore.add("");
      lore.add("&8Live data refreshes every 30 seconds.");
      return lore;
   }

   void guide(Player player, int page) throws Exception {
      if (!player.isOnline() || !this.plugin.access(player)) return;
      State state = this.plugin.state(player);
      View view = this.create("guide", page, 0, "", "&d&lREBORN &8| &fHow to progress");
      for (int slot : new int[]{0,8,46,52}) this.button(view, slot, Material.MAGENTA_STAINED_GLASS_PANE, " ", List.of(), null);
      for (int slot : new int[]{1,7,47,51}) this.button(view, slot, Material.LIME_STAINED_GLASS_PANE, " ", List.of(), null);
      this.button(view, 4, Material.EXPERIENCE_BOTTLE, "&eYour next goal",
         this.progressLore(player, state), null);
      List<String> active = new ArrayList<>(ProgressInfo.source(this.plugin.getConfig(), state, "playtime", "points-per-minute", "Move, build, farm or interact for one minute."));
      active.add("&7Standing idle does not earn time points.");
      active.add("&7Active Journey minutes: &f" + state.journeyMinutes);
      this.button(view, 10, Material.CLOCK, "&d1. &fActive playtime", active, null);
      this.sourceButton(view, state, 13, Material.DIAMOND_SWORD, "2. mcMMO skills", "mcmmo", "points-per-level", "Gain a new mcMMO power level.");
      this.sourceButton(view, state, 16, Material.IRON_PICKAXE, "3. Jobs", "jobs", "points-per-level", "Gain a new level in one of your Jobs.");
      this.sourceButton(view, state, 19, Material.FISHING_ROD, "4. Fishing", "fishing", "points-per-catch", "Catch a fish using a fishing rod.");
      this.sourceButton(view, state, 22, Material.COMPASS, "5. Exploration", "exploration", "points-per-biome", "Visit a biome you have not recorded yet.");
      this.sourceButton(view, state, 25, Material.KNOWLEDGE_BOOK, "6. Advancements", "advancements", "points-per-advancement", "Complete a new non-recipe advancement.");
      if (this.plugin.getConfig().getBoolean("sources.quests.enabled", false))
         this.sourceButton(view, state, 40, Material.BOOK, "Optional Quests", "quests", "points-per-completion", "Complete another quest.");
      this.button(view, 28, Material.NETHER_STAR, "&dHow Journey Points work", ProgressInfo.explanation(this.plugin.journey, this.plugin.getConfig()), null);
      this.button(view, 31, Material.MAP, "&dYour live stats", this.liveStats(player, state), null);
      this.button(view, 34, Material.CLOCK, "&dDaily limits &8• &f00:00 UTC",
         List.of("&7Each source has its own daily allowance.", "&7Daily reset: &f00:00 UTC &8(server time)",
            "&7Unused daily allowance does not carry over.", "&7Hover each activity to see today's remaining JP.", "",
            "&7Your total Journey Points never reset daily.", "&7Only new skill/Job records earn points.", "&7Progress refreshes every 30 seconds."), null);
      this.button(view, 45, Material.ARROW, "&dBack to journey", List.of(), new Action("journey", "", page, 0));
      this.button(view, 49, Material.ENDER_CHEST, "&aPermanent perks", List.of("&aClick to browse all five rank pages."), new Action("perks", "", 0, 0));
      this.show(player, view);
   }

   private void sourceButton(View view, State state, int slot, Material icon, String title, String source, String rateKey, String action) {
      boolean enabled = this.plugin.getConfig().getBoolean("sources." + source + ".enabled", true);
      List<String> lore = enabled ? ProgressInfo.source(this.plugin.getConfig(), state, source, rateKey, action) : List.of("&cThis source is disabled on this server.");
      this.button(view, slot, enabled ? icon : Material.GRAY_DYE, (enabled ? "&a" : "&7") + title, lore, null);
   }

   Material rewardIcon(Journey.Gift gift) {
      Material icon = Material.matchMaterial(gift.icon());
      return icon == null || !icon.isItem() ? Material.CHEST : icon;
   }

   void step(Player var1, int var2) throws Exception {
      if (var1.isOnline() && this.plugin.access(var1)) {
         State var3 = this.plugin.state(var1);
         Menu.View var4 = this.create("step", 0, var2, "", "&d&lREBORN &8| &fLevel " + var2);
         this.button(
            var4,
            4,
            Material.NETHER_STAR,
            "&r" + this.plugin.journey.styledTitle(var2),
            ProgressInfo.requirement(this.plugin.journey, var3, var2),
            new Menu.Action("guide", "", this.plugin.journey.page(var2), 0)
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
                  rewardIcon(var9),
                  "&a" + var9.name(),
                  List.of(
                     "&aFree milestone reward",
                     "&7No money requirement.",
                     var9.townRequired() ? "&7Join a town to claim; you can claim later." : "&7Claim once when you reach this level.",
                     var3.claimed.contains(var9.id()) ? "&aAlready claimed" :
                        this.plugin.journey.level(var3.points) < var9.level() ? "&cReach Level " + var9.level() + " first" : "&aClick to claim"
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
                     break;
                  case "guide":
                     this.guide(var1, var2.page);
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
                        case "guide":
                           this.guide(var10, var11.page());
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
