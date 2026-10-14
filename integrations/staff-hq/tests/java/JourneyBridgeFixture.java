package com.townyreborn.staffconnector;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import net.townyreborn.journey.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Runs the real Journey Store and exports its real audit receipts through the connector parser. */
public final class JourneyBridgeFixture {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);Files.createDirectories(root);
        var config=JourneyInsights.load(Path.of(args[1]));var journey=new Journey(config);var store=new Store(root,journey);
        java.util.UUID uuid=java.util.UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");var state=store.get(uuid,"BridgePlayer");
        store.save(state);
        state.capped(java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString(),"quests",10,300);store.save(state);
        state.points=journey.threshold(88);store.save(state);
        state.pending=new State.Purchase("eglow","PREPARED",19999,100000,System.currentTimeMillis());store.save(state);
        state.pending=new State.Purchase("eglow","DEBITED",19999,100000,System.currentTimeMillis());store.save(state);
        state.owned.add("eglow");state.pending=null;store.save(state);
        state.pendingGift="milestone_001";state.pendingGiftMoney=1234;state.pendingGiftTownBlocks=2;state.pendingGiftTown="FixtureTown";state.pendingGiftCommands=List.of("cratekey give "+uuid+" vote 1");store.save(state);
        state.claimed.add("milestone_001");state.pendingGift="";state.pendingGiftMoney=0;state.pendingGiftTownBlocks=0;store.save(state);
        var rows=new ArrayList<Map<String,Object>>();
        try(var files=Files.list(root.resolve("staff-audit/"+uuid))) {
            for(Path file:files.filter(p->p.toString().endsWith(".yml")).sorted().toList())rows.addAll(JourneyInsights.events(JourneyInsights.load(file),config));
        }
        var profile=JourneyInsights.plain(JourneyInsights.load(root.resolve("journeydata/"+uuid+".yml")));
        profile.put("level",88);profile.put("rank",config.getString("journey.titles.88"));profile.put("profile_saved_at",Instant.now().toString());
        Map<String,Object> result=new LinkedHashMap<>();result.put("events",rows);result.put("profile",profile);
        var catalogue=new LinkedHashMap<String,Object>();for(String key:List.of("journey","sources","perks","gifts"))catalogue.put(key,JourneyInsights.plain(config.getConfigurationSection(key)));result.put("catalogue",catalogue);
        Files.writeString(Path.of(args[2]),Json.encode(result));System.out.println("Real Journey -> connector fixture: "+rows.size()+" metric receipts");
    }
}
