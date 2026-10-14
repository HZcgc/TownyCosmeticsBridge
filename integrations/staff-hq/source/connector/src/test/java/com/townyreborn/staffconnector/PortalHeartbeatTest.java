package com.townyreborn.staffconnector;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PortalHeartbeatTest {
    @TempDir Path folder;
    @SuppressWarnings("unchecked") @Test void olderHqCannotArchiveUnsupportedJourneySnapshots() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api.php",exchange->{try{
            var body=(Map<String,Object>)Json.decode(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            var event=(Map<String,Object>)((List<?>)body.get("events")).getFirst();
            byte[] response=Json.encode(Map.of("error","invalid_event_data","validation_error","invalid_integration_snapshot","event_index",0,"event_id",event.get("event_id"),"event_type",event.get("type"))).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("X-TR-API-Version","7");exchange.sendResponseHeaders(422,response.length);exchange.getResponseBody().write(response);
        }finally{exchange.close();}});server.start();
        var settings=new Settings(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/api.php"),"tr_live_fixture_token_only_1234567890","smp",Duration.ofSeconds(2),Duration.ofSeconds(3),10,20,100,30,30,5,1,1,"",false,false,false,false,false,false,false,false,false,Set.of());
        try(var client=new PortalClient(folder,settings,Logger.getAnonymousLogger(),action->{})){
            client.enqueue(Map.of("type","integration.snapshot","plugin","TownyRebornProgression","snapshot_key","catalog","data",Map.of()));
            var flush=PortalClient.class.getDeclaredMethod("flushSafely");flush.setAccessible(true);flush.invoke(client);
            assertEquals(1,client.status().queuedEvents());assertTrue(client.status().state().contains("API 8"));assertFalse(Files.exists(folder.resolve("rejected-events.jsonl")));
        }finally{server.stop(0);}
    }
    @SuppressWarnings("unchecked") @Test void priorityLivenessSurvives500AndRetriedEventsKeepTheirIdentity() throws Exception {
        var failing=new AtomicBoolean(true);var heartbeats=new AtomicInteger();var uploads=new ArrayList<List<Map<String,Object>>>();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api.php",exchange->{
            try {
                var body=(Map<String,Object>)Json.decode(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
                String response;int status;
                if("connector.heartbeat".equals(body.get("operation"))){heartbeats.incrementAndGet();status=202;response="{\"accepted\":true,\"heartbeat\":true}";}
                else {var rows=(List<Map<String,Object>>)body.get("events");uploads.add(rows);status=failing.get()?500:202;response=failing.get()?"{\"error\":\"storage_error\"}":"{\"accepted\":true,\"event_count\":"+rows.size()+",\"accepted_count\":"+rows.size()+",\"duplicate_count\":0}";}
                byte[] data=response.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,data.length);exchange.getResponseBody().write(data);
            } finally {exchange.close();}
        });server.start();
        var endpoint=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/api.php");
        var settings=new Settings(endpoint,"tr_live_fixture_token_only_1234567890","smp",Duration.ofSeconds(2),Duration.ofSeconds(3),10,20,100,30,30,5,1,1,"",false,false,false,false,false,false,false,false,false,Set.of());
        try(var client=new PortalClient(folder,settings,Logger.getAnonymousLogger(),action->{})) {
            client.enqueue(Map.of("type","server.snapshot","online_players",7,"occurred_at",Instant.now().toString(),"metadata",Map.of("connector_version","1.5.0")));
            client.enqueue(Map.of("type","game.event","plugin","TownyRebornProgression","metric","journey_jp","value",10));
            var flush=PortalClient.class.getDeclaredMethod("flushSafely");flush.setAccessible(true);flush.invoke(client);
            assertEquals(1,heartbeats.get());assertNotNull(client.heartbeatSuccess());assertEquals(500,client.status().lastHttpStatus());assertEquals(2,client.status().queuedEvents());
            failing.set(false);var retry=PortalClient.class.getDeclaredField("nextAttemptNanos");retry.setAccessible(true);retry.setLong(client,0);flush.invoke(client);
            assertEquals(0,client.status().queuedEvents());assertNotNull(client.status().lastSuccess());
            assertEquals(uploads.getFirst().stream().map(r->r.get("event_id")).toList(),uploads.getLast().stream().map(r->r.get("event_id")).toList());
        } finally {server.stop(0);}
        assertTrue(Files.readString(folder.resolve("pending-events.jsonl")).isBlank());
    }
}
