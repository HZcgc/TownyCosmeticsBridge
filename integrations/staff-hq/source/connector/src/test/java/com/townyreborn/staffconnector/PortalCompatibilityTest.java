package com.townyreborn.staffconnector;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PortalCompatibilityTest {
    @TempDir Path folder;

    @Test void executedJsonVersionSurvivesStrippedOrOutdatedHeaders() {
        var actual = PortalCompatibility.read("7", Map.of("api_version",9,"capabilities",Map.of("journey",true)));
        assertEquals(9,actual.apiVersion()); assertFalse(actual.journeyUnavailable());
        assertEquals(8,PortalCompatibility.read("",Map.of("version",8,"journey",true)).apiVersion());
        assertFalse(PortalCompatibility.read("",Map.of()).known());
        assertFalse(PortalCompatibility.read("invalid",Map.of("api_version","9.5")).known());
        assertTrue(PortalCompatibility.read("9",Map.of("capabilities",Map.of("journey",false))).journeyUnavailable());
    }

    @Test void identifiedBadJourneyMetricCannotBlockHealthyEventsWhenHeadersAreStripped() throws Exception {
        try (var api = new Fixture(Map.of(), body -> {
            var events = events(body);
            for (int i=0;i<events.size();i++) if ("bad".equals(events.get(i).get("event_id")))
                return rejection(events.get(i),i,"invalid_game_event_value",9,true);
            return accepted(events.size());
        }); var client = client(api,50_000)) {
            client.enqueue(Map.of("type","game.event","plugin",JourneyInsights.PLUGIN,"event_id","bad","value","bad"));
            client.enqueue(Map.of("type","game.event","plugin",JourneyInsights.PLUGIN,"event_id","healthy","value",12));
            flush(client); assertFalse(client.status().state().contains("needs Staff HQ API"));
            flush(client); flush(client);
            assertEquals(0,client.status().queuedEvents()); assertEquals(9,client.status().apiVersion());
            var archived = Files.readAllLines(folder.resolve("pending/rejected-events.jsonl"));
            assertEquals(1,archived.size()); assertTrue(archived.getFirst().contains("\"event_id\":\"bad\""));
            assertFalse(archived.getFirst().contains("healthy"));
        }
    }

    @Test void currentHeaderlessApiEightCanBeVerifiedThroughItsRealHealthResponse() throws Exception {
        try (var api = new Fixture(Map.of("status","ok","service","Towny Reborn Staff API","version",8,"journey",true), body ->
                rejection(events(body).getFirst(),0,"invalid_integration_snapshot",0,null));
             var client = client(api,50_000)) {
            client.enqueue(Map.of("type","integration.snapshot","plugin",JourneyInsights.PLUGIN,"event_id","bad-profile","data","invalid"));
            flush(client);
            assertEquals(1,api.healthRequests.get()); assertEquals(8,client.status().apiVersion());
            assertEquals(0,client.status().queuedEvents()); assertTrue(Files.exists(folder.resolve("pending/rejected-events.jsonl")));
        }
    }

    @Test void unrelatedInvalidEntryInMixedJourneyBatchDoesNotBecomeAFalseCompatibilityFailure() throws Exception {
        try (var api = new Fixture(Map.of(), body -> {
            var events=events(body);
            for(int i=0;i<events.size();i++) if("bad-chat".equals(events.get(i).get("event_id")))
                return rejection(events.get(i),i,"invalid_message",7,null);
            return accepted(events.size());
        });var client=client(api,50_000)) {
            client.enqueue(Map.of("type","game.event","plugin",JourneyInsights.PLUGIN,"event_id","valid-journey"));
            client.enqueue(Map.of("type","chat.message","event_id","bad-chat"));
            flush(client);assertFalse(client.status().state().contains("Journey snapshot rejected"));
            flush(client);flush(client);flush(client);
            assertEquals(0,client.status().queuedEvents());
            String archived=Files.readString(folder.resolve("pending/rejected-events.jsonl"));
            assertTrue(archived.contains("bad-chat"));assertFalse(archived.contains("valid-journey"));
        }
    }

    @Test void actualOlderHeaderlessHqRetainsJourneySnapshotForWebsiteUpdate() throws Exception {
        try(var api=new Fixture(Map.of("status","ok","service","Towny Reborn Staff API","version",7),body ->
                rejection(events(body).getFirst(),0,"invalid_integration_snapshot",0,null));var client=client(api,50_000)) {
            client.enqueue(Map.of("type","integration.snapshot","plugin",JourneyInsights.PLUGIN,"event_id","retained-old-hq","data",Map.of()));
            flush(client);assertEquals(1,client.status().queuedEvents());assertTrue(client.status().state().contains("HQ API 7"));
            assertFalse(Files.exists(folder.resolve("pending/rejected-events.jsonl")));
        }
        assertTrue(Files.readString(folder.resolve("pending/pending-events.jsonl")).contains("retained-old-hq"));
    }

    @Test void partialWebsiteDeploymentReportsMissingJourneyCapabilityAndRetainsReceipt() throws Exception {
        try(var api=new Fixture(Map.of(),body -> rejection(events(body).getFirst(),0,"invalid_integration_snapshot",9,false));
            var client=client(api,50_000)) {
            client.enqueue(Map.of("type","integration.snapshot","plugin",JourneyInsights.PLUGIN,"event_id","retained-partial-hq","data",Map.of()));
            flush(client);assertEquals(1,client.status().queuedEvents());assertTrue(client.status().state().contains("complete Staff HQ"));
            assertFalse(Files.exists(folder.resolve("pending/rejected-events.jsonl")));
        }
    }

    @Test void unknownCapabilityIsNotClaimedToBeAnOldApiAndNeverArchivesJourneySnapshot() throws Exception {
        try(var api=new Fixture(Map.of("status","sign-in"),body -> rejection(events(body).getFirst(),0,"invalid_integration_snapshot",0,null));
            var client=client(api,50_000)) {
            client.enqueue(Map.of("type","integration.snapshot","plugin",JourneyInsights.PLUGIN,"data",Map.of()));
            flush(client);assertEquals(1,client.status().queuedEvents());assertTrue(client.status().state().contains("could not be verified"));
            assertFalse(client.status().state().contains("HQ API 0"));assertFalse(Files.exists(folder.resolve("pending/rejected-events.jsonl")));
        }
    }

    @Test void currentApiNumberCannotHideAStaleSnapshotValidatorOrDiscardAValidJourneyProfile() throws Exception {
        try(var api=new Fixture(Map.of(),body -> rejection(events(body).getFirst(),0,"invalid_integration_snapshot",9,true));
            var client=client(api,50_000)) {
            client.enqueue(Map.of("type","integration.snapshot","plugin",JourneyInsights.PLUGIN,"event_id","valid-profile","snapshot_key","player.fixture","data",Map.of("points",150)));
            flush(client);assertEquals(1,client.status().queuedEvents());assertTrue(client.status().state().contains("valid Journey snapshot"));
            assertFalse(Files.exists(folder.resolve("pending/rejected-events.jsonl")));
        }
    }

    @Test void mismatchedRejectionIdentityCannotDiscardQueuedReceipt() throws Exception {
        try(var api=new Fixture(Map.of(),body -> rejection(Map.of("event_id","different","type","game.event"),0,"invalid_game_metric",9,true));
            var client=client(api,50_000)) {
            client.enqueue(Map.of("type","game.event","plugin",JourneyInsights.PLUGIN,"event_id","keep-me"));
            flush(client);assertEquals(1,client.status().queuedEvents());assertFalse(Files.exists(folder.resolve("pending/rejected-events.jsonl")));
        }
    }

    @Test void fiftyThousandQueueEntriesSurviveLegacyLimitUpgradeAndRestartWithoutChangingIds() throws Exception {
        int capacity=Settings.queueLimit(5000);assertEquals(50_000,capacity);
        Path pending=folder.resolve("pending");Files.createDirectories(pending);
        StringBuilder original=new StringBuilder();for(int i=0;i<49_999;i++) original.append(Json.encode(Map.of("type","staff.action","event_id","legacy-"+i))).append('\n');
        Files.writeString(pending.resolve("pending-events.jsonl"),original);
        try(var api=new Fixture(Map.of(),body -> accepted(events(body).size()));var client=client(api,capacity)) {
            assertEquals(49_999,client.status().queuedEvents());assertEquals(50_000,client.status().queueCapacity());
            assertTrue(client.tryEnqueue(Map.of("type","staff.action","event_id","fifty-thousand")));
            assertFalse(client.tryEnqueue(Map.of("type","staff.action","event_id","over-limit")));
        }
        String saved=Files.readString(pending.resolve("pending-events.jsonl"));assertTrue(saved.startsWith(original.toString()));
        try(var api=new Fixture(Map.of(),body -> accepted(events(body).size()));var client=client(api,capacity)) {
            assertEquals(50_000,client.status().queuedEvents());
        }
        assertEquals(saved,Files.readString(pending.resolve("pending-events.jsonl")));
    }

    @Test void higherConfiguredCapacityIsRespectedAndOversizedExistingQueueIsNeverTruncated() throws Exception {
        assertEquals(100_000,Settings.queueLimit(100_000));assertEquals(500_000,Settings.queueLimit(Integer.MAX_VALUE));
        Path pending=folder.resolve("pending");Files.createDirectories(pending);
        StringBuilder original=new StringBuilder();for(int i=0;i<50_001;i++) original.append("{\"type\":\"staff.action\",\"event_id\":\"keep-").append(i).append("\"}\n");
        Files.writeString(pending.resolve("pending-events.jsonl"),original);
        try(var api=new Fixture(Map.of(),body -> accepted(events(body).size()));var client=client(api,50_000)) {
            assertEquals(50_001,client.status().queuedEvents());assertFalse(client.tryEnqueue(Map.of("type","staff.action")));
        }
        assertEquals(original.toString(),Files.readString(pending.resolve("pending-events.jsonl")));
    }

    private PortalClient client(Fixture api,int capacity) throws Exception {
        var settings=new Settings(api.endpoint(),"tr_live_fixture_token_only_1234567890","smp",Duration.ofSeconds(2),Duration.ofSeconds(3),10,20,capacity,30,30,5,1,1,"",false,false,false,false,false,false,false,false,false,Set.of());
        return new PortalClient(folder.resolve("pending"),settings,Logger.getAnonymousLogger(),action -> {});
    }
    private static void flush(PortalClient client) throws Exception {
        var retry=PortalClient.class.getDeclaredField("nextAttemptNanos");retry.setAccessible(true);retry.setLong(client,0);
        var flush=PortalClient.class.getDeclaredMethod("flushSafely");flush.setAccessible(true);flush.invoke(client);
    }
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> events(Map<String,Object> body) {return (List<Map<String,Object>>)body.get("events");}
    private static Map<String,Object> accepted(int count) {return Map.of("accepted",true,"event_count",count,"accepted_count",count,"duplicate_count",0);}
    private static Map<String,Object> rejection(Map<String,Object> event,int index,String validation,int version,Boolean journey) {
        Map<String,Object> result=new LinkedHashMap<>(Map.of("error","invalid_event_data","validation_error",validation,"event_index",index,"event_id",event.get("event_id"),"event_type",event.get("type")));
        if(version>0)result.put("api_version",version);if(journey!=null)result.put("capabilities",Map.of("journey",journey));return result;
    }
    private static final class Fixture implements AutoCloseable {
        final HttpServer server;final AtomicInteger healthRequests=new AtomicInteger();
        @SuppressWarnings("unchecked") Fixture(Map<String,Object> health,Function<Map<String,Object>,Map<String,Object>> handler) throws Exception {
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/api.php",exchange -> {try {
                boolean get="GET".equals(exchange.getRequestMethod());
                Map<String,Object> result;
                if(get){healthRequests.incrementAndGet();result=health;}
                else result=handler.apply((Map<String,Object>)Json.decode(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)));
                byte[] bytes=Json.encode(result).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(result.containsKey("error")?422:200,bytes.length);exchange.getResponseBody().write(bytes);
            } finally {exchange.close();}});server.start();
        }
        URI endpoint(){return URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/api.php");}
        public void close(){server.stop(0);}
    }
}
