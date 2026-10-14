package net.townyreborn.journey;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.concurrent.CompletableFuture;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.*;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.configuration.file.YamlConfiguration;
import com.leonardobishop.quests.bukkit.api.event.PlayerFinishQuestEvent;
import com.leonardobishop.quests.common.player.questprogressfile.QuestProgress;
import static org.mockito.Mockito.*;

public final class QuestPointsChecks {
    static Path root; static Journey journey; static YamlConfiguration config; static int checks;
    interface Check {void run()throws Exception;}
    static void test(String name,Check action)throws Exception{action.run();checks++;System.out.println("PASS "+name);}
    static void require(boolean value){if(!value)throw new AssertionError("Quest/eGlow regression");}
    static final class Setup {
        final Player player=mock(Player.class);final RebornPlugin plugin=mock(RebornPlugin.class,CALLS_REAL_METHODS);
        final Server server=mock(Server.class);final PluginManager manager=mock(PluginManager.class);
        final Store store;final QuestPoints hook;
        Setup(String name)throws Exception{
            store=new Store(root.resolve(name),journey);plugin.journey=journey;plugin.store=store;
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.getName()).thenReturn("TestPlayer");when(player.isOnline()).thenReturn(true);when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
            World world=mock(World.class);when(world.getName()).thenReturn("earth");when(player.getWorld()).thenReturn(world);
            doReturn(config).when(plugin).getConfig();doReturn(server).when(plugin).getServer();doReturn(Logger.getAnonymousLogger()).when(plugin).getLogger();when(server.getPluginManager()).thenReturn(manager);
            doReturn(true).when(plugin).access(player);doAnswer(a->store.get(player.getUniqueId(),player.getName())).when(plugin).state(player);
            doNothing().when(plugin).activate(any(Player.class),any(State.class));doNothing().when(plugin).levelReached(any(),any(),anyInt());
            doNothing().when(plugin).warnOnce(anyString(),anyString());doNothing().when(plugin).profileError(any(),any());
            plugin.sources=new ProgressSources(plugin);hook=new QuestPoints(plugin);
        }
        State state()throws Exception{return store.get(player.getUniqueId(),player.getName());}
        EventExecutor registered()throws Exception{
            Plugin quests=mock(Plugin.class);when(quests.isEnabled()).thenReturn(true);when(manager.getPlugin("Quests")).thenReturn(quests);
            final EventExecutor[] executor={null};doAnswer(a->{executor[0]=a.getArgument(3);return null;}).when(manager).registerEvent(eq(PlayerFinishQuestEvent.class),any(Listener.class),eq(EventPriority.MONITOR),any(EventExecutor.class),eq(plugin),eq(true));
            hook.register();require(executor[0]!=null);return executor[0];
        }
    }
    public static void main(String[] args)throws Exception{
        root=Path.of(args[0]);Files.createDirectories(root);config=JourneyUpdateChecks.readJar(args[1]);journey=new Journey(config);
        test("Quests enabled at 10 JP with 300 daily JP and unchanged migration revisions",()->{
            require(config.getBoolean("sources.quests.enabled")&&config.getLong("sources.quests.points-per-completion")==10&&config.getLong("sources.quests.daily-cap")==300);
            require(journey.progressRevision.equals("long-journey-2026-10-v1")&&journey.historyCreditRevision.equals("pre-reset-points-2026-10-v1"));
        });
        test("first new quest completion earns JP without an aggregate baseline",()->{
            Setup s=new Setup("first");s.hook.complete(s.player,"daily-mining",100);require(s.state().points==10&&s.state().daily.get("quests")==10&&s.state().questCompletions.size()==1);
        });
        test("repeat completion counts even when the displayed quest count never increases",()->{
            Setup s=new Setup("repeat");s.state().high.put("quests",999L);s.hook.complete(s.player,"daily-mining",100);s.hook.complete(s.player,"daily-mining",200);
            require(s.state().points==20&&s.state().questCompletions.size()==1&&s.state().high.get("quests")==999L);
        });
        test("duplicate and older completion events do not credit twice",()->{
            Setup s=new Setup("duplicate");s.hook.complete(s.player,"daily-mining",200);s.hook.complete(s.player,"daily-mining",200);s.hook.complete(s.player,"daily-mining",100);require(s.state().points==10);
        });
        test("same-time completions of different quest IDs count independently",()->{
            Setup s=new Setup("different");s.hook.complete(s.player,"daily-mining",100);s.hook.complete(s.player,"daily-fishing",100);require(s.state().points==20&&s.state().questCompletions.size()==2);
        });
        test("repeat timestamp receipt persists through a fresh Store and restart",()->{
            Setup s=new Setup("restart");s.hook.complete(s.player,"daily-mining",100);s.plugin.store=new Store(root.resolve("restart"),journey);
            doAnswer(a->s.plugin.store.get(s.player.getUniqueId(),s.player.getName())).when(s.plugin).state(s.player);s.hook.complete(s.player,"daily-mining",100);
            require(s.plugin.state(s.player).points==10&&s.plugin.state(s.player).questCompletions.get(QuestPoints.key("daily-mining"))==100L);
        });
        test("quest IDs with dots and unicode retain stable independent receipts",()->{
            Setup s=new Setup("ids");s.hook.complete(s.player,"daily.mine.a",100);s.hook.complete(s.player,"daily:mine/é",100);
            State stored=new Store(root.resolve("ids"),journey).get(s.player.getUniqueId(),"TestPlayer");require(stored.points==20&&stored.questCompletions.size()==2);
        });
        test("full quest budget records excess completions without queuing tomorrow's JP",()->{
            Setup s=new Setup("cap");for(int i=1;i<=31;i++)s.hook.complete(s.player,"daily-mining",i);require(s.state().points==300&&s.state().questCompletions.get(QuestPoints.key("daily-mining"))==31L);
            s.state().day="previous-day";s.hook.complete(s.player,"daily-mining",31);require(s.state().points==300);
            s.hook.complete(s.player,"daily-mining",32);require(s.state().points==310&&s.state().daily.get("quests")==10);
        });
        test("quest awards preserve purchases delivered gifts and source high-water records",()->{
            Setup s=new Setup("preserve");s.state().owned.add("homes8");s.state().claimed.add("milestone_001");s.state().high.put("mcmmo",500L);s.hook.complete(s.player,"daily-mining",100);
            State stored=new Store(root.resolve("preserve"),journey).get(s.player.getUniqueId(),"TestPlayer");require(stored.owned.contains("homes8")&&stored.claimed.contains("milestone_001")&&stored.high.get("mcmmo")==500L);
        });
        test("blocked offline and inaccessible players earn no quest JP",()->{
            Setup s=new Setup("access");s.state().blocked=true;s.hook.complete(s.player,"q",100);require(s.state().points==0);s.state().blocked=false;
            when(s.player.isOnline()).thenReturn(false);s.hook.complete(s.player,"q",100);require(s.state().points==0);
            when(s.player.isOnline()).thenReturn(true);doReturn(false).when(s.plugin).access(s.player);s.hook.complete(s.player,"q",100);require(s.state().points==0);
        });
        test("Creative and AFK-world completions earn no JP",()->{
            Setup s=new Setup("eligibility");when(s.player.getGameMode()).thenReturn(GameMode.CREATIVE);s.hook.complete(s.player,"q",100);require(s.state().points==0);
            when(s.player.getGameMode()).thenReturn(GameMode.SURVIVAL);when(s.player.getWorld().getName()).thenReturn("afk");s.hook.complete(s.player,"q",100);require(s.state().points==0);
        });
        test("disabled quest source registers no listener and awards nothing",()->{
            Setup s=new Setup("disabled");YamlConfiguration c=JourneyUpdateChecks.read(config.saveToString());c.set("sources.quests.enabled",false);doReturn(c).when(s.plugin).getConfig();s.hook.register();s.hook.complete(s.player,"q",100);
            require(s.state().points==0);verify(s.manager,never()).registerEvent(any(),any(),any(),any(),any(),anyBoolean());
        });
        test("missing Quests plugin is optional and reported once",()->{
            Setup s=new Setup("missing");s.hook.register();verify(s.plugin).warnOnce(eq("quests-points"),anyString());require(s.state().points==0);
        });
        test("registered real API-shaped completion routes to the saved quest award",()->{
            Setup s=new Setup("api");EventExecutor e=s.registered();var q=new QuestProgress(s.player.getUniqueId(),"daily",100);
            try(var b=mockStatic(Bukkit.class)){b.when(Bukkit::isPrimaryThread).thenReturn(true);e.execute(s.hook,new PlayerFinishQuestEvent(s.player,q));}
            require(s.state().points==10);q.completed=false;
            try(var b=mockStatic(Bukkit.class)){b.when(Bukkit::isPrimaryThread).thenReturn(true);e.execute(s.hook,new PlayerFinishQuestEvent(s.player,q));}
            q.completed=true;q.uuid=UUID.randomUUID();try(var b=mockStatic(Bukkit.class)){b.when(Bukkit::isPrimaryThread).thenReturn(true);e.execute(s.hook,new PlayerFinishQuestEvent(s.player,q));}require(s.state().points==10);
        });
        test("async finish snapshots values before the mutable Quests record changes",()->{
            Setup s=new Setup("async");EventExecutor e=s.registered();BukkitScheduler scheduler=mock(BukkitScheduler.class);when(s.server.getScheduler()).thenReturn(scheduler);final Runnable[] task={null};doAnswer(a->{task[0]=a.getArgument(1);return null;}).when(scheduler).runTask(eq(s.plugin),any(Runnable.class));
            var q=new QuestProgress(s.player.getUniqueId(),"original",100);try(var b=mockStatic(Bukkit.class)){b.when(Bukkit::isPrimaryThread).thenReturn(false);e.execute(s.hook,new PlayerFinishQuestEvent(s.player,q));}
            require(s.state().points==0&&task[0]!=null);q.id="changed";q.date=200;task[0].run();require(s.state().points==10&&s.state().questCompletions.containsKey(QuestPoints.key("original")));
        });
        test("failed profile save blocks further awards and sends no premature level notification",()->{
            Setup s=new Setup("failed");s.state().points=140;s.plugin.store=spy(s.store);doThrow(new IOException("disk full")).when(s.plugin.store).save(any());s.hook.complete(s.player,"q",100);
            require(s.state().blocked);verify(s.plugin,never()).levelReached(any(),any(),anyInt());verify(s.plugin).profileError(eq(s.player),any());
            long points=s.state().points;s.hook.complete(s.player,"q",200);require(s.state().points==points);
        });
        test("successful level crossing notifies only after its saved point and completion record",()->{
            Setup s=new Setup("level");s.state().points=140;s.hook.complete(s.player,"q",100);State state=s.state();verify(s.plugin).levelReached(eq(s.player),eq(state),eq(1));
            require(new Store(root.resolve("level"),journey).get(s.player.getUniqueId(),"TestPlayer").points==150);
        });
        test("Wither eGlow costs 20000 at level 88 and grants only personal use",()->{
            var p=journey.perks.get("eglow");require(p.price()==20000&&p.level()==88&&p.tier()==Journey.Tier.WITHER&&p.requires().isEmpty()&&p.legacy().isEmpty());
            require(new HashSet<>(p.permissions()).equals(Set.of("eglow.user.*","eglow.color.*","eglow.blink.*","eglow.effect.*")));
            var wither=journey.sortedPerks(0,Journey.Tier.WITHER);require(wither.get(wither.size()-2).id().equals("eglow")&&wither.get(wither.size()-1).id().equals("fly")&&journey.perks.get("fly").level()==116);
            State s=new State(UUID.randomUUID(),"Test");s.points=journey.threshold(88)-1;require(!journey.purchasable(s,p));s.points=journey.threshold(88);require(journey.purchasable(s,p));
        });
        test("actual eGlow purchase charges 20000 once and keeps JP plus durable ownership",()->{
            Store store=new Store(root.resolve("glow-buy"),journey);State s=store.get(UUID.randomUUID(),"Buyer");s.points=journey.threshold(88);long points=s.points;
            List<String> granted=new ArrayList<>();RecoveryChecks.Bank bank=new RecoveryChecks.Bank(){
                @Override public CompletableFuture<Void> grant(UUID id,List<String> nodes){granted.addAll(nodes);return super.grant(id,nodes);}
            };
            Purchases purchases=new Purchases(journey,store,bank);purchases.buy(s,journey.perks.get("eglow"),x->{});bank.future.complete(null);
            require(bank.balance==980000&&bank.debits==1&&s.owned.contains("eglow")&&s.points==points);
            require(granted.containsAll(journey.perks.get("eglow").permissions())&&granted.contains(Journey.perkMarker("eglow"))&&granted.stream().noneMatch(n->n.equals("eglow.*")||n.startsWith("eglow.admin")||n.startsWith("eglow.command.set")));
            State again=new Store(root.resolve("glow-buy"),journey).get(s.uuid,"Buyer");purchases.buy(again,journey.perks.get("eglow"),x->{});require(bank.debits==1&&again.owned.contains("eglow")&&again.points==points);
        });
        System.out.println("RESULT "+checks+" quest/eGlow checks passed");
    }
}
