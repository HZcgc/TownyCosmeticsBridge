package net.townyreborn.journey;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import org.bukkit.configuration.file.YamlConfiguration;
import static org.mockito.Mockito.*;

/** Check historical credit amounts, durable receipts, failures and replay protection. */
public final class HistoryCreditChecks {
    static int checks; static Journey journey; static YamlConfiguration config; static Path root;
    interface Check { void run() throws Exception; }
    static void test(String name, Check body) throws Exception { body.run(); checks++; System.out.println("PASS " + name); }
    static void require(boolean ok) { if(!ok)throw new AssertionError("Historical progress regression"); }
    static final String RESET="long-journey-2026-10-v1", CREDIT="pre-reset-points-2026-10-v1";
    static YamlConfiguration nativeProfile(UUID id,long points,String revision,long minutes,long currentMinutes){
        var c=new YamlConfiguration();c.set("schema",2);c.set("uuid",id.toString());c.set("name","TestPlayer");c.set("points",points);
        c.set("active-minutes",minutes);c.set("journey-active-minutes",currentMinutes);c.set("progress-revision",revision);return c;
    }
    static YamlConfiguration legacyProfile(int level,long minutes,long quests){
        var c=new YamlConfiguration();c.set("level",level);c.set("active-minutes",minutes);c.set("quest-points",quests);return c;
    }
    static Path write(Path dir,String relative,YamlConfiguration c)throws Exception{
        Path p=dir.resolve(relative);Files.createDirectories(p.getParent());Files.writeString(p,c.saveToString());return p;
    }
    static Path nativeFile(Path d,UUID id){return d.resolve("journeydata/"+id+".yml");}
    static Path receipt(Path d,UUID id){return d.resolve("history-credits/"+id+".yml");}
    static String oldNative(UUID id){return "migration-backups/"+RESET+"/journeydata/"+id+".yml";}
    static String oldLegacy(UUID id){return "migration-backups/"+RESET+"/playerdata/"+id+".yml";}
    static Path dir(String name)throws Exception{Path d=root.resolve(name);Files.createDirectories(d);return d;}
    static State load(Path d,UUID id)throws Exception{return new Store(d,journey).get(id,"TestPlayer");}
    static void fails(Check body)throws Exception{boolean fail=false;try{body.run();}catch(IOException|IllegalArgumentException expected){fail=true;}require(fail);}
    static void expectUntouched(Path d,UUID id,String original)throws Exception{
        Store st=new Store(d,journey);fails(()->st.get(id,"TestPlayer"));require(st.cached().isEmpty()&&Files.readString(nativeFile(d,id)).equals(original));
    }
    static Path setup(String name,UUID id)throws Exception{
        Path d=dir(name);write(d,"journeydata/"+id+".yml",nativeProfile(id,4,RESET,4674,4));
        write(d,oldNative(id),nativeProfile(id,5052,"",4670,4670));return d;
    }
    public static void main(String[] args)throws Exception{
        root=Path.of(args[0]);Files.createDirectories(root);config=JourneyUpdateChecks.readJar(args[1]);journey=new Journey(config);
        test("history credit is enabled without changing the reset revision or progression curve",()->{
            require(journey.historyCreditRevision.equals(CREDIT)&&journey.progressRevision.equals(RESET)&&journey.threshold(116)==213900);
        });
        test("5052 pre-reset JP plus 4 new JP become level 15 with restored active time",()->{
            UUID id=UUID.randomUUID();Path d=setup("native",id);String current=Files.readString(nativeFile(d,id));State s=load(d,id);
            require(s.points==5056&&journey.level(s.points)==15&&s.journeyMinutes==4674&&s.minutes==4674&&s.historyCreditPoints==5052&&s.historyCreditMinutes==4670);
            require(s.historyCreditRevision.equals(CREDIT)&&s.historyCreditSource.equals("reset-native"));
            require(Files.readString(d.resolve("migration-backups/"+CREDIT+"/journeydata/"+id+".yml")).equals(current));
            require(JourneyUpdateChecks.read(Files.readString(receipt(d,id))).getString("phase").equals("COMPLETED"));
        });
        test("newly encountered pre-reset native profile is converted without losing its earned JP",()->{
            UUID id=UUID.randomUUID();Path d=dir("unreset");var old=nativeProfile(id,5052,"",4670,4670);String original=old.saveToString();write(d,"journeydata/"+id+".yml",old);
            State s=load(d,id);require(s.points==5052&&s.journeyMinutes==4670&&s.progressRevision.equals(RESET)&&Files.readString(d.resolve(oldNative(id))).equals(original));
        });
        test("legacy rank minutes and quests use the frozen old conversion before the new curve",()->{
            UUID id=UUID.randomUUID();Path d=dir("legacy");var old=legacyProfile(4,6596,541);Path original=write(d,"playerdata/"+id+".yml",old);String text=Files.readString(original);
            State s=load(d,id);require(s.points==20121&&journey.level(s.points)==33&&s.journeyMinutes==6596&&s.historyCreditSource.equals("reset-legacy"));
            require(Files.readString(original).equals(text)&&Files.readString(d.resolve(oldLegacy(id))).equals(text)&&s.owned.isEmpty());
            require(s.unlocked.contains("homes16")&&s.unlocked.contains("fly"));
        });
        test("legacy rank floor is reconstructed on the old curve rather than the expensive new curve",()->{
            UUID id=UUID.randomUUID();Path d=dir("legacy-floor");write(d,"playerdata/"+id+".yml",legacyProfile(4,0,0));State s=load(d,id);
            require(s.points==9975&&journey.level(s.points)==22&&s.historyCreditPoints==9975);
        });
        test("native backup takes precedence over all legacy records and is never summed twice",()->{
            UUID id=UUID.randomUUID();Path d=setup("priority-native",id);write(d,oldLegacy(id),legacyProfile(4,10000,2000));write(d,"playerdata/"+id+".yml",legacyProfile(4,50000,5000));
            require(load(d,id).points==5056);
        });
        test("pre-reset legacy backup takes precedence over the later live legacy file",()->{
            UUID id=UUID.randomUUID();Path d=dir("priority-legacy");write(d,"journeydata/"+id+".yml",nativeProfile(id,7,RESET,6597,1));
            write(d,oldLegacy(id),legacyProfile(4,6596,541));write(d,"playerdata/"+id+".yml",legacyProfile(4,100000,10000));
            State s=load(d,id);require(s.points==20128&&s.historyCreditSource.equals("reset-legacy"));
        });
        test("live legacy file is a documented fallback when reset backups are absent",()->{
            UUID id=UUID.randomUUID();Path d=dir("fallback");write(d,"journeydata/"+id+".yml",nativeProfile(id,7,RESET,1375,1));write(d,"playerdata/"+id+".yml",legacyProfile(1,1374,0));
            State s=load(d,id);require(s.points==1381&&s.historyCreditSource.equals("legacy-fallback")&&s.historyCreditPoints==1374);
        });
        test("fresh players still start at zero and a read-only lookup writes no profile or receipt",()->{
            UUID id=UUID.randomUUID();Path d=dir("fresh");State s=load(d,id);require(s.points==0&&!s.persisted&&!Files.exists(nativeFile(d,id))&&!Files.exists(receipt(d,id)));
        });
        test("current players without old proof keep their points and receive no historical award",()->{
            UUID id=UUID.randomUUID();Path d=dir("no-proof");write(d,"journeydata/"+id+".yml",nativeProfile(id,123,RESET,7,7));State s=load(d,id);
            require(s.points==123&&s.historyCreditPoints==0&&s.historyCreditSource.equals("none"));
        });
        test("points restoration preserves purchased perks claims source caps baselines and pending receipts",()->{
            UUID id=UUID.randomUUID();Path d=setup("receipts",id);var c=nativeProfile(id,4,RESET,4674,4);c.set("owned-perks",List.of("homes12"));c.set("claimed-gifts",List.of("milestone_001","milestone_025"));
            c.set("daily-awards.playtime",17);c.set("daily-awards.fishing",120);c.set("day",DailyTime.today());c.set("high-water.mcmmo",500);c.set("baseline-due",List.of("job:Miner"));c.set("achievements",List.of("biome:minecraft:plains"));
            c.set("pending-purchase.perk","fly");c.set("pending-purchase.phase","DEBITED");c.set("pending-purchase.price",200000);c.set("pending-purchase.balance-before",250000);c.set("pending-purchase.created",100);
            c.set("pending-gift","milestone_009");c.set("pending-gift-money",1000);c.set("pending-gift-town","OriginalTown");c.set("pending-gift-town-blocks",2);c.set("pending-gift-commands",List.of("original reward command"));write(d,"journeydata/"+id+".yml",c);
            State s=load(d,id);require(s.points==5056&&s.owned.equals(Set.of("homes8","homes12"))&&s.claimed.equals(Set.of("milestone_001","milestone_025")));
            require(s.daily.get("playtime")==17&&s.daily.get("fishing")==120&&s.high.get("mcmmo")==500&&s.baselineDue.contains("job:Miner")&&s.achievements.contains("biome:minecraft:plains"));
            require(s.pending.price()==200000&&s.pending.balanceBefore()==250000&&s.pending.created()==100&&s.pendingGiftMoney==1000&&s.pendingGiftTown.equals("OriginalTown")&&s.pendingGiftTownBlocks==2&&s.pendingGiftCommands.equals(List.of("original reward command")));
            var rewards=new JourneyUpdateChecks.Rewards();new GiftClaims(journey,new Store(d,journey),rewards).claim(s,journey.gifts.get("milestone_001"),"",x->{});
            require(rewards.grants==0&&rewards.deposits==0&&rewards.commands==0);
        });
        test("relog restart and repeated installation preserve new earnings without repeating old credit",()->{
            UUID id=UUID.randomUUID();Path d=setup("repeat",id);Store st=new Store(d,journey);State s=st.get(id,"TestPlayer");s.points+=37;s.journeyMinutes+=5;s.minutes+=5;st.save(s);
            for(int i=0;i<3;i++){State again=load(d,id);require(again.points==5093&&again.historyCreditPoints==5052&&again.journeyMinutes==4679);}
        });
        test("restored progress sends a player notice once after its durable save",()->{
            UUID id=UUID.randomUUID();Path d=setup("notice",id);Store st=new Store(d,journey);State s=st.get(id,"TestPlayer");require(s.historyCreditJustApplied);
            RebornPlugin p=mock(RebornPlugin.class,CALLS_REAL_METHODS);p.journey=journey;p.store=st;p.integrations=mock(Integrations.class);
            var active=RebornPlugin.class.getDeclaredField("activated");active.setAccessible(true);active.set(p,new HashSet<UUID>());
            org.bukkit.entity.Player player=mock(org.bukkit.entity.Player.class);when(player.getUniqueId()).thenReturn(id);when(player.getName()).thenReturn("TestPlayer");doReturn(true).when(p).access(player);
            when(p.integrations.importOwned(s)).thenReturn(List.of());p.activate(player,s);p.deactivate(id);p.activate(player,s);
            verify(player,times(1)).sendMessage(contains("Previous progress restored"));require(!s.historyCreditJustApplied&&load(d,id).points==5056);
        });
        test("completed standalone receipt prevents re-crediting after older JAR strips profile markers",()->{
            UUID id=UUID.randomUUID();Path d=setup("downgrade",id);State s=load(d,id);var c=JourneyUpdateChecks.read(Files.readString(nativeFile(d,id)));
            for(String key:List.of("history-credit-revision","history-credit-points","history-credit-minutes","history-credit-source","history-credit-at","progress-revision"))c.set(key,null);
            c.set("points",5069);write(d,"journeydata/"+id+".yml",c);State restored=load(d,id);
            require(restored.points==5069&&restored.historyCreditPoints==5052&&restored.historyCreditRevision.equals(CREDIT));
        });
        test("a changed config credit revision cannot repeat a completed historical grant",()->{
            UUID id=UUID.randomUUID();Path d=setup("changed-revision",id);load(d,id);var c=JourneyUpdateChecks.read(config.saveToString());c.set("journey.history-credit-revision","other-history-credit");
            State s=new Store(d,new Journey(c)).get(id,"TestPlayer");require(s.points==5056&&s.historyCreditRevision.equals(CREDIT));
        });
        test("a stripped new-curve profile is not reset or mistaken for old early-flight eligibility",()->{
            UUID id=UUID.randomUUID();Path d=dir("new-curve-downgrade");write(d,"journeydata/"+id+".yml",nativeProfile(id,5000,RESET,50,50));load(d,id);
            var c=JourneyUpdateChecks.read(Files.readString(nativeFile(d,id)));
            for(String key:List.of("history-credit-revision","history-credit-points","history-credit-minutes","history-credit-source","history-credit-at","progress-revision","unlocked-perks","legacy-perks-imported"))c.set(key,null);
            write(d,"journeydata/"+id+".yml",c);State s=load(d,id);
            require(s.points==5000&&s.historyCreditPoints==0&&!s.unlocked.contains("fly")&&!Files.exists(d.resolve(oldNative(id))));
        });
        test("completed credit with a deleted native profile fails closed rather than reconstructing another grant",()->{
            UUID id=UUID.randomUUID();Path d=setup("missing-profile",id);load(d,id);String proof=Files.readString(receipt(d,id));Files.delete(nativeFile(d,id));
            Store st=new Store(d,journey);fails(()->st.get(id,"TestPlayer"));require(st.cached().isEmpty()&&!Files.exists(nativeFile(d,id))&&Files.readString(receipt(d,id)).equals(proof));
        });
        test("a valid profile credit marker still prevents duplication if the standalone receipt is missing",()->{
            UUID id=UUID.randomUUID();Path d=setup("profile-proof",id);load(d,id);Files.delete(receipt(d,id));require(load(d,id).points==5056);
        });
        test("backup failure leaves current profile untouched and no partially credited cache",()->{
            UUID id=UUID.randomUUID();Path d=setup("backup-fail",id);String before=Files.readString(nativeFile(d,id));Files.writeString(d.resolve("migration-backups/"+CREDIT),"blocked");expectUntouched(d,id,before);
        });
        test("journal preparation failure gives no points and never changes the original profile",()->{
            UUID id=UUID.randomUUID();Path d=setup("prepare-fail",id);String before=Files.readString(nativeFile(d,id));Files.writeString(d.resolve("history-credits"),"blocked");expectUntouched(d,id,before);
        });
        test("failed profile save leaves a prepared receipt that safely retries the exact unchanged profile",()->{
            UUID id=UUID.randomUUID();Path d=setup("save-fail",id);String before=Files.readString(nativeFile(d,id));Store st=spy(new Store(d,journey));doThrow(new IOException("disk full")).when(st).save(any(State.class));
            fails(()->st.get(id,"TestPlayer"));require(st.cached().isEmpty()&&Files.readString(nativeFile(d,id)).equals(before));
            require(JourneyUpdateChecks.read(Files.readString(receipt(d,id))).getString("phase").equals("PREPARED"));require(load(d,id).points==5056&&load(d,id).points==5056);
        });
        test("crash after committed profile but before completed journal never adds the credit again",()->{
            UUID id=UUID.randomUUID();Path d=setup("commit-crash",id);load(d,id);var r=JourneyUpdateChecks.read(Files.readString(receipt(d,id)));r.set("phase","PREPARED");write(d,"history-credits/"+id+".yml",r);
            require(load(d,id).points==5056&&JourneyUpdateChecks.read(Files.readString(receipt(d,id))).getString("phase").equals("COMPLETED"));
        });
        test("changed profile with an uncertain prepared credit fails closed instead of guessing",()->{
            UUID id=UUID.randomUUID();Path d=setup("uncertain",id);Store st=spy(new Store(d,journey));doThrow(new IOException("disk full")).when(st).save(any(State.class));fails(()->st.get(id,"TestPlayer"));
            var c=JourneyUpdateChecks.read(Files.readString(nativeFile(d,id)));c.set("points",8);write(d,"journeydata/"+id+".yml",c);expectUntouched(d,id,c.saveToString());
        });
        test("wrong UUID native backup cannot award historical points",()->{
            UUID id=UUID.randomUUID();Path d=setup("wrong-uuid",id);write(d,oldNative(id),nativeProfile(UUID.randomUUID(),5052,"",4670,4670));expectUntouched(d,id,Files.readString(nativeFile(d,id)));
        });
        test("new-curve or incomplete native backup cannot be interpreted as old earned points",()->{
            UUID id=UUID.randomUUID();Path d=setup("new-curve",id);write(d,oldNative(id),nativeProfile(id,5052,RESET,4670,4));expectUntouched(d,id,Files.readString(nativeFile(d,id)));
            UUID missing=UUID.randomUUID();Path other=setup("missing-old-points",missing);var c=nativeProfile(missing,5052,"",4670,4670);c.set("points",null);write(other,oldNative(missing),c);
            expectUntouched(other,missing,Files.readString(nativeFile(other,missing)));
        });
        test("invalid legacy counters cannot silently create historical progress",()->{
            UUID id=UUID.randomUUID();Path d=dir("bad-legacy");write(d,"journeydata/"+id+".yml",nativeProfile(id,7,RESET,1,1));var old=legacyProfile(4,1000,100);old.set("quest-points","broken");write(d,oldLegacy(id),old);
            expectUntouched(d,id,Files.readString(nativeFile(d,id)));
        });
        test("tampered receipt metadata fails closed without replacing current points",()->{
            UUID id=UUID.randomUUID();Path d=setup("bad-receipt",id);load(d,id);var c=JourneyUpdateChecks.read(Files.readString(receipt(d,id)));c.set("uuid",UUID.randomUUID().toString());write(d,"history-credits/"+id+".yml",c);
            expectUntouched(d,id,Files.readString(nativeFile(d,id)));
        });
        test("credit saturates the point safety limit without overflow or repeatedly adding time",()->{
            UUID id=UUID.randomUUID();Path d=setup("saturation",id);write(d,"journeydata/"+id+".yml",nativeProfile(id,HistoryCredit.LIMIT-2,RESET,4674,4674));State s=load(d,id);
            require(s.points==HistoryCredit.LIMIT&&s.historyCreditPoints==2&&s.historyCreditMinutes==0&&s.journeyMinutes==4674&&load(d,id).points==HistoryCredit.LIMIT);
        });
        if(args.length>2){int index=0;for(JsonElement row:JsonParser.parseString(Files.readString(Path.of(args[2]))).getAsJsonArray()){
            JsonObject c=row.getAsJsonObject();int number=++index;test("anonymous uploaded historical profile "+number+" keeps its expected converted progress",()->{
                UUID id=UUID.randomUUID();Path d=dir("uploaded-"+number);write(d,"playerdata/"+id+".yml",legacyProfile(c.get("legacy_level").getAsInt(),c.get("legacy_minutes").getAsLong(),c.get("legacy_quests").getAsLong()));
                if(c.has("current_points"))write(d,"journeydata/"+id+".yml",nativeProfile(id,c.get("current_points").getAsLong(),RESET,c.get("current_minutes").getAsLong(),c.get("current_journey_minutes").getAsLong()));
                if(c.has("native_points"))write(d,oldNative(id),nativeProfile(id,c.get("native_points").getAsLong(),"",c.get("native_minutes").getAsLong(),c.get("native_minutes").getAsLong()));
                else if(c.get("legacy_backed_up").getAsBoolean())write(d,oldLegacy(id),legacyProfile(c.get("legacy_level").getAsInt(),c.get("legacy_minutes").getAsLong(),c.get("legacy_quests").getAsLong()));
                State s=load(d,id);require(s.points==c.get("expected_total").getAsLong()&&s.historyCreditPoints==c.get("expected_credit").getAsLong()&&journey.level(s.points)==c.get("expected_level").getAsInt());
                require(load(d,id).points==s.points&&s.owned.isEmpty());
            });
        }}
        System.out.println("RESULT "+checks+" historical credit checks passed");
    }
}
