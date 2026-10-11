package com.townyreborn.staffconnector;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.logging.Logger;

/** Off-thread durable handover. Replays keep their event IDs, so HQ can deduplicate them. */
final class InsightsOutbox implements AutoCloseable {
    private final Deque<Map<String,Object>> pending = new ArrayDeque<>();
    private final Predicate<Map<String,Object>> sink;
    private final Path file;
    private final Logger log;
    private final Object drain = new Object();
    private final ScheduledExecutorService worker;
    private long rejected;

    @SuppressWarnings("unchecked")
    InsightsOutbox(Path folder, Predicate<Map<String,Object>> sink, Logger log) {
        this.sink=sink;this.log=log;file=folder.resolve("insights-outbox.json");
        try {
            if(Files.isRegularFile(file) && Json.decode(Files.readString(file)) instanceof List<?> rows)
                for(Object row:rows) if(row instanceof Map<?,?> map) pending.add((Map<String,Object>)map);
        } catch(Exception error) { throw new IllegalStateException("Cannot restore the insights outbox; preserve the file and check it.",error); }
        worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"TR-Insights-Outbox");t.setDaemon(true);return t;});
        worker.scheduleWithFixedDelay(this::flush,2,2,TimeUnit.SECONDS);
    }

    synchronized boolean add(Map<String,Object> event) {
        if(pending.size()>=50000) { if(rejected++==0)log.severe("Game insights outbox is full; new game events cannot be retained.");return false; }
        Map<String,Object> copy=new LinkedHashMap<>(event);
        copy.putIfAbsent("event_id","game-"+UUID.randomUUID());
        copy.putIfAbsent("occurred_at",java.time.Instant.now().toString());
        pending.add(copy);return true;
    }

    void flush() {
        synchronized(drain) {
            try {
                if(!save())return;
                for(int i=0;i<120;i++) {
                    Map<String,Object> next;synchronized(this){next=pending.peekFirst();}
                    if(next==null || !sink.test(next))break;
                    synchronized(this){if(pending.peekFirst()==next)pending.removeFirst();}
                }
                save();
            } catch(RuntimeException error) { log.warning("Game insights retained after "+error.getClass().getSimpleName()); }
        }
    }

    private boolean save() {
        try {
            List<Map<String,Object>> rows;synchronized(this){rows=new ArrayList<>(pending);}
            Path tmp=file.resolveSibling(file.getFileName()+".tmp");Files.writeString(tmp,Json.encode(rows));
            try{Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ignored){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
            return true;
        }catch(Exception error){log.warning("Could not checkpoint game insights: "+error.getClass().getSimpleName());return false;}
    }
    synchronized int buffered(){return pending.size();}
    boolean checkpoint(){synchronized(drain){return save();}}
    synchronized long rejected(){return rejected;}
    @Override public void close(){worker.shutdown();synchronized(drain){save();}}
}
