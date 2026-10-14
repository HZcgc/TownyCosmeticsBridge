package com.townyreborn.staffconnector;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.jar.JarFile;

/** Optional API listeners plus read-only catalog/ledger snapshots. Never changes keys or permissions. */
final class GameInsights implements Listener, AutoCloseable {
    private final JavaPlugin owner;
    private final InsightsOutbox outbox;
    private final Map<String,List<String>> hooks=new HashMap<>();
    private final Set<String> registered=ConcurrentHashMap.newKeySet();
    private final Map<String,String> unavailable=new ConcurrentHashMap<>();
    private final ScheduledExecutorService reader=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"TR-Integration-Files");t.setDaemon(true);return t;});
    private final Set<String> completedPets=new HashSet<>();
    private final Path petCursor;
    private final CrateHistoryReader crateHistory;
    private final JourneyInsights journey;
    private boolean petBaseline;
    private int snapshotTask=-1;

    GameInsights(JavaPlugin owner,InsightsOutbox outbox) {
        this.owner=owner;this.outbox=outbox;petCursor=owner.getDataFolder().toPath().resolve("pet-ledger-seen.json");
        crateHistory=new CrateHistoryReader(owner.getDataFolder().toPath(),outbox,owner.getLogger());
        journey=new JourneyInsights(outbox,owner.getLogger());
        try{if(Files.isRegularFile(petCursor)&&Json.decode(Files.readString(petCursor)) instanceof List<?> keys){for(Object key:keys)completedPets.add(String.valueOf(key));petBaseline=true;}}
        catch(Exception e){throw new IllegalStateException("Cannot read pet insights checkpoint",e);}
    }
    void start() {
        Bukkit.getPluginManager().registerEvents(this,owner);registerHooks();
        snapshotTask=Bukkit.getScheduler().scheduleSyncRepeatingTask(owner,this::snapshots,60L,6000L);
        reader.scheduleWithFixedDelay(this::petLedger,5,15,TimeUnit.SECONDS);
        reader.scheduleWithFixedDelay(journey::read,2,2,TimeUnit.SECONDS);
        reader.scheduleWithFixedDelay(()->{Plugin p=Bukkit.getPluginManager().getPlugin("ExcellentCrates");if(p!=null&&registered.contains("su.nightexpress.excellentcrates.api.reward.event.RewardGrantedEvent"))crateHistory.read(p.getDataFolder().toPath().resolve("logs/crate_history.log"));},5,5,TimeUnit.SECONDS);
    }
    @EventHandler public void enabled(PluginEnableEvent event){Bukkit.getScheduler().runTask(owner,this::registerHooks);}

    private void record(String plugin,String metric,String dimension,Object actor,double value,Map<String,Object> details) {
        if(actor instanceof Optional<?> optional)actor=optional.orElse(null);
        UUID uuid=actor instanceof UUID id?id:actor instanceof OfflinePlayer p?p.getUniqueId():Reflect.uuid(actor,"getUniqueId","getUUID","getUuid");
        String name=actor instanceof OfflinePlayer p?Objects.toString(p.getName(),""):Reflect.string(actor,"","getUsername","getName");
        if(actor instanceof UUID id){OfflinePlayer p=Bukkit.getOfflinePlayer(id);name=Objects.toString(p.getName(),"");}
        Map<String,Object> row=new LinkedHashMap<>();row.put("type","game.event");row.put("plugin",plugin);row.put("metric",metric);row.put("dimension",clean(dimension,128));row.put("value",value);row.put("count",1);
        if(uuid!=null)row.put("uuid",uuid.toString());row.put("username",clean(name,32));row.put("details",details);outbox.add(row);
    }

    private void registerHooks() {
        Plugin progression=Bukkit.getPluginManager().getPlugin(JourneyInsights.PLUGIN);
        if(progression!=null&&progression.isEnabled())journey.configure(progression.getDataFolder().toPath());
        hookFirst("ExcellentCrates",List.of("su.nightexpress.excellentcrates.api.reward.event.RewardGrantedEvent","su.nightexpress.excellentcrates.api.event.CrateObtainRewardEvent"),e->{
            Object crate=Reflect.call(e,"getCrate"),reward=Reflect.call(e,"getReward"),player=Reflect.call(e,"getPlayer");
            String id=label(crate),rid=label(reward);
            // EC 7 writes the confirmed batch size to its history log. A reward grant
            // can also be a manual grant and must not manufacture an opening.
            if(e.getClass().getSimpleName().equals("CrateObtainRewardEvent"))record("ExcellentCrates","crate_openings",id,player,1,Map.of("reward",rid,"source","legacy_confirmed_reward"));
            record("ExcellentCrates","crate_reward",id+"|"+rid,player,1,Map.of("crate",id,"reward",rid,"reward_name",name(reward),"weight",Reflect.number(reward,0,"getWeight")));
        });
        hook("QuickShop-Hikari","com.ghostchu.quickshop.api.event.economy.ShopSuccessPurchaseEvent",e->{
            Object player=Reflect.call(e,"getPurchaser"),shop=Reflect.call(e,"getShop");String item=item(shop);
            Map<String,Object> details=new LinkedHashMap<>();details.put("shop_id",Reflect.string(shop,"","getShopId","getId"));details.put("type",String.valueOf(Reflect.call(shop,"getShopType")));details.put("owner",Reflect.string(Reflect.call(shop,"getOwner"),"","getUsername"));details.put("tax",Reflect.number(e,0,"getTax"));details.put("items",Reflect.number(e,0,"getAmount"));details.put("value",Reflect.number(e,0,"getBalanceWithoutTax"));
            record("QuickShop-Hikari","shop_transactions",item,player,1,details);
            record("QuickShop-Hikari","items_traded",item,player,Reflect.number(e,0,"getAmount"),details);
            record("QuickShop-Hikari","trade_value",item,player,Reflect.number(e,0,"getBalanceWithoutTax"),details);
        });
        for(String type:List.of("Create","Delete"))hook("QuickShop-Hikari","com.ghostchu.quickshop.api.event.management.Shop"+type+"Event",e->record("QuickShop-Hikari","shops_"+type.toLowerCase(Locale.ROOT),item(Reflect.call(e,"shop","getShop")),Reflect.call(e,"user","getPlayer"),1,Map.of()));
        hook("Votifier","com.vexsoftware.votifier.model.VotifierEvent",e->{
            Object vote=Reflect.call(e,"getVote");String username=Reflect.string(vote,"","getUsername"),service=Reflect.string(vote,"unknown","getServiceName"),timestamp=Reflect.string(vote,"","getTimeStamp","getTimestamp");
            Map<String,Object> row=new LinkedHashMap<>(Map.of("type","game.event","plugin","Votifier","metric","votes","dimension",service,"username",username,"value",1,"details",Map.of("service",service)));
            if(!timestamp.isEmpty())row.put("event_id","vote-"+hash(service+":"+username+":"+timestamp));outbox.add(row);
        });
        hook("MCPets","fr.nocsy.mcpets.events.PetSpawnedEvent",e->{Object pet=Reflect.call(e,"getPet");record("MCPets","pet_summons",label(pet),Reflect.call(pet,"getOwner"),1,Map.of());});
        hook("MCPets","fr.nocsy.mcpets.events.PetChangeNameEvent",e->{Object pet=Reflect.call(e,"getPet");record("MCPets","pet_name_changes",label(pet),Reflect.call(pet,"getOwner"),1,Map.of());});
        hookFirst("PyroFishingPro",List.of("me.PyroAPI.Events.PyroFishingPro.PyroFishCatchEvent","me.arsmagica.API.PyroFishCatchEvent"),e->record("PyroFishingPro","fish_caught",Reflect.string(e,"unknown","getTier")+" #"+Reflect.string(e,"unknown","getFishId","getFishNumber"),Reflect.call(e,"getPlayer"),1,Map.of("hotspot",Reflect.bool(e,false,"isHotspot"))));
        discover("PyroFishingPro",Map.of("ShinyCatchEvent","shiny_fish_caught","SeaCreatureCatchEvent","sea_creatures_caught","FishingTournamentEndEvent","tournaments_completed"));
        discover("PinataParty",Map.of("PinataHitEvent","pinata_hits","PinataDamageEvent","pinata_hits","PinataDamagedEvent","pinata_hits","PinataDieEvent","pinata_kills","PinataDeathEvent","pinata_kills","PinataSpawnEvent","pinatas_spawned"));
        hook("TownyDragonEvent","net.townysmp.dragonevent.api.DragonResultEvent",e->{Object player=Reflect.call(e,"getPlayerId");Map<String,Object>d=Map.of("rank",(int)Reflect.number(e,0,"getRank"),"deaths",(int)Reflect.number(e,0,"getDeaths"),"crystals",(int)Reflect.number(e,0,"getCrystals"),"event",Reflect.string(e,"","getEventId"));
            String event=Reflect.string(e,"","getEventId"),uuid=Objects.toString(player,"");
            Map<String,Object> row=new LinkedHashMap<>(Map.of("type","game.event","event_id","dragon-"+hash(event+":"+uuid+":damage"),"plugin","TownyDragonEvent","metric","dragon_damage","dimension","fight","uuid",uuid,"username",Reflect.string(e,"","getPlayerName"),"value",Reflect.number(e,0,"getDamage"),"details",d));outbox.add(row);
            if(Reflect.number(e,0,"getRank")==1){row=new LinkedHashMap<>(row);row.put("metric","dragon_wins");row.put("value",1);row.put("event_id","dragon-"+hash(event+":"+uuid+":win"));outbox.add(row);}
        });
    }

    private void hookFirst(String plugin,List<String> classes,Consumer<Object> action){for(String type:classes)if(hook(plugin,type,action))return;}
    @SuppressWarnings("unchecked") private boolean hook(String plugin,String type,Consumer<Object> action) {
        if(registered.contains(type))return true;Plugin target=Bukkit.getPluginManager().getPlugin(plugin);if(target==null||!target.isEnabled())return false;
        try {
            Class<?> c=Class.forName(type,false,target.getClass().getClassLoader());if(!Event.class.isAssignableFrom(c))return false;
            Bukkit.getPluginManager().registerEvent((Class<? extends Event>)c,this,EventPriority.MONITOR,(listener,event)->{
                if(!c.isInstance(event)||event instanceof Cancellable cancelled&&cancelled.isCancelled())return;
                Object phase=Reflect.call(event,"phase");if(phase instanceof Enum<?> p&&!p.name().equals("POST"))return;
                try{action.accept(event);}catch(RuntimeException|LinkageError failure){if(unavailable.putIfAbsent(type,failure.getClass().getSimpleName())==null)owner.getLogger().warning("Game insights hook failed: "+type+" ("+failure.getClass().getSimpleName()+")");}
            },owner,true);
            registered.add(type);hooks.computeIfAbsent(plugin,k->new ArrayList<>()).add(type);return true;
        } catch(ReflectiveOperationException|LinkageError|RuntimeException ignored){return false;}
    }

    /** Locate actual exported event classes; avoids assuming one paid plugin's package layout. */
    private void discover(String plugin,Map<String,String> wanted) {
        Plugin target=Bukkit.getPluginManager().getPlugin(plugin);if(target==null)return;
        try(JarFile jar=new JarFile(Path.of(target.getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).toFile())) {
            var entries=jar.entries();while(entries.hasMoreElements()) {
                String path=entries.nextElement().getName();if(!path.endsWith(".class")||path.contains("$"))continue;
                String simple=path.substring(path.lastIndexOf('/')+1,path.length()-6),metric=wanted.get(simple);if(metric==null)continue;
                hook(plugin,path.substring(0,path.length()-6).replace('/','.'),e->{Object player=Reflect.call(e,"getPlayer","getDamager","getKiller");record(plugin,metric,"all",player,1,Map.of("source","native_event"));});
            }
        }catch(Exception|LinkageError ignored){}
    }

    Map<String,Object> coverage(String plugin) {
        List<String> nativeHooks=hooks.getOrDefault(plugin,List.of());
        Map<String,Object> data=new LinkedHashMap<>();data.put("insights_hooks",nativeHooks);data.put("insights_buffered",outbox.buffered());data.put("insights_rejected",outbox.rejected());
        if(!nativeHooks.isEmpty())data.put("coverage","native");
        if(JourneyInsights.PLUGIN.equals(plugin)) {
            data.put("coverage",journey.audited()?"native":"snapshot");
            data.put("insights_hooks",List.of(journey.audited()?"Durable Journey commit receipts":"Journey profile snapshots; install Journey beta.9 for event receipts"));
        }
        data.put("failed_hooks",unavailable.entrySet().stream().filter(e->nativeHooks.contains(e.getKey())).map(Map.Entry::getKey).toList());return data;
    }

    private void snapshot(String plugin,String key,Map<String,Object> data){outbox.add(Map.of("type","integration.snapshot","plugin",plugin,"snapshot_key",clean(key,96),"data",data));}
    private void snapshots() {
        try{crateCatalog();}catch(RuntimeException e){owner.getLogger().warning("Crate catalog snapshot unavailable: "+e.getClass().getSimpleName());}
        try{shopCatalog();petCatalog();permissions();}catch(RuntimeException|LinkageError e){owner.getLogger().warning("Optional catalog snapshot unavailable: "+e.getClass().getSimpleName());}
        Plugin dragon=Bukkit.getPluginManager().getPlugin("TownyDragonEvent");Object totals=Reflect.call(dragon,"getStaffStatistics");
        if(totals instanceof Map<?,?> map) { @SuppressWarnings("unchecked") Map<String,Object> data=(Map<String,Object>)map;snapshot("TownyDragonEvent","lifetime",data); }
        else reader.execute(this::dragonTotals);
    }
    private void crateCatalog() {
        Plugin p=Bukkit.getPluginManager().getPlugin("ExcellentCrates");if(p==null)return;
        Object manager=Reflect.call(p,"getCrateManager"),rewardAPI=null;
        Collection<?> crates=collection(Reflect.call(manager,"getCrates"));
        try {
            Class<?> apiType=Class.forName("su.nightexpress.excellentcrates.api.ExcellentCratesAPI",false,p.getClass().getClassLoader());
            Object api=Bukkit.getServicesManager().load(apiType);
            crates=collection(Reflect.call(Reflect.call(Reflect.call(api,"getCratesAPI"),"registry"),"crates"));
            Object optional=Reflect.call(api,"getRewardAPI");rewardAPI=optional instanceof Optional<?> o?o.orElse(null):optional;
        }catch(ClassNotFoundException ignored){}
        List<String> keys=new ArrayList<>();
        for(Object crate:crates) {
            Collection<?> available=collection(Reflect.call(crate,"getRewards"));
            if(rewardAPI!=null){String id=label(crate);available=collection(Reflect.call(rewardAPI,"getRewards")).stream().filter(r->Objects.toString(Reflect.call(Reflect.call(r,"getId"),"crateId"),"").equals(id)).toList();}
            List<Map<String,Object>> rewards=new ArrayList<>();for(Object reward:available)if(rewards.size()<500)rewards.add(Map.of("id",label(reward),"name",name(reward),"weight",Reflect.number(reward,0,"getWeight"),"rarity",label(Reflect.call(reward,"getRarity"))));
            String key="crate."+label(crate);keys.add(key);snapshot("ExcellentCrates",key,Map.of("id",label(crate),"name",name(crate),"rewards",rewards,"truncated",available.size()>rewards.size()));
        }
        snapshot("ExcellentCrates","catalog-manifest",Map.of("keys",keys,"prefix","crate."));
    }
    private void shopCatalog() {
        Plugin p=Bukkit.getPluginManager().getPlugin("QuickShop-Hikari");if(p==null)return;
        Object manager=Reflect.call(p,"getShopManager");Collection<?> shops=collection(Reflect.call(manager,"getAllShops","getLoadedShops"));
        List<Map<String,Object>> entries=new ArrayList<>();int part=0;
        for(Object shop:shops) {
            if(shop instanceof Map.Entry<?,?> entry)shop=entry.getValue();
            Map<String,Object> row=new LinkedHashMap<>();row.put("id",Reflect.string(shop,"","getShopId","getId"));row.put("name",item(shop));row.put("owner",Reflect.string(Reflect.call(shop,"getOwner"),"","getUsername"));row.put("price",Reflect.number(shop,0,"getPrice"));row.put("type",String.valueOf(Reflect.call(shop,"getShopType")));row.put("unlimited",Reflect.bool(shop,false,"isUnlimited"));
            Object location=Reflect.call(shop,"getLocation");if(location instanceof Location l)row.put("location",l.getWorld().getName()+" "+l.getBlockX()+", "+l.getBlockY()+", "+l.getBlockZ());
            entries.add(row);if(entries.size()==150){snapshot("QuickShop-Hikari","catalog."+part++,Map.of("entries",List.copyOf(entries)));entries.clear();}
        }
        snapshot("QuickShop-Hikari","catalog."+part,Map.of("entries",entries));
        List<String> keys=new ArrayList<>();for(int i=0;i<=part;i++)keys.add("catalog."+i);
        snapshot("QuickShop-Hikari","catalog-manifest",Map.of("keys",keys,"prefix","catalog."));
    }
    private void petCatalog() {
        Plugin p=Bukkit.getPluginManager().getPlugin("MCPets");if(p==null)return;
        try {
            Class<?> api=Class.forName("fr.nocsy.mcpets.api.MCPetsAPI",false,p.getClass().getClassLoader());List<Map<String,Object>> entries=new ArrayList<>();
            for(Object pet:collection(Reflect.callStatic(api,"getObjectPets")))entries.add(Map.of("id",label(pet),"name",name(pet),"permission",Reflect.string(pet,"","getPermission")));
            snapshot("MCPets","catalog",Map.of("entries",entries));
        }catch(ReflectiveOperationException|LinkageError ignored){}
    }
    private void permissions() {
        Plugin p=Bukkit.getPluginManager().getPlugin("LuckPerms");if(p==null)return;
        try {
            Class<?> provider=Class.forName("net.luckperms.api.LuckPermsProvider",false,p.getClass().getClassLoader());Object api=Reflect.callStatic(provider,"get");Object manager=Reflect.call(api,"getGroupManager");
            for(Object group:collection(Reflect.call(manager,"getLoadedGroups"))) {
                List<Map<String,Object>> nodes=new ArrayList<>();Collection<?> all=collection(Reflect.call(group,"getNodes"));
                for(Object node:all) {if(nodes.size()==1000)break;Object contexts=Reflect.call(Reflect.call(node,"getContexts"),"toMap");nodes.add(Map.of("key",Reflect.string(node,"","getKey"),"value",Reflect.bool(node,false,"getValue"),"contexts",contexts instanceof Map<?,?> m?m:Map.of()));}
                snapshot("LuckPerms","group."+label(group),Map.of("name",name(group),"nodes",nodes,"truncated",all.size()>nodes.size()));
            }
        }catch(ReflectiveOperationException|LinkageError ignored){}
    }
    private void dragonTotals() {
        Plugin p=Bukkit.getPluginManager().getPlugin("TownyDragonEvent");if(p==null)return;
        Path path=p.getDataFolder().toPath().resolve("stats.yml");if(!Files.isRegularFile(path))return;
        YamlConfiguration y=YamlConfiguration.loadConfiguration(path.toFile());ConfigurationSection root=y.getConfigurationSection("players");if(root==null)return;
        List<Map<String,Object>> entries=new ArrayList<>();for(String uuid:root.getKeys(false)){Map<String,Object> row=new LinkedHashMap<>(root.getConfigurationSection(uuid).getValues(false));row.put("uuid",uuid);row.putIfAbsent("name",uuid);entries.add(row);}
        snapshot("TownyDragonEvent","lifetime",Map.of("players",entries));
    }
    private void petLedger() {
        try {
            Plugin p=Bukkit.getPluginManager().getPlugin("TownyRebornPetShops");if(p==null)return;
            Path file=p.getDataFolder().toPath().resolve("transactions.yml");if(!Files.isRegularFile(file))return;
            YamlConfiguration y=YamlConfiguration.loadConfiguration(file.toFile());ConfigurationSection players=y.getConfigurationSection("players");if(players==null)return;
            List<Map<String,Object>> entries=new ArrayList<>();
            for(String uuid:players.getKeys(false)){ConfigurationSection player=players.getConfigurationSection(uuid);if(player==null)continue;for(String pet:player.getKeys(false)) {
                ConfigurationSection s=player.getConfigurationSection(pet);if(s==null||!"DONE".equals(s.getString("phase")))continue;
                String key=uuid+":"+pet;double price=s.getDouble("price");String source=s.getString("source","unknown");
                entries.add(Map.of("id",key,"name",pet,"player_uuid",uuid,"price",price,"source",source,"permission",s.getString("permission","")));
                if(!completedPets.contains(key)) {
                    if(petBaseline) {
                        Map<String,Object> event=new LinkedHashMap<>(Map.of("type","game.event","event_id","pet-"+hash(key),"plugin","TownyRebornPetShops","metric","pet_purchases","dimension",pet,"uuid",uuid,"value",1,"details",Map.of("price",price,"source",source,"timestamp_kind","first_observed")));
                        if(!outbox.add(event))continue;
                    }
                    completedPets.add(key);
                }
            }}
            snapshotEntries("TownyRebornPetShops","purchases",entries);
            if(!outbox.checkpoint())return;
            Path tmp=petCursor.resolveSibling(petCursor.getFileName()+".tmp");Files.writeString(tmp,Json.encode(completedPets));Files.move(tmp,petCursor,StandardCopyOption.REPLACE_EXISTING);petBaseline=true;
            Path pets=p.getDataFolder().toPath().resolve("shops");if(Files.isDirectory(pets)) {List<Map<String,Object>> catalog=new ArrayList<>();try(var stream=Files.list(pets)){for(Path pet:stream.filter(v->v.getFileName().toString().endsWith(".yml")).toList()){var data=YamlConfiguration.loadConfiguration(pet.toFile());Map<String,Object> row=new LinkedHashMap<>();row.put("id",pet.getFileName().toString().replaceFirst("\\.yml$",""));for(String field:List.of("name","ingame-price","store-price","permission","store-url","tebex.command","enabled")){Object v=data.get(field);if(v instanceof String||v instanceof Number||v instanceof Boolean)row.put(field,v);}row.put("price",data.getDouble("ingame-price",75000));row.put("tebex-command",data.getString("tebex.command",""));catalog.add(row);}}snapshotEntries("TownyRebornPetShops","catalog",catalog);}
        }catch(Exception e){owner.getLogger().warning("Pet ledger snapshot retained after "+e.getClass().getSimpleName());}
    }
    static Collection<?> collection(Object value){if(value instanceof Map<?,?> m)return m.values();if(value instanceof Collection<?> c)return c;return List.of();}
    private void snapshotEntries(String plugin,String key,List<Map<String,Object>> entries){
        List<String> keys=new ArrayList<>();for(int from=0,part=0;from<Math.max(1,entries.size());from+=100,part++){
            String chunk=key+"."+part;keys.add(chunk);snapshot(plugin,chunk,Map.of("entries",entries.subList(Math.min(from,entries.size()),Math.min(from+100,entries.size()))));
        }snapshot(plugin,key+"-manifest",Map.of("prefix",key+".","keys",keys));
    }
    static String label(Object v){Object id=Reflect.call(v,"getId","getID","getName");Object part=Reflect.call(id,"rewardId");return clean(Objects.toString(part==null?id:part,"unknown"),128);}
    static String name(Object v){Object display=Reflect.call(v,"getDisplay","getPreview");return clean(Reflect.string(display,Reflect.string(v,label(v),"getDisplayName","getName"),"getName"),200);}
    static String clean(String v,int max){return TelemetryBuffer.clean(ChatColor.stripColor(Objects.toString(v,"")).replaceAll("<[^>]*>",""),max);}
    static String item(Object v){Object stack=Reflect.call(v,"getItem","getItemStack");return stack instanceof org.bukkit.inventory.ItemStack i?i.getType().name():label(v);}
    static String hash(String value){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    @Override public void close(){if(snapshotTask>=0)Bukkit.getScheduler().cancelTask(snapshotTask);HandlerList.unregisterAll(this);reader.shutdownNow();}
}
