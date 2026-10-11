package com.townyreborn.staffconnector;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.*;

/** EC 7's completed history reports the opening count independently of reward grants. */
final class CrateHistoryReader {
    private static final Pattern BATCH=Pattern.compile("^\\[([0-9-]+ [0-9:]+)] Player: (\\S+) \\| Crate: (\\S+) \\|.*?selectedAmount:\\s*(\\d+) \\|.*?Rewards Given: (.*?) \\| Rewards Required:\\s*(\\d+)\\s*$",Pattern.DOTALL);
    private static final Pattern REWARD=Pattern.compile("rewardId=([^] ,]+)");
    private final Path cursor;private final InsightsOutbox outbox;private final Logger log;
    private long offset=-1;private String identity="";
    CrateHistoryReader(Path folder,InsightsOutbox outbox,Logger log){this.cursor=folder.resolve("crate-history-cursor.json");this.outbox=outbox;this.log=log;
        try{if(Files.isRegularFile(cursor)&&Json.decode(Files.readString(cursor)) instanceof Map<?,?> m){offset=((Number)m.get("offset")).longValue();identity=Objects.toString(m.get("identity"),"");}}
        catch(Exception e){throw new IllegalStateException("Cannot restore crate history checkpoint; preserve the file",e);}
    }
    static Map<String,Object> parse(String line,long position){
        Matcher m=BATCH.matcher(line);if(!m.matches())return null;
        int amount=Integer.parseInt(m.group(4));if(amount<1||amount>1000000)return null;
        List<String> rewards=new ArrayList<>();Matcher r=REWARD.matcher(m.group(5));while(r.find())rewards.add(r.group(1));
        String at=LocalDateTime.parse(m.group(1),DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(ZoneId.of("Europe/Berlin")).toInstant().toString();
        Map<String,Object> row=new LinkedHashMap<>();row.put("type","game.event");row.put("event_id","crate-history-"+GameInsights.hash(position+":"+line));row.put("plugin","ExcellentCrates");row.put("metric","crate_openings");row.put("dimension",m.group(3));row.put("username",m.group(2));row.put("value",amount);row.put("count",1);row.put("occurred_at",at);row.put("details",Map.of("selected_amount",amount,"rewards",rewards,"rewards_required",Integer.parseInt(m.group(6)),"source","confirmed_batch_history"));return row;
    }
    void read(Path file){
        try{
            if(!Files.isRegularFile(file))return;
            var attributes=Files.readAttributes(file,java.nio.file.attribute.BasicFileAttributes.class);
            String current=Objects.toString(attributes.fileKey(),"");long size=attributes.size();
            // Existing history is imported explicitly; installing a connector never replays it.
            if(offset<0){offset=size;identity=current;save();return;}
            if(size<offset||!identity.equals(current)){offset=0;identity=current;}
            if(size==offset)return;
            long next=offset;
            try(RandomAccessFile input=new RandomAccessFile(file.toFile(),"r")){
                input.seek(offset);ByteArrayOutputStream line=new ByteArrayOutputStream(),record=new ByteArrayOutputStream();
                for(int read=0;read<2097152;read++){
                    int value=input.read();if(value<0)break;
                    if(value=='\n'){
                        String text=line.toString(StandardCharsets.UTF_8).replaceFirst("\\r$","");
                        if(record.size()==0&&!text.matches("^\\[[0-9-]+ [0-9:]+] Player: .*")){next=input.getFilePointer();line.reset();continue;}
                        if(record.size()>0&&text.matches("^\\[[0-9-]+ [0-9:]+] Player: .*")){log.warning("Skipped an incomplete crate history record");record.reset();next=input.getFilePointer()-line.size()-1;}
                        record.write(text.getBytes(StandardCharsets.UTF_8));String full=record.toString(StandardCharsets.UTF_8);Map<String,Object> row=parse(full,next);
                        if(row!=null){if(!outbox.add(row))break;next=input.getFilePointer();record.reset();}else record.write('\n');
                        line.reset();
                    }else{line.write(value);if(line.size()+record.size()>262144)throw new IOException("Oversized crate history record");}
                }
            }
            if(outbox.checkpoint()){offset=next;save();}
        }catch(Exception e){log.warning("Crate history retained after "+e.getClass().getSimpleName());}
    }
    private void save()throws IOException{Path tmp=cursor.resolveSibling(cursor.getFileName()+".tmp");Files.writeString(tmp,Json.encode(Map.of("offset",offset,"identity",identity)));try{Files.move(tmp,cursor,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,cursor,StandardCopyOption.REPLACE_EXISTING);}}
}
