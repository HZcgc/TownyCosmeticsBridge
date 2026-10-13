package net.townyreborn.journey;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.configuration.file.YamlConfiguration;

/** Run against both the preserved release and the source rebuild, with identical inputs. */
public final class RecoveryChecks {
    static Journey journey;static Path root;static int checks;
    interface Test {void run() throws Exception;}
    static void test(String name,Test test) throws Exception {test.run();checks++;System.out.println("PASS "+name);}
    static void require(boolean condition){if(!condition)throw new AssertionError("Recovery regression");}
    static void reject(Test test) throws Exception {try{test.run();}catch(Exception expected){return;}throw new AssertionError("Expected failure");}
    static Store store(String name) throws Exception {return new Store(root.resolve(name),journey);}
    static State ready(Store s) throws Exception {State state=s.get(UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"),"TestPlayer");state.points=journey.threshold(116);return state;}
    static class Bank implements Purchases.Backend {
        double balance=1000000;int debits,grants;boolean uncertain,declined;CompletableFuture<Void> future=new CompletableFuture<>();
        public double balance(UUID id){return balance;}
        public boolean withdraw(UUID id,double amount){debits++;if(uncertain)throw new IllegalStateException("uncertain");if(declined)return false;balance-=amount;return true;}
        public CompletableFuture<Void> grant(UUID id,List<String> nodes){grants++;return future;}
        public void main(Runnable task){task.run();}
    }
    static class Rewards implements GiftClaims.Backend {
        int grants,commands,blocks;String town="";boolean fail;CompletableFuture<Void> future=new CompletableFuture<>();
        public CompletableFuture<Void> grant(UUID id,List<String> nodes){grants++;return future;}
        public boolean dispatch(String command){commands++;return !fail;}
        public void townBonus(String name,int number){town=name;blocks+=number;}
        public void main(Runnable task){task.run();}
    }
    public static void main(String[] args) throws Exception {
        root=Path.of(args[0]);Files.createDirectories(root);
        YamlConfiguration config=new YamlConfiguration();config.loadFromString(Files.readString(Path.of(args[1])));journey=new Journey(config);
        var fly=journey.perks.get("fly");var key=journey.gifts.get("daily_key_5");
        test("default catalogue, pages and early flight",()->require(journey.steps==116&&journey.pages()==4&&journey.perks.size()==44&&journey.gifts.size()==116&&fly.level()==18));
        test("all level boundaries and cap",()->{for(int level=1;level<=116;level++){require(journey.level(journey.threshold(level))==level);if(level>1)require(journey.level(journey.threshold(level)-1)==level-1);}require(journey.level(Long.MAX_VALUE)==116);});
        test("snake is connected and unique",()->{Set<Integer> slots=new HashSet<>();int last=-1;for(int slot:Journey.PATH){require(slot>=9&&slot<54&&slots.add(slot));if(last>=0)require(Math.abs(last/9-slot/9)+Math.abs(last%9-slot%9)==1);last=slot;}require(slots.size()==29);});
        test("free level progression",()->{State state=ready(store("free"));require(state.owned.isEmpty()&&journey.level(state.points)==116);});
        test("permanent tier closure",()->{Set<String> owned=new HashSet<>(List.of("homes30","name_style"));journey.inheritLowerPerks(owned);require(owned.containsAll(List.of("homes8","homes12","homes16","homes22","name_color","name_gradient")));});
        test("legacy point mapping",()->{require(journey.legacyPoints(1,100,10)==350);require(journey.legacyPoints(4,0,0)==journey.threshold(36));});
        test("read-only profile has no persisted player file",()->{Store s=store("readonly");State state=ready(s);try(var paths=Files.list(root.resolve("readonly/journeydata"))){require(paths.count()==0&&!state.persisted);}});
        test("native state round trip",()->{Store s=store("roundtrip");State state=ready(s);state.owned.add("fly");state.minutes=345;s.save(state);State loaded=new Store(root.resolve("roundtrip"),journey).get(state.uuid,"TestPlayer");require(loaded.points==state.points&&loaded.minutes==345&&loaded.owned.contains("fly"));});
        test("corrupt profiles fail closed",()->{Store s=store("corrupt");UUID id=UUID.randomUUID();Path p=root.resolve("corrupt/journeydata/"+id+".yml");Files.writeString(p,"schema: [");reject(()->s.get(id,"Test"));require(Files.readString(p).equals("schema: ["));});
        test("high-water and job rejoin protection",()->{State state=ready(store("high"));require(state.advanceHighWater("job:Miner",10,12)==0&&state.advanceHighWater("job:Miner",12,12)==24&&state.advanceHighWater("job:Miner",1,12)==0&&state.advanceHighWater("job:Miner",12,12)==0);});
        test("daily limits",()->{State state=new State(UUID.randomUUID(),"Test");require(state.capped("A","fish",500,300)==300&&state.capped("A","fish",20,300)==0&&state.capped("B","fish",3,300)==3&&state.points==303);});
        test("purchase and duplicate click",()->{Store s=store("buy");State state=ready(s);Bank b=new Bank();Purchases p=new Purchases(journey,s,b);long points=state.points;p.buy(state,fly,x->{});p.buy(state,fly,x->{});require(b.debits==1&&state.pending.phase().equals("DEBITED"));b.future.complete(null);require(state.pending==null&&state.owned.contains("fly")&&state.points==points);});
        test("in-flight receipt cannot be cleared",()->{Store s=store("inflight");State state=ready(s);Bank b=new Bank();Purchases p=new Purchases(journey,s,b);p.buy(state,fly,x->{});reject(()->p.resolve(state,false,x->{}));require(state.pending!=null);});
        test("insufficient funds and prerequisite do not charge",()->{Store s=store("denied");State state=ready(s);Bank b=new Bank();Purchases p=new Purchases(journey,s,b);b.balance=1;p.buy(state,fly,x->{});b.balance=1000000;p.buy(state,journey.perks.get("homes30"),x->{});require(b.debits==0);});
        test("declined payment grants nothing",()->{Store s=store("declined");State state=ready(s);Bank b=new Bank();b.declined=true;new Purchases(journey,s,b).buy(state,fly,x->{});require(state.pending==null&&b.grants==0&&state.owned.isEmpty());});
        test("uncertain payment stays PREPARED and never repeats",()->{Store s=store("uncertain");State state=ready(s);Bank b=new Bank();b.uncertain=true;Purchases p=new Purchases(journey,s,b);p.buy(state,fly,x->{});require(state.pending.phase().equals("PREPARED")&&!state.blocked);State loaded=new Store(root.resolve("uncertain"),journey).get(state.uuid,"Test");p.recover(loaded,x->{});p.buy(loaded,fly,x->{});require(b.debits==1&&b.grants==0);});
        test("paid restart recovery does not debit",()->{Store s=store("restart");State state=ready(s);Bank b=new Bank();new Purchases(journey,s,b).buy(state,fly,x->{});Store reload=new Store(root.resolve("restart"),journey);State loaded=reload.get(state.uuid,"Test");Bank recovery=new Bank();Purchases p=new Purchases(journey,reload,recovery);p.recover(loaded,x->{});p.recover(loaded,x->{});require(recovery.debits==0&&recovery.grants==1);recovery.future.complete(null);require(loaded.owned.contains("fly"));});
        test("LP failure retains paid recovery receipt",()->{Store s=store("lpfailed");State state=ready(s);Bank b=new Bank();Purchases p=new Purchases(journey,s,b);p.buy(state,fly,x->{});b.future.completeExceptionally(new IllegalStateException("LP"));require(state.pending.phase().equals("DEBITED"));b.future=new CompletableFuture<>();p.recover(state,x->{});b.future.complete(null);require(b.debits==1&&state.owned.contains("fly"));});
        test("free key grant waits for durable LP save",()->{Store s=store("key");State state=ready(s);Rewards b=new Rewards();GiftClaims p=new GiftClaims(journey,s,b);p.claim(state,key,"",x->{});p.claim(state,key,"",x->{});require(b.commands==0&&b.grants==1);b.future.complete(null);p.claim(state,key,"",x->{});require(b.commands==1&&state.claimed.contains(key.id()));});
        test("failed reward cannot replay after restart",()->{Store s=store("keyfailed");State state=ready(s);Rewards b=new Rewards();b.fail=true;GiftClaims p=new GiftClaims(journey,s,b);p.claim(state,key,"",x->{});b.future.complete(null);require(state.pendingGift.equals(key.id()));Store reload=new Store(root.resolve("keyfailed"),journey);State loaded=reload.get(state.uuid,"Test");p.claim(loaded,key,"",x->{});require(b.commands==1&&loaded.pendingGiftCommands.get(0).contains("TestPlayer"));p.delivered(loaded);p.claim(loaded,key,"",x->{});require(b.commands==1);});
        test("reward receipt serializes purchases",()->{Store s=store("combined");State state=ready(s);Rewards b=new Rewards();new GiftClaims(journey,s,b).claim(state,key,"",x->{});Bank wallet=new Bank();new Purchases(journey,s,wallet).buy(state,fly,x->{});require(wallet.debits==0);});
        test("town claim can be deferred and cannot be farmed by changing town",()->{Store s=store("town");State state=ready(s);Rewards b=new Rewards();GiftClaims p=new GiftClaims(journey,s,b);var gift=journey.gifts.get("town_claims_25");p.claim(state,gift,"",x->{});require(b.grants==0);p.claim(state,gift,"OriginalTown",x->{});b.future.complete(null);p.claim(state,gift,"OtherTown",x->{});require(b.blocks==2&&b.town.equals("OriginalTown"));});
        test("in-flight reward cannot be manually closed",()->{Store s=store("rewardflight");State state=ready(s);GiftClaims p=new GiftClaims(journey,s,new Rewards());p.claim(state,key,"",x->{});reject(()->p.delivered(state));});
        test("command subjects are validated",()->{Store s=store("name");State state=ready(s);state.name="name injected";Rewards b=new Rewards();new GiftClaims(journey,s,b).claim(state,key,"",x->{});require(b.grants==0&&b.commands==0);});
        System.out.println("RESULT "+checks+" recovery checks passed");
    }
}
