package net.townyreborn.journey;

import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

public final class StaffAuditChecks {
    private static int checks;
    interface Check { void run() throws Exception; }
    static void test(String name, Check check) throws Exception { check.run();checks++;System.out.println("PASS "+name); }
    static void require(boolean value) { if(!value)throw new AssertionError("Staff audit regression"); }
    static YamlConfiguration read(Path path) throws Exception { YamlConfiguration y=new YamlConfiguration();y.loadFromString(Files.readString(path));return y; }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);Files.createDirectories(root);YamlConfiguration config=read(Path.of(args[1]));Journey journey=new Journey(config);
        UUID uuid=UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");Path folder=root.resolve("normal");Store store=new Store(folder,journey);State state=store.get(uuid,"FixturePlayer");
        Path profile=folder.resolve("journeydata/"+uuid+".yml"),audit=folder.resolve("staff-audit/"+uuid);
        test("initial committed state has a stable receipt and persistent sequence",()->{store.save(state);require(read(profile).getLong("staff-audit-sequence")==1);require(read(audit.resolve("1.yml")).getString("event-id").equals("journey-"+uuid+"-1"));});
        test("unchanged save cannot create another earned-activity receipt",()->{store.save(state);require(!Files.exists(audit.resolve("2.yml")));require(read(profile).getLong("staff-audit-sequence")==1);});
        test("actual capped quest JP commit records both states and its source",()->{state.capped("2026-10-14","quests",10,300);store.save(state);var row=read(audit.resolve("2.yml"));require(row.getLong("before.points")==0&&row.getLong("after.points")==10&&row.getLong("after.daily-awards.quests")==10);});
        test("saved payment phases retain the exact debited amount",()->{state.pending=new State.Purchase("eglow","DEBITED",19999,100000,1);store.save(state);state.owned.add("eglow");state.pending=null;store.save(state);var row=read(audit.resolve("4.yml"));require(row.getDouble("before.pending-purchase.price")==19999&&row.getStringList("after.owned-perks").contains("eglow"));});
        test("post-profile crash recovery finalises the same receipt without replaying it",()->{Files.move(audit.resolve("4.yml"),audit.resolve("4.pending"));new Store(folder,journey).save(state);require(Files.isRegularFile(audit.resolve("4.yml"))&&!Files.exists(audit.resolve("4.pending"))&&!Files.exists(audit.resolve("5.yml")));});
        test("independent audit counter survives an older profile writer removing its sequence",()->{var old=read(profile);old.set("staff-audit-sequence",null);Store.atomic(profile,old.saveToString());state.points++;store.save(state);require(read(profile).getLong("staff-audit-sequence")==5&&Files.exists(audit.resolve("5.yml")));});
        test("failed profile commit never creates a completed receipt",()->{Path f=root.resolve("failed");Store st=new Store(f,journey);State s=st.get(uuid,"FixturePlayer");s.points=10;Path temporary=f.resolve("journeydata/"+uuid+".yml.tmp");Files.createDirectory(temporary);boolean failed=false;try{st.save(s);}catch(Exception expected){failed=true;}require(failed&&!Files.exists(f.resolve("staff-audit/"+uuid+"/1.yml")));Files.delete(temporary);s.points=20;st.save(s);require(Files.exists(f.resolve("staff-audit/"+uuid+"/1.uncommitted"))&&read(f.resolve("staff-audit/"+uuid+"/2.yml")).getLong("after.points")==20);});
        test("audit prepare failure leaves the existing gameplay profile unchanged",()->{byte[] before=Files.readAllBytes(profile);Files.createDirectory(audit.resolve("sequence.txt.tmp"));state.points++;boolean failed=false;try{store.save(state);}catch(Exception expected){failed=true;}require(failed&&Arrays.equals(before,Files.readAllBytes(profile)));});
        System.out.println("RESULT "+checks+" Staff audit checks passed");
    }
}
