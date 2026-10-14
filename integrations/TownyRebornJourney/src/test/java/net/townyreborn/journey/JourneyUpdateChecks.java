package net.townyreborn.journey;

import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipFile;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.*;

/** Upgrade, crash-safety and actual inventory output checks for the owner's requested changes. */
public final class JourneyUpdateChecks {
    static int checks; static Path root; static Journey journey; static YamlConfiguration config, original;
    interface Check { void run() throws Exception; }
    static void test(String name, Check check) throws Exception { check.run(); checks++; System.out.println("PASS " + name); }
    static void require(boolean ok) { if (!ok) throw new AssertionError("Journey update regression"); }
    static YamlConfiguration readJar(String name) throws Exception {
        try (ZipFile zip = new ZipFile(name)) { return read(new String(zip.getInputStream(zip.getEntry("config.yml")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)); }
    }
    static YamlConfiguration read(String text) throws Exception { YamlConfiguration c = new YamlConfiguration(); c.loadFromString(text); return c; }
    static Store store(String name) throws Exception { return new Store(root.resolve(name), journey); }
    static State ready(Store store) throws Exception { State s=store.get(UUID.randomUUID(),"TestPlayer");s.points=journey.threshold(116);return s; }
    static class Rewards implements GiftClaims.Backend {
        int grants, commands, deposits, blocks; double money; boolean uncertain, decline;
        CompletableFuture<Void> future=new CompletableFuture<>();
        public CompletableFuture<Void> grant(UUID id,List<String> nodes) { grants++;return future; }
        public boolean dispatch(String command) { commands++;return true; }
        public boolean deposit(UUID id,double amount) { deposits++; if(decline)return false;money+=amount;if(uncertain)throw new IllegalStateException("ambiguous deposit");return true; }
        public void townBonus(String town,int count) { blocks+=count; }
        public void main(Runnable task) {task.run();}
    }
    record Tile(Material icon,String name,List<String> lore) {}
    static final class Gui implements AutoCloseable {
        final Map<ItemStack,Tile> tiles=new IdentityHashMap<>(); final Map<Inventory,Map<Integer,ItemStack>> inventories=new IdentityHashMap<>();
        final Inventory[] current={mock(Inventory.class)};
        final MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class);
        final RebornPlugin plugin=mock(RebornPlugin.class); final Player player=mock(Player.class); final Menu menu;
        Gui() throws Exception {
            plugin.journey=journey;plugin.store=store("gui");plugin.sources=mock(ProgressSources.class);plugin.integrations=mock(Integrations.class);
            menu=spy(new Menu(plugin));plugin.menu=menu;
            // Item registry resolution belongs to the live Paper runtime, like ItemStack creation below.
            doReturn(Material.GOLD_INGOT).when(menu).rewardIcon(journey.gifts.get("milestone_001"));
            doReturn(Material.TRIPWIRE_HOOK).when(menu).rewardIcon(journey.gifts.get("milestone_005"));
            when(plugin.getConfig()).thenReturn(config);when(plugin.access(player)).thenReturn(true);
            when(player.isOnline()).thenReturn(true);when(player.getUniqueId()).thenReturn(UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"));when(player.getName()).thenReturn("TestPlayer");
            when(plugin.state(player)).thenAnswer(a->plugin.store.get(player.getUniqueId(),player.getName()));
            when(plugin.sources.number(player,"%mcmmo_power_level%")).thenReturn(300L);
            when(plugin.integrations.jobsLevels(player)).thenReturn(Map.of("job:Miner",15L));
            when(player.getStatistic(Statistic.FISH_CAUGHT)).thenReturn(123);
            Server server=mock(Server.class);when(plugin.getServer()).thenReturn(server);doReturn(List.of(player)).when(server).getOnlinePlayers();
            bukkit.when(Bukkit::getTPS).thenReturn(new double[]{19.8,19.9,20});
            doAnswer(a-> { ItemStack item=mock(ItemStack.class);when(item.getItemMeta()).thenReturn(mock(ItemMeta.class));tiles.put(item,new Tile(a.getArgument(0),a.getArgument(1),List.copyOf(a.getArgument(2))));return item; })
                .when(menu).item(any(Material.class),anyString(),anyList());
            bukkit.when(()->Bukkit.createInventory(any(InventoryHolder.class),eq(54),anyString())).thenAnswer(a->{
                Inventory inventory=mock(Inventory.class);Map<Integer,ItemStack> content=new HashMap<>();inventories.put(inventory,content);
                when(inventory.getHolder()).thenReturn(a.getArgument(0));
                doAnswer(call->{content.put(call.getArgument(0),call.getArgument(1));return null;}).when(inventory).setItem(anyInt(),any(ItemStack.class));
                doAnswer(call->{ItemStack[] items=call.getArgument(0);content.clear();for(int i=0;i<items.length;i++)content.put(i,items[i]);return null;}).when(inventory).setContents(any(ItemStack[].class));
                when(inventory.getContents()).thenAnswer(call->{ItemStack[] items=new ItemStack[54];content.forEach((i,item)->items[i]=item);return items;});
                when(inventory.getItem(anyInt())).thenAnswer(call->content.get(call.getArgument(0)));
                return inventory;
            });
            InventoryView view=mock(InventoryView.class);when(view.getTopInventory()).thenAnswer(a->current[0]);when(player.getOpenInventory()).thenReturn(view);
            when(player.openInventory(any(Inventory.class))).thenAnswer(a->{current[0]=a.getArgument(0);return view;});
        }
        Tile tile(int slot){return tiles.get(inventories.get(current[0]).get(slot));}
        String text(int slot){Tile t=tile(slot);return t.name()+"\n"+String.join("\n",t.lore());}
        Menu.View view(){return (Menu.View)current[0].getHolder();}
        State state() throws Exception {return plugin.state(player);}
        public void close(){bukkit.close();}
    }

    public static void main(String[] args) throws Exception {
        root=Path.of(args[0]);Files.createDirectories(root);config=readJar(args[1]);original=readJar(args[2]);journey=new Journey(config);
        test("continuous snake across four and extended pages",()->{
            for(int page=0;page<8;page++){
                Set<Integer> seen=new HashSet<>();int previous=-1;
                for(int i=0;i<Journey.PATH.length;i++){int slot=Journey.slot(page,i);require(slot>=9&&slot<54&&seen.add(slot));if(previous>=0)require(Math.abs(slot/9-previous/9)+Math.abs(slot%9-previous%9)==1);previous=slot;}
                require(previous/9==Journey.slot(page+1,0)/9);
            }
        });
        test("whole 116-step catalogue has useful free rewards",()->{
            require(journey.gifts.size()==116&&journey.perks.size()==43);
            for(int level=1;level<=116;level++){int l=level;require(journey.gifts.values().stream().filter(g->g.level()==l).count()==1);}
        });
        test("no UltraCosmetics or cosmetic-crate rewards",()->{
            for(var g:journey.gifts.values())require(g.permissions().stream().noneMatch(p->p.toLowerCase().contains("ultracosmetics"))&&g.commands().stream().noneMatch(c->c.contains(" cosmetic ")||c.startsWith("uc ")));
        });
        test("only three occasional Minecraft XP rewards",()->require(journey.gifts.values().stream().filter(g->g.commands().stream().anyMatch(c->c.startsWith("minecraft:experience"))).count()==3));
        test("daily supporter perk removed, supporter keys only one-time gifts",()->{
            require(!journey.perks.containsKey("daily_supporter"));
            require(journey.perks.values().stream().flatMap(p->p.permissions().stream()).noneMatch(p->p.equals("townysmp.supporter.dailycrate")));
            require(journey.gifts.values().stream().filter(g->g.commands().stream().anyMatch(c->c.contains(" patron "))).count()==4);
        });
        test("warp limits are two and three, not trading-shop limits",()->{
            require(journey.perks.get("warps2").permissions().contains("axplayerwarps.warps.2"));
            require(journey.perks.get("warps3").permissions().contains("axplayerwarps.warps.3"));
            for(var p:journey.perks.values())for(String n:p.permissions())if(n.startsWith("axplayerwarps.warps."))require(Integer.parseInt(n.substring(n.lastIndexOf('.')+1))<=3);
            require(journey.perks.get("shops200").name().contains("QuickShop")&&!journey.perks.get("shops200").commands().contains("/shops"));
        });
        test("remaining percentages and points at boundaries",()->{
            require(journey.remainingPercent(0)==100&&journey.remaining(0,2)==150);
            require(journey.percent(75)==50&&journey.remainingPercent(75)==50&&journey.remaining(75,2)==75);
            require(journey.remainingPercent(journey.threshold(116))==0&&journey.remaining(journey.threshold(116),116)==0);
        });
        test("abbreviations match title boundaries",()->{
            require(journey.abbreviation(1).equals("J001")&&journey.abbreviation(4).equals("J004")&&journey.abbreviation(17).equals("J017")&&journey.abbreviation(116).equals("J116"));
        });
        Path sourceRoot=Path.of(args[4]);
        test("updated catalogue preserves prices permission chains and stable reward IDs",()->{
            YamlConfiguration previous=readJar(sourceRoot.resolve("releases/TownyRebornProgression-2.0.0-beta.3.jar").toString());
            require(config.getLong("journey.points-first")==150&&config.getLong("journey.points-increase")==30&&journey.threshold(116)==213900);
            var newGifts=config.getConfigurationSection("gifts");var oldGifts=previous.getConfigurationSection("gifts");
            require(newGifts.getKeys(false).equals(oldGifts.getKeys(false)));
            for(String id:oldGifts.getKeys(false))for(String key:List.of("level","permissions","legacy-ids"))require(Objects.equals(oldGifts.get(id+"."+key),newGifts.get(id+"."+key)));
            for(String id:journey.perks.keySet())for(String field:List.of("level","price","permissions","legacy-markers","requires"))
                if(!id.equals("fly")||!field.equals("level"))require(Objects.equals(config.get("perks."+id+"."+field),previous.get("perks."+id+"."+field)));
        });
        test("startup reads existing configuration without rewriting it",()->{
            Path folder=root.resolve("manual-config");Files.createDirectories(folder);var old=read(config.saveToString());
            old.set("catalogue-version",1);old.set("legacy-cleanup.enabled",true);old.set("display.auto-configure",true);old.set("sources.jobs.points-per-level",26);
            String text="# custom owner comments\n"+old.saveToString();Path file=folder.resolve("config.yml");Files.writeString(file,text);
            RebornPlugin plugin=mock(RebornPlugin.class,CALLS_REAL_METHODS);doNothing().when(plugin).saveDefaultConfig();doReturn(folder.toFile()).when(plugin).getDataFolder();
            var method=RebornPlugin.class.getDeclaredMethod("prepareConfig");method.setAccessible(true);method.invoke(plugin);require(Files.readString(file).equals(text));
            require(!Files.exists(folder.resolve("legacy-backup")));
        });
        test("incompatible old configuration fails without replacing it",()->{
            Path folder=root.resolve("invalid-config");Files.createDirectories(folder);Path file=folder.resolve("config.yml");String text="progression:\n  paused: true\n";Files.writeString(file,text);
            RebornPlugin plugin=mock(RebornPlugin.class,CALLS_REAL_METHODS);doNothing().when(plugin).saveDefaultConfig();doReturn(folder.toFile()).when(plugin).getDataFolder();
            var method=RebornPlugin.class.getDeclaredMethod("prepareConfig");method.setAccessible(true);boolean failed=false;try{method.invoke(plugin);}catch(java.lang.reflect.InvocationTargetException expected){failed=true;}
            require(failed&&Files.readString(file).equals(text));
        });
        test("external config writers and cleanup are absent from the JAR",()->{
            try(ZipFile z=new ZipFile(args[1])){for(String name:List.of("ConfigUpgrade","DisplaySetup","LegacyCleanup"))require(z.getEntry("net/townyreborn/journey/"+name+".class")==null);}
        });
        test("five ordered supporter feature tiers, with fly last",()->{
            require(journey.perkTiers().equals(List.of(Journey.Tier.SKELETON,Journey.Tier.CREEPER,Journey.Tier.BLAZE,Journey.Tier.DRAGON,Journey.Tier.WITHER)));
            var pages=journey.perkPages(0);require(pages.size()==5);
            var last=pages.get(4).items();require(last.get(last.size()-1).id().equals("fly")&&journey.perks.get("fly").level()==116);
            require(pages.stream().flatMap(p->p.items().stream()).count()==43);
        });
        test("current level is yellow; future level is locked",()->{
            long points=journey.threshold(13)+100;
            require(journey.status(points,12)==Journey.StepStatus.COMPLETED&&journey.status(points,13)==Journey.StepStatus.CURRENT&&journey.status(points,14)==Journey.StepStatus.LOCKED);
            require(journey.status(journey.threshold(116),116)==Journey.StepStatus.COMPLETED);
        });
        test("activity example combines into one total with cumulative thresholds",()->{
            State state=new State(UUID.randomUUID(),"Test");state.capped("A","playtime",10,100000);state.capped("A","fishing",15,300);state.advanceHighWater("mcmmo",300,5);
            state.capped("A","mcmmo",state.advanceHighWater("mcmmo",301,5),500);
            require(state.points==30&&journey.level(state.points)==1&&journey.threshold(2)==150&&journey.threshold(3)==330&&journey.threshold(4)==540);
            require(!config.getBoolean("sources.quests.enabled"));
        });
        test("all beta1 gift IDs map to their original milestone",()->{
            for(String id:original.getConfigurationSection("gifts").getKeys(false)){
                int level=original.getInt("gifts."+id+".level");require(journey.canonicalGift(id).equals(String.format(Locale.ROOT,"milestone_%03d",level)));
            }
        });
        test("explicit owner-defined old reward aliases remain supported",()->{
            var edited=read(config.saveToString());var aliases=new ArrayList<>(edited.getStringList("gifts.milestone_004.legacy-ids"));aliases.add("special_old_reward");edited.set("gifts.milestone_004.legacy-ids",aliases);
            require(new Journey(edited).canonicalGift("special_old_reward").equals("milestone_004"));
        });
        test("old completed receipts retain points and paid upgrades",()->{
            Store s=store("claimed");State state=ready(s);long points=state.points;state.owned.add("homes30");state.minutes=456;state.claimed.addAll(List.of("cosmetic_hats_chicken","vanilla_xp_1","daily_key_5"));s.save(state);
            State loaded=new Store(root.resolve("claimed"),journey).get(state.uuid,"TestPlayer");
            require(loaded.points==points&&loaded.minutes==456&&loaded.owned.contains("homes8")&&loaded.claimed.containsAll(List.of("milestone_001","milestone_004","milestone_005")));
            Rewards b=new Rewards();new GiftClaims(journey,s,b).claim(loaded,journey.gifts.get("milestone_001"),"",x->{});require(b.grants==0&&b.deposits==0);
        });
        test("old LP markers import as claimed without replacement payouts",()->{
            State state=new State(UUID.randomUUID(),"Test");journey.importGiftMarkers(state,Set.of(Journey.giftMarker("cosmetic_hats_chicken"),Journey.giftMarker("vanilla_xp_1")));
            require(state.claimed.containsAll(List.of("milestone_001","milestone_004")));
        });
        test("old pending receipt retains original commands and town",()->{
            Store s=store("oldpending");State state=ready(s);state.pendingGift="cosmetic_hats_chicken";state.pendingGiftCommands=List.of("original command");state.pendingGiftTown="OriginalTown";state.pendingGiftTownBlocks=2;s.save(state);
            State loaded=new Store(root.resolve("oldpending"),journey).get(state.uuid,"TestPlayer");
            require(loaded.pendingGift.equals("milestone_004")&&loaded.pendingGiftCommands.equals(List.of("original command"))&&loaded.pendingGiftTown.equals("OriginalTown")&&loaded.pendingGiftTownBlocks==2&&loaded.pendingGiftMoney==0);
            journey.importGiftMarkers(loaded,Set.of(Journey.giftMarker("cosmetic_hats_chicken")));require(!loaded.claimed.contains("milestone_004"));
            Rewards b=new Rewards();GiftClaims claims=new GiftClaims(journey,s,b);claims.claim(loaded,journey.gifts.get("milestone_004"),"",x->{});claims.delivered(loaded);require(b.commands==0&&b.deposits==0&&loaded.claimed.contains("milestone_004"));
        });
        var moneyGift=journey.gifts.get("milestone_001");
        test("money deposited once after durable LP save",()->{
            Store s=store("money");State state=ready(s);Rewards b=new Rewards();GiftClaims claims=new GiftClaims(journey,s,b);
            claims.claim(state,moneyGift,"",x->{});claims.claim(state,moneyGift,"",x->{});require(b.deposits==0&&state.pendingGiftMoney==moneyGift.money());
            b.future.complete(null);claims.claim(state,moneyGift,"",x->{});require(b.deposits==1&&b.money==moneyGift.money()&&state.claimed.contains(moneyGift.id())&&state.pendingGiftMoney==0);
        });
        test("uncertain deposit and restart cannot duplicate money",()->{
            Store s=store("uncertainmoney");State state=ready(s);Rewards b=new Rewards();b.uncertain=true;GiftClaims claims=new GiftClaims(journey,s,b);
            claims.claim(state,moneyGift,"",x->{});b.future.complete(null);require(state.pendingGift.equals(moneyGift.id())&&b.deposits==1);
            Store reload=new Store(root.resolve("uncertainmoney"),journey);State loaded=reload.get(state.uuid,"Test");journey.importGiftMarkers(loaded,Set.of(Journey.giftMarker(moneyGift.id())));
            new GiftClaims(journey,reload,b).claim(loaded,moneyGift,"",x->{});require(b.deposits==1&&!loaded.claimed.contains(moneyGift.id())&&loaded.pendingGiftMoney==moneyGift.money());
        });
        test("declined deposit leaves review receipt and no item effects",()->{
            Store s=store("declinedmoney");State state=ready(s);Rewards b=new Rewards();b.decline=true;GiftClaims claims=new GiftClaims(journey,s,b);claims.claim(state,moneyGift,"",x->{});b.future.complete(null);
            require(state.busy()&&b.money==0&&b.commands==0);claims.claim(state,moneyGift,"",x->{});require(b.deposits==1);
        });
        test("post-deposit profile save failure cannot replay",()->{
            Store real=store("savefailed");Store s=spy(real);State state=ready(s);Rewards b=new Rewards();
            doAnswer(a->{State x=a.getArgument(0);if(x.claimed.contains(moneyGift.id()))throw new IOException("disk");a.callRealMethod();return null;}).when(s).save(any(State.class));
            GiftClaims claims=new GiftClaims(journey,s,b);claims.claim(state,moneyGift,"",x->{});b.future.complete(null);require(state.busy()&&b.deposits==1);
            State loaded=new Store(root.resolve("savefailed"),journey).get(state.uuid,"Test");claims.claim(loaded,moneyGift,"",x->{});require(b.deposits==1);
        });
        test("UI callback failure cannot reopen delivered reward",()->{
            Store s=store("callback");State state=ready(s);Rewards b=new Rewards();GiftClaims claims=new GiftClaims(journey,s,b);
            claims.claim(state,moneyGift,"",x->{throw new IllegalStateException("UI");});b.future.complete(null);
            require(!state.busy()&&state.claimed.contains(moneyGift.id()));claims.claim(state,moneyGift,"",x->{});require(b.deposits==1);
        });
        test("LP save failure gives no money and cannot replay",()->{
            Store s=store("moneylp");State state=ready(s);Rewards b=new Rewards();GiftClaims claims=new GiftClaims(journey,s,b);claims.claim(state,moneyGift,"",x->{});b.future.completeExceptionally(new IOException("LP"));
            require(state.busy()&&b.deposits==0);claims.claim(state,moneyGift,"",x->{});require(b.grants==1);
        });
        test("manual receipt closure never pays again",()->{
            Store s=store("manualmoney");State state=ready(s);Rewards b=new Rewards();b.uncertain=true;GiftClaims claims=new GiftClaims(journey,s,b);claims.claim(state,moneyGift,"",x->{});b.future.complete(null);claims.delivered(state);claims.claim(state,moneyGift,"",x->{});require(b.deposits==1&&state.pendingGiftMoney==0);
        });
        test("money receipt survives mixed cash-and-key reward",()->{
            Store s=store("mixed");State state=ready(s);Rewards b=new Rewards();var gift=journey.gifts.get("milestone_116");GiftClaims claims=new GiftClaims(journey,s,b);
            claims.claim(state,gift,"",x->{});b.future.complete(null);claims.claim(state,gift,"",x->{});require(b.deposits==1&&b.commands==1&&b.money==100000);
        });
        test("malformed money receipt fails closed",()->{
            Store s=store("invalidmoney");UUID id=UUID.randomUUID();Files.writeString(root.resolve("invalidmoney/journeydata/"+id+".yml"),"schema: 2\nuuid: "+id+"\npending-gift-money: -1\n");
            boolean failed=false;try{s.get(id,"Test");}catch(IOException expected){failed=true;}require(failed);
        });
        test("TAB ordered format preserves mcMMO, RGB display and AFK",()->{
            YamlConfiguration tab=new YamlConfiguration();tab.load(Path.of(args[3]).toFile());
            for(String group:tab.getKeys(false)){String p=tab.getString(group+".tabprefix"),s=tab.getString(group+".tabsuffix");require(p.indexOf("%luckperms_prefix%")<p.indexOf("%reborn_short_title_colored%")&&!p.contains("mcmmo")&&s.contains("mcmmo_power_level")&&s.contains("%essentials_afk%")&&!s.startsWith(" ")&&tab.getString(group+".customtabname").equals("%player_displayname%"));}
        });
        test("nametag has rank spacing and attached mcMMO on its Journey line",()->{
            var c=read(Files.readString(sourceRoot.resolve("ui/LyttleNametag-config.yml")));String[] lines=c.getString("nametag").split("\n");
            require(lines.length==3&&lines[0].contains("town_unformatted")&&lines[0].contains("nation_unformatted")&&lines[1].contains("<luckperms_prefix>  %reborn_title_mini%")&&lines[1].contains("%reborn_title_mini%<gray>%string_replaceCharacters_small-numbers_{mcmmo_power_level}%")&&lines[2].equals("<displayname>")&&c.getInt("view_distance")==64);
        });
        test("Quests screenshot layout retains shortcut two slots right of Nether Star",()->{
            var q=read(Files.readString(sourceRoot.resolve("ui/Quests-config.yml")));
            require(q.getString("custom-elements.categories.49.display.type").equals("NETHER_STAR"));
            require(q.getStringList("custom-elements.categories.51.commands").equals(List.of("rebornadmin open {player}"))&&q.getString("custom-elements.categories.51.result").equals("CLOSE_MENU"));
            require(q.getBoolean("options.categories-enabled")&&q.getInt("options.quest-limit.default")==6);
        });
        test("progression command alias keeps the normal access permission",()->{
            try(ZipFile z=new ZipFile(args[1])){var m=read(new String(z.getInputStream(z.getEntry("plugin.yml")).readAllBytes()));require(m.getStringList("commands.reborn.aliases").contains("progression")&&m.getString("commands.reborn.permission").equals(RebornPlugin.ACCESS));}
        });
        test("progress descriptions match actual source rates and daily limits",()->{
            State s=new State(UUID.randomUUID(),"Test");s.day=DailyTime.today();s.daily.put("fishing",120L);
            String guide=String.join("\n",ProgressInfo.source(config,s,"fishing","points-per-catch","Catch a fish"));require(guide.contains("+3 JP")&&guide.contains("120 JP")&&guide.contains("180 JP"));
        });
        try(Gui gui=new Gui()){
            test("inventory shows exact goal and remaining percent on bottle",()->{
                gui.state().points=75;gui.menu.journey(gui.player,0);String text=gui.text(2);require(text.contains("Still remaining: &e50%")&&text.contains("75 more Journey Points"));require(gui.text(4).contains("mcMMO power level: &f300")&&gui.text(4).contains("Fish caught: &f123"));
            });
            test("inventory legend and green yellow red status",()->{
                gui.state().points=journey.threshold(2);gui.menu.journey(gui.player,0);
                require(gui.text(7).contains("Green:")&&gui.text(7).contains("Yellow:")&&gui.text(7).contains("Red:"));
                require(gui.tile(Journey.slot(0,0)).icon()==Material.LIME_STAINED_GLASS_PANE&&gui.tile(Journey.slot(0,1)).icon()==Material.YELLOW_STAINED_GLASS_PANE&&gui.tile(Journey.slot(0,2)).icon()==Material.RED_STAINED_GLASS_PANE);
                require(gui.tile(Journey.slot(0,1)).name().startsWith("&e")&&gui.text(Journey.slot(0,1)).contains("current Journey")&&gui.text(Journey.slot(0,2)).contains("Still needed: &e180 JP"));
            });
            test("second inventory page starts at bottom and continues upward",()->{
                gui.menu.journey(gui.player,1);require(gui.text(45).contains("Level 30")&&gui.text(17).contains("Level 58")&&gui.text(8).contains("top row"));
            });
            test("guide aligns all six sources with UTC limits and removes server snapshot",()->{
                gui.menu.guide(gui.player,0);require(gui.text(10).contains("+1 JP")&&gui.text(13).contains("+5 JP")&&gui.text(16).contains("+12 JP")&&gui.text(19).contains("+3 JP")&&gui.text(22).contains("+8 JP")&&gui.text(25).contains("+15 JP"));
                require(gui.text(28).contains("same progress bar")&&gui.text(28).contains("30 JP")&&gui.text(28).contains("150 total JP")&&gui.text(34).contains("00:00 UTC"));
                for(int i=0;i<54;i++)require(!gui.text(i).contains("Server snapshot")&&!gui.text(i).contains("TPS")&&!gui.text(i).contains("Berlin"));
            });
            test("free rewards have actual item and money icons",()->{
                gui.menu.step(gui.player,1);require(gui.tile(10).icon()==Material.GOLD_INGOT&&gui.text(10).contains("$2,000"));
                gui.menu.step(gui.player,5);require(gui.tile(10).icon()==Material.TRIPWIRE_HOOK);
            });
            test("shop display cannot be mistaken for player warps",()->{
                gui.menu.perks(gui.player,0,62);require(gui.text(9).contains("200 QuickShop trading shops")&&gui.text(9).contains("Trading shops; separate from player warps")&&!gui.text(9).contains("/shops"));
            });
            test("inventory presents rank tabs and fly as the last Wither perk",()->{
                gui.menu.perks(gui.player,4,0);require(gui.text(0).contains("Skeleton")&&gui.text(4).contains("Wither")&&gui.text(16).contains("Personal flight")&&gui.view().actions.get(16).id().equals("fly"));
            });
            test("rank tab click opens the selected tier",()->{
                gui.menu.perks(gui.player,0,0);var event=mock(InventoryClickEvent.class);InventoryView open=gui.player.getOpenInventory();when(event.getView()).thenReturn(open);when(event.getWhoClicked()).thenReturn(gui.player);when(event.getRawSlot()).thenReturn(4);gui.menu.click(event);
                require(gui.view().page==4&&gui.text(16).contains("Personal flight"));
            });
            test("Quests bridge opens on the next tick and checks target access",()->{
                gui.bukkit.when(()->Bukkit.getPlayerExact("TestPlayer")).thenReturn(gui.player);
                CommandSender console=mock(CommandSender.class);when(console.hasPermission("townyreborn.progression.admin")).thenReturn(true);
                Server server=gui.plugin.getServer();BukkitScheduler scheduler=mock(BukkitScheduler.class);when(server.getScheduler()).thenReturn(scheduler);
                final Runnable[] task={null};doAnswer(a->{task[0]=a.getArgument(1);return null;}).when(scheduler).runTask(eq(gui.plugin),any(Runnable.class));
                doCallRealMethod().when(gui.plugin).admin(any(CommandSender.class),any(String[].class));
                gui.plugin.admin(console,new String[]{"open","TestPlayer"});require(task[0]!=null);task[0].run();require(gui.view().type.equals("journey"));
                task[0]=null;when(gui.plugin.access(gui.player)).thenReturn(false);gui.plugin.admin(console,new String[]{"open","TestPlayer"});require(task[0]==null);when(gui.plugin.access(gui.player)).thenReturn(true);
            });
            test("bottle click opens guide and cancels item movement",()->{
                gui.menu.journey(gui.player,0);var event=mock(InventoryClickEvent.class);InventoryView open=gui.player.getOpenInventory();when(event.getView()).thenReturn(open);when(event.getWhoClicked()).thenReturn(gui.player);when(event.getRawSlot()).thenReturn(2);
                gui.menu.click(event);verify(event).setCancelled(true);require(gui.view().type.equals("guide"));
            });
            test("revoked access closes inventory and prevents further actions",()->{
                when(gui.plugin.access(gui.player)).thenReturn(false);gui.menu.refresh(gui.player);verify(gui.player).closeInventory();
            });
        }
        System.out.println("RESULT " + checks + " journey update checks passed");
    }
}
