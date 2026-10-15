package net.townyreborn.journey;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.BukkitScheduler;
import static org.mockito.Mockito.*;

/** Regression cases found in the release review; all side effects stay in temporary fixtures. */
public final class StableReleaseChecks {
    static int checks, failures; static Path root; static Journey journey; static YamlConfiguration config;
    interface Check { void run() throws Exception; }
    static void test(String name, Check body) {
        try { body.run(); checks++; System.out.println("PASS " + name); }
        catch (Throwable error) { failures++; System.out.println("FAIL " + name + " (" + error.getClass().getSimpleName() + ")"); }
    }
    static void require(boolean value) { if (!value) throw new AssertionError("Stable release regression"); }
    static Store store(String name) throws Exception { return new Store(root.resolve(name), journey); }
    static State fresh(Store store) throws Exception { State state=store.get(UUID.randomUUID(),"TestPlayer"); store.save(state); return state; }
    static Object values(Object value) {
        if (value instanceof ConfigurationSection section) {
            Map<String,Object> result=new TreeMap<>();
            for (String key:section.getKeys(false)) result.put(key,values(section.get(key)));
            return result;
        }
        if (value instanceof List<?> list) return list.stream().map(StableReleaseChecks::values).toList();
        return value;
    }
    static RebornPlugin plugin(Store store, Player player) throws Exception {
        RebornPlugin plugin=LongJourneyChecks.plugin(store,player,config);
        plugin.journey=journey;
        doNothing().when(plugin).profileError(any(Player.class),any(Exception.class));
        doNothing().when(plugin).activate(any(Player.class),any(State.class));
        return plugin;
    }
    static final class Clicks {
        final RebornPlugin plugin=mock(RebornPlugin.class);
        final Player player=LongJourneyChecks.player();
        final Inventory inventory=mock(Inventory.class);
        final InventoryView open=mock(InventoryView.class);
        final Menu.View view=new Menu.View("journey",0,0,"");
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>();
        final Menu menu;
        Clicks() throws Exception {
            view.inventory=inventory; view.actions.put(2,new Menu.Action("guide","",0,0));
            when(inventory.getHolder()).thenReturn(view);
            when(open.getTopInventory()).thenReturn(inventory);when(player.getOpenInventory()).thenReturn(open);
            when(plugin.access(player)).thenReturn(true);when(plugin.isEnabled()).thenReturn(true);
            Server server=mock(Server.class); BukkitScheduler scheduler=mock(BukkitScheduler.class);
            when(plugin.getServer()).thenReturn(server);when(server.getScheduler()).thenReturn(scheduler);
            doAnswer(call->{tasks.add(call.getArgument(1));return null;}).when(scheduler).runTask(eq(plugin),any(Runnable.class));
            menu=spy(new Menu(plugin));doNothing().when(menu).guide(eq(player),anyInt());
        }
        InventoryClickEvent event(ClickType click, int slot) {
            InventoryClickEvent event=mock(InventoryClickEvent.class);
            when(event.getView()).thenReturn(open);when(event.getWhoClicked()).thenReturn(player);
            when(event.getRawSlot()).thenReturn(slot);when(event.getClick()).thenReturn(click);
            return event;
        }
        void tick() { while (!tasks.isEmpty()) tasks.remove().run(); }
    }
    public static void main(String[] args) throws Exception {
        root=Path.of(args[0]);Files.createDirectories(root);config=JourneyUpdateChecks.readJar(args[1]);journey=new Journey(config);
        test("all 116 TAB labels contain their exact level and three unique title letters",()->{
            Set<String> letters=new HashSet<>();
            for (int level=1;level<=116;level++) {
                String label=journey.abbreviation(level);
                require(label.matches(level+"[A-Z]{3}")&&letters.add(label.substring(Integer.toString(level).length())));
            }
            require(journey.abbreviation(1).equals("1NEW")&&journey.abbreviation(116).equals("116RLG"));
        });
        test("actual TAB and chat placeholders retain their distinct compact and full formats",()->{
            Store store=store("placeholders");Player player=LongJourneyChecks.player();RebornPlugin plugin=plugin(store,player);
            RebornExpansion expansion=new RebornExpansion(plugin,"reborn");State state=plugin.state(player);
            for (int level=1;level<=116;level++) {
                state.points=journey.threshold(level);
                require(ChatColor.stripColor(expansion.onPlaceholderRequest(player,"tab_prefix")).equals("["+journey.abbreviation(level)+"] "));
                require(ChatColor.stripColor(expansion.onPlaceholderRequest(player,"chat_prefix")).equals("["+journey.title(level)+"] "));
                require(expansion.onPlaceholderRequest(player,"short_title").equals(journey.abbreviation(level)));
                require(ChatColor.stripColor(expansion.onPlaceholderRequest(player,"short_title_colored")).equals(journey.abbreviation(level)));
                require(expansion.onPlaceholderRequest(player,"title").equals(journey.title(level)));
            }
        });
        test("custom future level fallback retains the level number",()->{
            var future=JourneyUpdateChecks.read(config.saveToString());future.set("journey.steps",117);future.set("journey.titles.117","Reborn Pioneer");
            require(new Journey(future).abbreviation(117).equals("117REB"));
        });
        test("omitted curve options use the current 150 and 30 defaults",()->{
            var minimal=new YamlConfiguration();minimal.set("journey.steps",116);Journey defaults=new Journey(minimal);
            require(defaults.threshold(2)==150&&defaults.threshold(116)==213900);
        });
        test("stable update preserves the complete Beta 9 gameplay catalogue and revision markers",()->{
            var before=JourneyUpdateChecks.readJar(args[2]);var after=JourneyUpdateChecks.read(config.saveToString());
            before.set("journey.short-titles",null);after.set("journey.short-titles",null);
            require(values(before).equals(values(after)));
        });
        test("every level boundary and both sides of each boundary retain the same JP",()->{
            Journey before=new Journey(JourneyUpdateChecks.readJar(args[2]));
            for (int level=1;level<=116;level++) {
                long threshold=before.threshold(level);require(journey.threshold(level)==threshold&&journey.level(threshold)==level);
                if (level>1) require(journey.level(threshold-1)==level-1);
            }
            require(journey.level(Long.MAX_VALUE)==116&&journey.level(0)==1);
        });
        test("failed level-up commit blocks later activity until the profile is saved",()->{
            Store store=spy(store("award-failed"));Player player=LongJourneyChecks.player();RebornPlugin plugin=plugin(store,player);
            State state=plugin.state(player);store.save(state);doThrow(new IOException("disk full")).when(store).save(state);
            ProgressSources sources=new ProgressSources(plugin);sources.award(player,"playtime",150,360);
            require(state.blocked);long points=state.points;sources.award(player,"fishing",3,300);require(state.points==points);
            verify(player,never()).playSound(any(Location.class),anyString(),anyFloat(),anyFloat());
            doCallRealMethod().when(store).save(state);store.save(state);require(!state.blocked);
            require(new Store(root.resolve("award-failed"),journey).get(state.uuid,"TestPlayer").points==150);
        });
        test("client level-up feedback failure cannot block an already saved progression award",()->{
            Store store=store("award-feedback");Player player=LongJourneyChecks.player();RebornPlugin plugin=plugin(store,player);
            State state=plugin.state(player);store.save(state);
            doThrow(new IllegalStateException("disconnected client")).when(player).playSound(any(Location.class),anyString(),anyFloat(),anyFloat());
            ProgressSources sources=new ProgressSources(plugin);sources.award(player,"playtime",150,360);
            require(state.points==150&&!state.blocked&&new Store(root.resolve("award-feedback"),journey).get(state.uuid,"TestPlayer").points==150);
            sources.award(player,"fishing",3,300);require(state.points==153&&!state.blocked);
        });
        test("poll storage failure blocks subsequent progression writes",()->{
            Store store=spy(store("poll-failed"));Player player=LongJourneyChecks.player();RebornPlugin plugin=plugin(store,player);
            var quiet=JourneyUpdateChecks.read(config.saveToString());
            for (String source:List.of("mcmmo","jobs","exploration")) quiet.set("sources."+source+".enabled",false);
            doReturn(quiet).when(plugin).getConfig();Server server=mock(Server.class);doReturn(List.of(player)).when(server).getOnlinePlayers();
            doReturn(server).when(plugin).getServer();plugin.menu=mock(Menu.class);State state=plugin.state(player);state.dirty=true;
            doThrow(new IOException("disk full")).when(store).save(state);
            new ProgressSources(plugin).poll();require(state.blocked);
        });
        test("failed owner point correction cannot leave spendable uncommitted progression",()->{
            Store store=spy(store("admin-failed"));Player player=LongJourneyChecks.player();RebornPlugin plugin=plugin(store,player);
            State state=plugin.state(player);store.save(state);doThrow(new IOException("disk full")).when(store).save(state);
            CommandSender owner=mock(CommandSender.class);when(owner.hasPermission("townyreborn.progression.admin")).thenReturn(true);
            try (var bukkit=mockStatic(Bukkit.class)) {
                bukkit.when(()->Bukkit.getPlayerExact("TestPlayer")).thenReturn(player);
                plugin.admin(owner,new String[]{"addpoints","TestPlayer","150"});require(state.blocked);
            }
        });
        test("failed owner grant review retains PREPARED instead of inventing a DEBITED receipt",()->{
            Store store=spy(store("review-failed"));State state=fresh(store);
            State.Purchase original=new State.Purchase("fly","PREPARED",150000,200000,123456);state.pending=original;store.save(state);
            doThrow(new IOException("disk full")).when(store).save(state);
            RecoveryChecks.Bank bank=new RecoveryChecks.Bank();Purchases purchases=new Purchases(journey,store,bank);
            boolean failed=false;try {purchases.resolve(state,true,ignored->{});}catch(IOException expected){failed=true;}
            require(failed&&state.blocked&&state.pending.equals(original));purchases.recover(state,ignored->{});require(bank.grants==0&&bank.debits==0);
        });
        test("UI feedback failure cannot reopen a successfully saved purchase",()->{
            Store store=store("purchase-feedback");State state=fresh(store);state.points=journey.threshold(116);
            RecoveryChecks.Bank bank=new RecoveryChecks.Bank();Purchases purchases=new Purchases(journey,store,bank);
            purchases.buy(state,journey.perks.get("fly"),ignored->{throw new IllegalStateException("disconnected UI");});
            bank.future.complete(null);require(state.pending==null&&!state.blocked&&state.owned.contains("fly"));
            State loaded=new Store(root.resolve("purchase-feedback"),journey).get(state.uuid,"TestPlayer");
            require(loaded.pending==null&&loaded.owned.contains("fly")&&bank.debits==1);
        });
        test("menu navigation is cancelled immediately and runs only on the next tick",()->{
            Clicks clicks=new Clicks();var event=clicks.event(ClickType.LEFT,2);clicks.menu.click(event);
            verify(event).setCancelled(true);verify(clicks.menu,never()).guide(any(Player.class),anyInt());require(clicks.tasks.size()==1);
            clicks.tick();verify(clicks.menu).guide(clicks.player,0);
        });
        test("multiple clicks in one tick schedule a single menu action",()->{
            Clicks clicks=new Clicks();clicks.menu.click(clicks.event(ClickType.LEFT,2));clicks.menu.click(clicks.event(ClickType.LEFT,2));
            require(clicks.tasks.size()==1);clicks.tick();verify(clicks.menu,times(1)).guide(clicks.player,0);
        });
        test("closing or replacing the menu discards a delayed click",()->{
            Clicks clicks=new Clicks();clicks.menu.click(clicks.event(ClickType.LEFT,2));
            when(clicks.open.getTopInventory()).thenReturn(mock(Inventory.class));clicks.tick();verify(clicks.menu,never()).guide(any(Player.class),anyInt());
        });
        test("disconnecting discards delayed menu actions",()->{
            Clicks clicks=new Clicks();clicks.menu.click(clicks.event(ClickType.LEFT,2));when(clicks.player.isOnline()).thenReturn(false);
            clicks.tick();verify(clicks.menu,never()).guide(any(Player.class),anyInt());
        });
        test("permission removal between click and next tick prevents the action",()->{
            Clicks clicks=new Clicks();clicks.menu.click(clicks.event(ClickType.LEFT,2));when(clicks.plugin.access(clicks.player)).thenReturn(false);
            clicks.tick();verify(clicks.menu,never()).guide(any(Player.class),anyInt());verify(clicks.player).closeInventory();
        });
        test("number keys drop keys middle clicks and offhand swaps cannot activate a menu purchase",()->{
            for (ClickType type:List.of(ClickType.NUMBER_KEY,ClickType.DROP,ClickType.CONTROL_DROP,ClickType.MIDDLE,ClickType.SWAP_OFFHAND,ClickType.DOUBLE_CLICK)) {
                Clicks clicks=new Clicks();var event=clicks.event(type,2);clicks.menu.click(event);verify(event).setCancelled(true);
                require(clicks.tasks.isEmpty());verify(clicks.menu,never()).guide(any(Player.class),anyInt());
            }
        });
        test("a changed slot action cannot execute from a stale menu click",()->{
            Clicks clicks=new Clicks();clicks.menu.click(clicks.event(ClickType.LEFT,2));clicks.view.actions.put(2,new Menu.Action("guide","",1,0));
            clicks.tick();verify(clicks.menu,never()).guide(any(Player.class),anyInt());
        });
        if (failures>0) throw new AssertionError(failures+" stable release checks failed; "+checks+" passed");
        System.out.println("RESULT "+checks+" stable release checks passed");
    }
}
