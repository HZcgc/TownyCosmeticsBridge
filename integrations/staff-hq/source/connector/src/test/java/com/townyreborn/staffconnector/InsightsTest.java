package com.townyreborn.staffconnector;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class InsightsTest {
 @TempDir Path directory;
 static final String LINE="[2026-10-10 12:31:53] Player: Tester | Crate: vote | location: World: spawn, X: 1, Y: 60, Z: 1 | maxAllowed:7 | selectedAmount:7 | Rewards Given: "+String.join(", ",Collections.nCopies(7,"RewardId[crateId=vote, rewardId=coin]"))+" | Rewards Required:7";
 @Test void confirmedBatchCountsSevenOpeningsAndPreservesAllRewards(){Map<String,Object> row=CrateHistoryReader.parse(LINE,0);assertEquals(7,row.get("value"));assertEquals(1,row.get("count"));assertEquals("2026-10-10T10:31:53Z",row.get("occurred_at"));assertEquals(7,((List<?>)((Map<?,?>)row.get("details")).get("rewards")).size());}
 @Test void batchIdentityIsStableButDistinctPositionsAreNotMerged(){assertEquals(CrateHistoryReader.parse(LINE,0).get("event_id"),CrateHistoryReader.parse(LINE,0).get("event_id"));assertNotEquals(CrateHistoryReader.parse(LINE,0).get("event_id"),CrateHistoryReader.parse(LINE,200).get("event_id"));assertNull(CrateHistoryReader.parse("unrelated log",0));}
 @Test void nativeHistorySupportsMultilineItemsAndReorderedFields(){String line=LINE.replace("location: World: spawn, X: 1, Y: 60, Z: 1 | maxAllowed:7 | selectedAmount:7", "itemStack: chest {\n\"id\":\"vote\"\n} | selectedAmount: 7 | maxAllowed:7").replace("Required:7","Required: 7");assertEquals(7,CrateHistoryReader.parse(line,0).get("value"));}
 @Test void failedHandoverRetainsDurableEventsAndReplayKeepsIdentity()throws Exception{
  InsightsOutbox first=new InsightsOutbox(directory,e->false,Logger.getAnonymousLogger());first.add(Map.of("type","game.event","value",7));first.flush();String saved=Files.readString(directory.resolve("insights-outbox.json"));first.close();List<Map<String,Object>>received=new ArrayList<>();
  try(InsightsOutbox second=new InsightsOutbox(directory,e->{received.add(e);return true;},Logger.getAnonymousLogger())){assertEquals(1,second.buffered());second.flush();assertEquals(0,second.buffered());}
  assertEquals(1,received.size());assertEquals(((Map<?,?>)((List<?>)Json.decode(saved)).getFirst()).get("event_id"),received.getFirst().get("event_id"));
 }
 @Test void checkpointFailureNeverHandsOwnershipToTransport()throws Exception{
  List<Map<String,Object>>received=new ArrayList<>();Files.createDirectory(directory.resolve("insights-outbox.json.tmp"));
  try(InsightsOutbox out=new InsightsOutbox(directory,e->{received.add(e);return true;},Logger.getAnonymousLogger())){out.add(Map.of("type","game.event"));out.flush();assertTrue(received.isEmpty());assertEquals(1,out.buffered());}
 }
 @Test void historyTailStartsAtEndThenWaitsForCompleteLineAndDoesNotReplay()throws Exception{
  Path file=directory.resolve("crate_history.log");Files.writeString(file,LINE+"\n");List<Map<String,Object>>received=new ArrayList<>();
  try(InsightsOutbox out=new InsightsOutbox(directory,e->{received.add(e);return true;},Logger.getAnonymousLogger())){
   CrateHistoryReader reader=new CrateHistoryReader(directory,out,Logger.getAnonymousLogger());reader.read(file);out.flush();assertTrue(received.isEmpty());
   Files.writeString(file,LINE,StandardOpenOption.APPEND);reader.read(file);out.flush();assertTrue(received.isEmpty());
   Files.writeString(file,"\n",StandardOpenOption.APPEND);reader.read(file);out.flush();assertEquals(1,received.size());
   new CrateHistoryReader(directory,out,Logger.getAnonymousLogger()).read(file);out.flush();assertEquals(1,received.size());
  }
 }
 @Test void actualExcellentCratesSevenApiUsesRewardGrantedEventAndStructuredIds()throws Exception{
  Class<?> idType;try{idType=Class.forName("su.nightexpress.engine.id.Identifier");}catch(ClassNotFoundException absent){org.junit.jupiter.api.Assumptions.assumeTrue(false,"Supply the actual EC 7/nightcore JARs for this native linkage check");return;}
  Object crateId=idType.getConstructor(String.class).newInstance("vote"),rid=idType.getConstructor(String.class).newInstance("coin");
  Object rewardId=Class.forName("su.nightexpress.excellentcrates.api.reward.registry.RewardId").getConstructor(idType,idType).newInstance(crateId,rid);
  Object crate=java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Class.forName("su.nightexpress.excellentcrates.api.crate.Crate")},(p,m,a)->m.getName().equals("getId")?crateId:null);
  Object reward=java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Class.forName("su.nightexpress.excellentcrates.api.reward.Reward")},(p,m,a)->m.getName().equals("getId")?rewardId:null);
  assertEquals("vote",GameInsights.label(crate));assertEquals("coin",GameInsights.label(reward));
  assertNotNull(Class.forName("su.nightexpress.excellentcrates.api.reward.event.RewardGrantedEvent").getMethod("getReward"));
  assertNotNull(Class.forName("su.nightexpress.excellentcrates.api.ExcellentCratesAPI").getMethod("getCratesAPI"));
 }
}
