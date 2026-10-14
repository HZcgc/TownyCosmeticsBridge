package net.townyreborn.journey;

import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.inventory.meta.SkullMeta;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.*;

/** Safety checks for the authorised points restart, UTC days, long curve and client feedback. */
public final class LongJourneyChecks {
    static int checks; static Journey journey; static YamlConfiguration config; static Path root;
    interface Check { void run() throws Exception; }
    static void test(String name, Check body) throws Exception { body.run(); checks++; System.out.println("PASS " + name); }
    static void require(boolean ok) { if (!ok) throw new AssertionError("Long Journey regression"); }
    static YamlConfiguration oldProfile(UUID id) {
        YamlConfiguration c=new YamlConfiguration();c.set("schema",2);c.set("uuid",id.toString());c.set("name","TestPlayer");
        c.set("points",100000);c.set("active-minutes",456);c.set("owned-perks",List.of("homes30","fly"));
        c.set("claimed-gifts",List.of("vanilla_xp_1","daily_key_5"));c.set("high-water.mcmmo",500);c.set("high-water.job:Miner",50);
        c.set("daily-awards.fishing",120);c.set("day",DailyTime.today());c.set("achievements",List.of("biome:minecraft:plains"));return c;
    }
    static Path writeOld(String dir, UUID id, YamlConfiguration c) throws Exception {
        Path file=root.resolve(dir+"/journeydata/"+id+".yml");Files.createDirectories(file.getParent());Files.writeString(file,"# original profile\n"+c.saveToString());return file;
    }
    static Store store(String dir) throws Exception { return new Store(root.resolve(dir),journey); }
    static RebornPlugin plugin(Store store, Player player, YamlConfiguration c) throws Exception {
        RebornPlugin p=mock(RebornPlugin.class,CALLS_REAL_METHODS);p.journey=journey;p.store=store;
        doReturn(c).when(p).getConfig();doReturn(Logger.getAnonymousLogger()).when(p).getLogger();
        doReturn(true).when(p).access(player);doAnswer(a->store.get(player.getUniqueId(),player.getName())).when(p).state(player);return p;
    }
    static Player player() {
        Player p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());when(p.getName()).thenReturn("TestPlayer");
        when(p.isOnline()).thenReturn(true);when(p.getGameMode()).thenReturn(GameMode.SURVIVAL);
        World world=mock(World.class);when(world.getName()).thenReturn("earth");when(p.getWorld()).thenReturn(world);
        when(p.getLocation()).thenReturn(new Location(world,0,64,0));return p;
    }
    public static void main(String[] args) throws Exception {
        root=Path.of(args[0]);Files.createDirectories(root);config=JourneyUpdateChecks.readJar(args[1]);journey=new Journey(config);
        test("116 distinct level titles and compact level IDs",()->{
            Set<String> names=new HashSet<>(),shorts=new HashSet<>();
            for(int l=1;l<=116;l++)require(names.add(journey.title(l))&&shorts.add(journey.abbreviation(l)));
            require(names.size()==116&&journey.title(1).equals("Newcomer")&&journey.title(116).equals("Reborn Legend"));
        });
        test("maximum permitted earnings require the 100th UTC day",()->{
            State s=new State(UUID.randomUUID(),"Test");long cap=0;
            for(String id:config.getConfigurationSection("sources").getKeys(false))
                if(config.isConfigurationSection("sources."+id)&&config.getBoolean("sources."+id+".enabled"))cap+=config.getLong("sources."+id+".daily-cap");
            require(cap==2160&&journey.threshold(116)==213900&&99*cap<journey.threshold(116)&&100*cap>=journey.threshold(116));
            for(int day=1;day<=99;day++)for(String id:List.of("playtime","mcmmo","jobs","fishing","exploration","advancements"))
                s.capped("day"+day,id,100000,config.getLong("sources."+id+".daily-cap"));
            require(s.points==213840&&journey.level(s.points)==115);
            s.capped("day100","playtime",60,360);require(journey.level(s.points)==116);
        });
        test("UTC midnight ignores JVM timezone and Berlin daylight-saving time",()->{
            Clock before=Clock.fixed(Instant.parse("2026-10-14T23:59:59Z"),ZoneId.of("Europe/Berlin"));
            Clock after=Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"),ZoneId.of("Pacific/Honolulu"));
            require(DailyTime.today(before).equals("2026-10-14")&&DailyTime.today(after).equals("2026-10-15"));
            State s=new State(UUID.randomUUID(),"Test");require(s.capped(DailyTime.today(before),"playtime",400,360)==360);
            require(s.capped(DailyTime.today(before),"playtime",1,360)==0&&s.capped(DailyTime.today(after),"playtime",1,360)==1&&s.points==361);
        });
        test("fresh profiles begin at level one without writing a read-only record",()->{
            State s=store("fresh").get(UUID.randomUUID(),"Test");require(s.points==0&&s.journeyMinutes==0&&s.progressRevision.equals("long-journey-2026-10-v1")&&!s.persisted);
        });
        test("native reset keeps purchases claims limits achievements and exact backup",()->{
            UUID id=UUID.randomUUID();Path file=writeOld("native",id,oldProfile(id));String old=Files.readString(file);
            Store store=store("native");State s=store.get(id,"Test");
            require(s.points==0&&s.journeyMinutes==0&&s.minutes==456&&s.owned.containsAll(List.of("homes8","homes30","fly")));
            require(s.claimed.containsAll(List.of("milestone_001","milestone_005"))&&s.high.get("mcmmo")==500&&s.daily.get("fishing")==120&&s.achievements.contains("biome:minecraft:plains"));
            Path backup=root.resolve("native/migration-backups/long-journey-2026-10-v1/journeydata/"+id+".yml");require(Files.readString(backup).equals(old));
            s.points=333;s.journeyMinutes=7;store.save(s);State again=new Store(root.resolve("native"),journey).get(id,"Test");
            require(again.points==333&&again.journeyMinutes==7&&Files.readString(backup).equals(old));
        });
        test("legacy quests and active time cannot pre-fill the restarted Journey",()->{
            UUID id=UUID.randomUUID();Path legacy=root.resolve("legacy/playerdata/"+id+".yml");Files.createDirectories(legacy.getParent());
            String old="level: 5\nactive-minutes: 50000\nquest-points: 800\n";Files.writeString(legacy,old);
            State s=store("legacy").get(id,"Test");require(s.points==0&&s.minutes==50000&&s.journeyMinutes==0&&Files.readString(legacy).equals(old));
            require(Files.readString(root.resolve("legacy/migration-backups/long-journey-2026-10-v1/playerdata/"+id+".yml")).equals(old));
        });
        test("backup failure leaves old progress intact and no partially migrated cache",()->{
            UUID id=UUID.randomUUID();Path file=writeOld("backup-fail",id,oldProfile(id));String old=Files.readString(file);
            Files.writeString(root.resolve("backup-fail/migration-backups"),"cannot be a directory");Store s=store("backup-fail");
            boolean failed=false;try{s.get(id,"Test");}catch(IOException expected){failed=true;}
            require(failed&&s.cached().isEmpty()&&Files.readString(file).equals(old));
        });
        test("migration save failure is retryable and cannot discard the original profile",()->{
            UUID id=UUID.randomUUID();Path file=writeOld("save-fail",id,oldProfile(id));String old=Files.readString(file);Store s=spy(store("save-fail"));
            doThrow(new IOException("disk full")).when(s).save(any(State.class));boolean failed=false;try{s.get(id,"Test");}catch(IOException expected){failed=true;}
            require(failed&&s.cached().isEmpty()&&Files.readString(file).equals(old));
            State retry=store("save-fail").get(id,"Test");require(retry.points==0&&retry.owned.contains("fly"));
        });
        test("new first skill readings cannot import pre-update levels or farm old records",()->{
            UUID id=UUID.randomUUID();writeOld("baseline",id,oldProfile(id));Store st=store("baseline");State s=st.get(id,"Test");
            require(s.advanceHighWater("mcmmo",-1,5)==0&&s.baselineDue.contains("mcmmo"));
            require(s.advanceHighWater("mcmmo",540,5)==0&&s.advanceHighWater("mcmmo",541,5)==5);
            require(s.advanceHighWater("job:Miner",55,12)==0&&s.advanceHighWater("job:Miner",56,12)==12);
            st.save(s);State again=store("baseline").get(id,"Test");require(again.advanceHighWater("mcmmo",541,5)==0&&again.advanceHighWater("job:Miner",1,12)==0);
        });
        test("a debited flight receipt recovers at level one without another debit",()->{
            UUID id=UUID.randomUUID();var c=oldProfile(id);c.set("owned-perks",List.of("homes8"));c.set("pending-purchase.perk","fly");
            c.set("pending-purchase.phase","DEBITED");c.set("pending-purchase.price",200000);c.set("pending-purchase.balance-before",250000);c.set("pending-purchase.created",100);
            writeOld("paid",id,c);Store st=store("paid");State s=st.get(id,"Test");require(s.points==0&&s.pending.price()==200000);
            RecoveryChecks.Bank bank=new RecoveryChecks.Bank();Purchases p=new Purchases(journey,st,bank);p.recover(s,x->{});p.recover(s,x->{});
            bank.future.complete(null);require(bank.debits==0&&bank.grants==1&&s.owned.contains("fly")&&s.pending==null);
        });
        test("larger reward amounts cannot replay old gift claims or ambiguous receipts",()->{
            UUID id=UUID.randomUUID();var c=oldProfile(id);c.set("pending-gift","vanilla_xp_9");c.set("pending-gift-money",950);c.set("pending-gift-commands",List.of("original command"));
            writeOld("old-gift",id,c);Store st=store("old-gift");State s=st.get(id,"Test");require(s.points==0&&s.pendingGift.equals("milestone_009")&&s.pendingGiftMoney==950);
            JourneyUpdateChecks.Rewards bank=new JourneyUpdateChecks.Rewards();GiftClaims gifts=new GiftClaims(journey,st,bank);
            gifts.claim(s,journey.gifts.get("milestone_001"),"",x->{});gifts.claim(s,journey.gifts.get("milestone_009"),"",x->{});
            require(bank.grants==0&&bank.deposits==0&&bank.commands==0&&s.pendingGiftCommands.equals(List.of("original command")));
        });
        test("flight needs the final level for a new purchase; owned flight remains owned",()->{
            State s=new State(UUID.randomUUID(),"Test");s.points=journey.threshold(115);require(!journey.purchasable(s,journey.perks.get("fly")));
            s.points=journey.threshold(116);require(journey.purchasable(s,journey.perks.get("fly")));s.owned.add("fly");s.points=0;
            require(s.owned.contains("fly")&&!journey.purchasable(s,journey.perks.get("fly")));
        });
        test("rank colours move pink to lime and both render formats preserve titles",()->{
            require(journey.rankStyle.colour(1,116)==0xff6bd6&&journey.rankStyle.colour(116,116)==0xb8ff60);
            for(int l=1;l<=116;l++){
                String plain=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(
                    net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(journey.miniTitle(l)));
                require(plain.equals(journey.title(l))&&ChatColor.stripColor(journey.styledTitle(l)).equals(journey.title(l)));
            }
        });
        test("reward value rises without recurring keys or cosmetic rewards",()->{
            require(journey.gifts.get("milestone_001").money()==2000&&journey.gifts.get("milestone_116").money()==100000);
            require(journey.gifts.get("milestone_116").commands().equals(List.of("cratekey give %player% patron 5")));
            require(journey.gifts.get("milestone_105").townBlocks()==8);
        });
        test("Experience category occupies the stone slot and preserves screenshot positions",()->{
            var q=JourneyUpdateChecks.read(Files.readString(Path.of(args[2]).resolve("ui/Quests-config.yml")));
            var cats=JourneyUpdateChecks.read(Files.readString(Path.of(args[2]).resolve("ui/Quests-categories.yml")));
            List<Integer> free=new ArrayList<>();for(int slot=0;slot<54;slot++)if(!q.contains("custom-elements.categories."+slot))free.add(slot);
            List<String> ids=new ArrayList<>(cats.getConfigurationSection("categories").getKeys(false));
            require(free.size()==17&&free.get(ids.indexOf("expquests"))==40&&free.get(ids.indexOf("misc"))==32&&free.get(ids.indexOf("impossible"))==33);
            require(q.getString("custom-elements.categories.31.display.type").equals("BLACK_STAINED_GLASS_PANE")&&q.contains("custom-elements.categories.51.commands"));
        });
        test("successful level gain saves before sound and sends the new-rank notification",()->{
            Player player=player();Store st=store("notify");RebornPlugin p=plugin(st,player,config);ProgressSources sources=new ProgressSources(p);
            doAnswer(a->{State saved=new Store(root.resolve("notify"),journey).get(player.getUniqueId(),"Test");require(saved.points==150);return null;})
                .when(player).playSound(any(Location.class),anyString(),anyFloat(),anyFloat());
            sources.award(player,"playtime",149,360);verify(player,never()).sendTitle(anyString(),anyString(),anyInt(),anyInt(),anyInt());
            sources.award(player,"playtime",1,360);verify(player).playSound(any(Location.class),eq("minecraft:ui.toast.challenge_complete"),eq(1.0F),eq(1.0F));
            verify(player).sendTitle(anyString(),contains("Level 2"),eq(10),eq(70),eq(20));verify(player).sendMessage(contains("You reached"));
            sources.award(player,"playtime",0,360);require(st.get(player.getUniqueId(),"Test").points==150);
        });
        test("failed level save sends no success sound or level notification",()->{
            Player player=player();Store st=spy(store("notify-fail"));RebornPlugin p=plugin(st,player,config);doNothing().when(p).profileError(any(Player.class),any(Exception.class));
            doThrow(new IOException("disk full")).when(st).save(any(State.class));new ProgressSources(p).award(player,"playtime",150,360);
            verify(player,never()).playSound(any(Location.class),anyString(),anyFloat(),anyFloat());verify(p).profileError(eq(player),any(IOException.class));
        });
        JourneyUpdateChecks.root=root.resolve("ui");JourneyUpdateChecks.config=config;JourneyUpdateChecks.journey=journey;
        try(var gui=new JourneyUpdateChecks.Gui()) {
            test("player head shows live personal stats while progress bottle stays focused",()->{
                gui.state().points=75;gui.menu.journey(gui.player,0);require(gui.tile(4).icon()==Material.PLAYER_HEAD);
                require(gui.text(4).contains("mcMMO power level: &f300")&&gui.text(4).contains("Miner: &f15")&&gui.text(4).contains("Fish caught: &f123")&&gui.text(4).contains("Balance:"));
                require(gui.text(2).contains("50%")&&!gui.text(2).contains("Fish caught"));
            });
            test("rank tabs use a Blaze head and explicit browsing instructions",()->{
                gui.menu.perks(gui.player,0,0);require(gui.tile(2).icon()==Material.PLAYER_HEAD&&gui.text(2).contains("Click this head")&&gui.text(6).contains("five heads above are clickable"));
                for(int i=0;i<5;i++)require(gui.view().actions.get(i).page()==i);
            });
            test("Blaze tab sends a fixed Mojang skin profile rather than a Blaze rod",()->{
                SkullMeta skull=mock(SkullMeta.class);PlayerProfile profile=mock(PlayerProfile.class);PlayerTextures textures=mock(PlayerTextures.class);when(profile.getTextures()).thenReturn(textures);
                gui.bukkit.when(()->Bukkit.createPlayerProfile(any(UUID.class))).thenReturn(profile);
                doAnswer(a->{var stack=mock(org.bukkit.inventory.ItemStack.class);when(stack.getItemMeta()).thenReturn(skull);
                    gui.tiles.put(stack,new JourneyUpdateChecks.Tile(a.getArgument(0),a.getArgument(1),List.copyOf(a.getArgument(2))));return stack;})
                    .when(gui.menu).item(eq(Material.PLAYER_HEAD),anyString(),anyList());
                gui.menu.perks(gui.player,2,0);verify(skull).setOwnerProfile(profile);
                verify(textures).setSkin(argThat(url->url.toString().equals("https://textures.minecraft.net/texture/d06e342f90ec538aaa1552b224f264a040840902e126d91ece6139aa5b3c7cc3")));
            });
        }
        System.out.println("RESULT "+checks+" long Journey checks passed");
    }
}
