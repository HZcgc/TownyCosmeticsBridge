package net.townyreborn.journey;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.bukkit.configuration.file.YamlConfiguration;

/** Optional private input, read-only. No player names, UUIDs or original records enter the report. */
public final class UploadedProfileChecks {
    static int checks;
    static final String UUID_FILE="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    interface Check { void run() throws Exception; }
    static void test(String label, Check body) throws Exception {
        body.run(); checks++; System.out.println("PASS " + label);
    }
    static void require(boolean value) { if(!value) throw new AssertionError("Private profile preservation regression"); }
    static String digest(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
    static boolean allowed(String name) {
        return name.matches("(?:journeydata|playerdata|history-credits)/"+UUID_FILE+"\\.yml")
            || name.matches("migration-backups/(?:long-journey-2026-10-v1|pre-reset-points-2026-10-v1)/(?:journeydata|playerdata)/"+UUID_FILE+"\\.yml")
            || name.matches("staff-audit/"+UUID_FILE+"/(?:sequence\\.txt|[0-9]+\\.(?:pending|yml|uncommitted))");
    }
    static void extract(Path archive, Path target) throws Exception {
        try(var zip=new ZipFile(archive.toFile())) {
            var entries=zip.entries();
            while(entries.hasMoreElements()) {
                var entry=entries.nextElement(); if(!allowed(entry.getName())||entry.isDirectory()) continue;
                require(entry.getSize()>=0&&entry.getSize()<2_000_000);
                Path file=target.resolve(entry.getName());Files.createDirectories(file.getParent());
                Files.write(file,zip.getInputStream(entry).readAllBytes());
            }
        }
    }
    static Map<String,String> preservedProofs(Path root) throws Exception {
        Map<String,String> result=new TreeMap<>();
        for(String name:List.of("history-credits","migration-backups")) {
            Path folder=root.resolve(name);if(!Files.exists(folder)) continue;
            try(var files=Files.walk(folder)) {
                for(Path file:files.filter(Files::isRegularFile).toList()) result.put(root.relativize(file).toString(),digest(file));
            }
        }
        return result;
    }
    static void equalProfile(YamlConfiguration before, State after, Journey journey) {
        require(before.getLong("points")==after.points&&before.getLong("active-minutes")==after.minutes
            &&before.getLong("journey-active-minutes")==after.journeyMinutes);
        require(new HashSet<>(before.getStringList("owned-perks")).equals(after.owned)
            &&new HashSet<>(before.getStringList("claimed-gifts")).equals(after.claimed)
            &&new HashSet<>(before.getStringList("unlocked-perks")).equals(after.unlocked)
            &&new HashSet<>(before.getStringList("achievements")).equals(after.achievements));
        require(before.getString("progress-revision","").equals(after.progressRevision)
            &&before.getString("history-credit-revision","").equals(after.historyCreditRevision)
            &&before.getLong("history-credit-points")==after.historyCreditPoints
            &&before.getLong("history-credit-minutes")==after.historyCreditMinutes
            &&before.getLong("history-credit-at")==after.historyCreditAt
            &&before.getString("history-credit-source","").equals(after.historyCreditSource)
            &&before.getLong("journey-started-at")==after.journeyStartedAt);
        require(before.getString("day","").equals(after.day)
            &&before.getString("pending-gift","").equals(after.pendingGift)
            &&!before.isConfigurationSection("pending-purchase")&&after.pending==null);
        for(var entry:Map.of("high-water",after.high,"quest-completions",after.questCompletions,"daily-awards",after.daily).entrySet()) {
            Map<String,Long> original=new TreeMap<>();var section=before.getConfigurationSection(entry.getKey());
            if(section!=null) for(String key:section.getKeys(false)) original.put(key,section.getLong(key));
            require(original.equals(entry.getValue()));
        }
        require(journey.level(after.points)>=1&&journey.level(after.points)<=journey.steps);
    }
    public static void main(String[] args) throws Exception {
        Path source=Path.of(args[0]),output=Path.of(args[1]);String sourceHash=digest(source);Files.createDirectories(output);
        Path nativeCopy=output.resolve("native"),legacyCopy=output.resolve("legacy");extract(source,nativeCopy);extract(source,legacyCopy);
        Journey journey=new Journey(JourneyUpdateChecks.readJar(args[2]));Store store=new Store(nativeCopy,journey);
        Map<String,String> proofs=preservedProofs(nativeCopy);
        List<Path> profiles;
        try(var files=Files.list(nativeCopy.resolve("journeydata"))) {profiles=files.filter(Files::isRegularFile).sorted().toList();}
        require(!profiles.isEmpty());int index=0;
        for(Path file:profiles) {
            var before=JourneyUpdateChecks.read(Files.readString(file));UUID id=UUID.fromString(file.getFileName().toString().replace(".yml",""));
            final int number=++index;
            test("uploaded native profile "+number+" retains points purchases rewards limits and historical credit",()->{
                State state=store.get(id,before.getString("name","TestPlayer"));equalProfile(before,state,journey);
                store.save(state);
                for(int attempt=0;attempt<3;attempt++) {
                    Store restart=new Store(nativeCopy,journey);State again=restart.get(id,before.getString("name","TestPlayer"));
                    equalProfile(before,again,journey);restart.save(again);
                }
            });
        }
        test("all existing historical receipts and migration backups remain byte-identical",()->require(proofs.equals(preservedProofs(nativeCopy))));
        List<Path> legacy;
        try(var files=Files.list(legacyCopy.resolve("playerdata"))) {legacy=files.filter(Files::isRegularFile).sorted().toList();}
        int offline=0;
        for(Path file:legacy) {
            UUID id=UUID.fromString(file.getFileName().toString().replace(".yml",""));
            if(Files.exists(legacyCopy.resolve("journeydata").resolve(file.getFileName()))) continue;
            final int number=++offline;var old=JourneyUpdateChecks.read(Files.readString(file));
            test("uploaded offline legacy profile "+number+" converts once without paid ownership or reward replay",()->{
                Store migration=new Store(legacyCopy,journey);State state=migration.get(id,old.getString("name","TestPlayer"));
                long expected=journey.legacyPerks.legacyPoints(old.getInt("level",1),old.getLong("active-minutes"),old.getLong("quest-points"));
                require(state.points==expected&&state.owned.isEmpty()&&state.claimed.isEmpty()&&state.pending==null&&state.pendingGift.isEmpty());
                long points=state.points,minutes=state.journeyMinutes;migration.save(state);
                for(int attempt=0;attempt<3;attempt++) {
                    Store restart=new Store(legacyCopy,journey);State again=restart.get(id,"TestPlayer");
                    require(again.points==points&&again.journeyMinutes==minutes&&again.owned.isEmpty()&&again.claimed.isEmpty());restart.save(again);
                }
            });
        }
        test("the uploaded archive is unchanged by every profile and restart check",()->require(sourceHash.equals(digest(source))));
        System.out.println("RESULT "+checks+" uploaded profile checks passed");
    }
}
