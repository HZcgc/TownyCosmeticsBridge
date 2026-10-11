package net.townyreborn.mcmmo;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class MainTest {
    private static int checks;
    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static void rejects(Runnable action, String message) {
        checks++;
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }
    // Independent reconstruction using the native Standard indices, NOT the converter's helpers.
    private static long retroTotal(int level, int xp) {
        long sum = xp;
        for (int i=0; i<level; i++) sum += 10200L + 200L*i;
        return sum;
    }
    private static long standardTotal(int level, int xp) {
        long sum = xp;
        for (int i=0; i<level; i++) for (int index=i*10+1; index<=i*10+10; index++) sum += 10000L + 200L*index;
        return sum;
    }
    private static String row(int mining, int xp) {
        String[] fields = new String[59]; Arrays.fill(fields,"0");
        fields[0]="TestPlayer";fields[1]=Integer.toString(mining);fields[4]=Integer.toString(xp);
        fields[2]="KEEP-IGNORED";fields[23]="1234567";
        fields[41]="12345678-1234-1234-1234-123456789abc";
        fields[57]="KEEP-METADATA";fields[58]="";
        return String.join(":",fields);
    }
    private static Map<String,byte[]> fixture() {
        Map<String,byte[]> files = new LinkedHashMap<>();
        String config="General:\r\n  RetroMode:\r\n    Enabled: true # keep comment\r\n  Power_Level_Cap: 0\r\n  Level_Up_Chat_Broadcasts:\r\n    Milestone_Interval: 100\r\n    Broadcast_Powerlevels:\r\n      Milestone_Interval: 100\r\nMySQL:\r\n  Enabled: false\r\nSkills:\r\n";
        for (Main.Skill skill:Main.SKILLS) config+="  "+skill.name()+":\r\n    Level_Cap: 0\r\n";
        config+="  Salvage:\r\n    Level_Cap: 0\r\n  Smelting:\r\n    Level_Cap: 0\r\n";
        files.put("config.yml",bytes(config));
        files.put("experience.yml",bytes("Experience_Formula:\n  Curve: LINEAR\n  Cumulative_Curve: false\n  Linear_Values:\n    base: 10200 # unchanged spacing\n    multiplier: 200\n  Multiplier:\n    Global: 1.0\n"));
        files.put("skillranks.yml",bytes("Mining:\n  DoubleDrops:\n    Standard:\n      Rank_1: 1\n      Rank_2: 25\n    RetroMode:\n      Rank_1: 1\n      Rank_2: 250\n"));
        files.put("flatfile/mcmmo.users",bytes("# keep header\r\n"+row(525,4321)+"\r\n\r\n"));
        files.put("flatfile/formula.yml",bytes("Previous_Formula: UNKNOWN\n"));
        files.put("level_up_commands.yml",bytes("level_up_commands:\n  reward:\n    enabled: false\n"));
        return files;
    }
    private static void withText(Map<String,byte[]> files,String key,String from,String to) {
        files.put(key,bytes(new String(files.get(key),StandardCharsets.UTF_8).replace(from,to)));
    }
    public static void main(String[] args) throws Exception {
        for (int level:new int[]{0,1,2,8,9,10,11,19,20,99,100,249,250,253,499,500,525,999,1000,1001}) {
            int next=10200+200*level;
            for(int xp:new int[]{0,1,next/2,next-1}) {
                Main.Progress migrated=Main.scale(level,xp,10200,200);
                check(migrated.level()==level/10,"Level grouping must retain all completed ten-level blocks");
                check(retroTotal(level,xp)==standardTotal(migrated.level(),migrated.xp()),"Native curve reconstruction lost or granted XP at level "+level);
            }
        }
        check(Main.scale(250,0,10200,200).level()==25,"Exact threshold 250 must remain Standard 25, not 24");
        check(Main.scale(9,0,10200,200).xp()>0,"Sub-ten levels must remain represented as XP");
        rejects(()->Main.scale(-1,0,10200,200),"Negative levels must abort");
        rejects(()->Main.scale(5,11200,10200,200),"XP at an unnormalised level boundary must abort");
        rejects(()->Main.scale(99999,0,10200,200),"Float precision loss must abort");
        Map<String,byte[]> input=fixture();
        Map<String,byte[]> retained=new HashMap<>();input.forEach((k,v)->retained.put(k,v.clone()));
        Main.Result result=Main.convert(input);
        for(String file:input.keySet()) check(Arrays.equals(input.get(file),retained.get(file)),"Input mutated: "+file);
        String config=new String(result.after().get("config.yml"),StandardCharsets.UTF_8);
        check(config.contains("Enabled: false # keep comment\r\n"),"Retro change must retain CRLF and comments");
        check(config.contains("Milestone_Interval: 10\r\n"),"Milestone must use the new scale");
        check(new String(result.after().get("experience.yml"),StandardCharsets.UTF_8).contains("base: 10000 # unchanged spacing"),"Index correction must retain comments");
        String ranks=new String(result.after().get("skillranks.yml"),StandardCharsets.UTF_8);
        check(ranks.contains("Standard:\n      Rank_1: 0\n      Rank_2: 25"),"Keep existing basic unlocks without lowering higher requirements");
        check(ranks.contains("RetroMode:\n      Rank_1: 1"),"Retain rollback scaling in the rank config");
        String data=new String(result.after().get("flatfile/mcmmo.users"),StandardCharsets.UTF_8);
        check(data.startsWith("# keep header\r\n")&&data.endsWith("\r\n\r\n"),"Retain header and trailing blank lines");
        String[] before=row(525,4321).split(":",-1),after=data.split("\r\n")[1].split(":",-1);
        Set<Integer> changed=new HashSet<>();for(Main.Skill skill:Main.SKILLS){changed.add(skill.levelIndex());changed.add(skill.xpIndex());}
        for(int i=0;i<before.length;i++)if(!changed.contains(i))check(before[i].equals(after[i]),"Metadata changed at field "+i);
        check(result.profiles()==1,"Profile count changed");
        check(result.preservedXp()==retroTotal(525,4321),"Report total disagrees with independent reconstruction");
        for(String file:Main.CHANGED_FILES)check(Arrays.equals(result.before().get(file),retained.get(file)),"Rollback bytes differ: "+file);
        Map<String,byte[]> already=new HashMap<>(input);already.putAll(result.after());
        rejects(()->Main.convert(already),"Double conversion must abort");
        Map<String,byte[]> marked=fixture();marked.put("towny-standard-migration.properties",bytes("done=true"));
        rejects(()->Main.convert(marked),"A marker must block conversion even if Retro is manually re-enabled");
        Map<String,byte[]> mysql=fixture();withText(mysql,"config.yml","MySQL:\r\n  Enabled: false","MySQL:\r\n  Enabled: true");
        rejects(()->Main.convert(mysql),"SQL snapshots must abort");
        Map<String,byte[]> curve=fixture();withText(curve,"experience.yml","base: 10200","base: 10201");
        rejects(()->Main.convert(curve),"A changed XP curve must abort");
        Map<String,byte[]> cap=fixture();withText(cap,"config.yml","Power_Level_Cap: 0","Power_Level_Cap: 1000");
        rejects(()->Main.convert(cap),"Custom caps must abort");
        Map<String,byte[]> commands=fixture();withText(commands,"level_up_commands.yml","enabled: false","enabled: true");
        rejects(()->Main.convert(commands),"Reward thresholds need review before conversion");
        Map<String,byte[]> corrupt=fixture();withText(corrupt,"flatfile/mcmmo.users","KEEP-METADATA:","KEEP-METADATA:EXTRA:");
        rejects(()->Main.convert(corrupt),"Unknown record layout must abort");
        Map<String,byte[]> duplicate=fixture();duplicate.put("flatfile/mcmmo.users",bytes(row(5,1)+"\n"+row(5,1)+"\n"));
        rejects(()->Main.convert(duplicate),"Duplicate profiles must abort");
        Map<String,byte[]> child=fixture();
        String[] childFields=row(199,0).split(":",-1);childFields[7]="1";
        child.put("flatfile/mcmmo.users",bytes(String.join(":",childFields)+"\n"));
        child.put("skillranks.yml",bytes(new String(child.get("skillranks.yml"),StandardCharsets.UTF_8)
            +"Smelting:\n  FuelEfficiency:\n    Standard:\n      Rank_1: 10\n    RetroMode:\n      Rank_1: 100\n"));
        rejects(()->Main.convert(child),"Derived child-level rounding must not silently relock a higher ability rank");
        rejects(()->new Main.Yaml("General:\n  Mode: true\n  Mode: false\n"),"Duplicate YAML must abort");
        Path temporary=Files.createTempDirectory("mcmmo-migration-test-");
        try {
            Path archive=temporary.resolve("fresh.zip"),pack=temporary.resolve("package.zip");
            try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(archive))){
                for(var entry:input.entrySet()){zip.putNextEntry(new ZipEntry("mcMMO/"+entry.getKey()));zip.write(entry.getValue());zip.closeEntry();}
            }
            check(Main.archive(archive).keySet().containsAll(input.keySet()),"Nested mcMMO exports must work");
            try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(pack))){
                for(String file:Main.CHANGED_FILES){zip.putNextEntry(new ZipEntry("ROLLBACK/plugins/mcMMO/"+file));zip.write(input.get(file));zip.closeEntry();}
            }
            Main.verify(pack,archive);
            check(true,"Exact stopped snapshot verification failed");
            Path stale=temporary.resolve("stale.zip");
            try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(stale))){
                for(var entry:input.entrySet()){zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getKey().equals("flatfile/mcmmo.users")?bytes(row(526,1)):entry.getValue());zip.closeEntry();}
            }
            boolean rejected=false;
            try{Main.verify(pack,stale);}catch(IllegalArgumentException expected){rejected=true;}
            check(rejected,"A stale player snapshot must fail verification");
            Path unsafe=temporary.resolve("unsafe.zip");
            try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(unsafe))){zip.putNextEntry(new ZipEntry("../flatfile/mcmmo.users"));zip.write(bytes(row(5,1)));zip.closeEntry();}
            boolean blocked=false;
            try{Main.archive(unsafe);}catch(IllegalArgumentException expected){blocked=true;}
            check(blocked,"Unsafe ZIP paths must abort");
        } finally {
            try(var files=Files.walk(temporary)){for(Path file:files.sorted(Comparator.reverseOrder()).toList())Files.delete(file);}
        }
        System.out.println("PASS: "+checks+" migration safety checks.");
    }
}
