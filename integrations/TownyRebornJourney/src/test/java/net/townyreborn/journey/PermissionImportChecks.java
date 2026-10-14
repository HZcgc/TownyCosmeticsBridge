package net.townyreborn.journey;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import com.google.gson.*;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.*;
import net.luckperms.api.model.data.*;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.context.ImmutableContextSet;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import static org.mockito.Mockito.*;

/** Exercise the actual permanent-node importer; no live LP mutations or payments. */
public final class PermissionImportChecks {
    static int checks; static Journey journey; static YamlConfiguration config;
    interface Check { void run() throws Exception; }
    static void test(String name,Check body) throws Exception {body.run();checks++;System.out.println("PASS "+name);}
    static void require(boolean ok){if(!ok)throw new AssertionError("Permission import regression");}
    static Node node(String key,boolean value,boolean expiry,Map<String,Set<String>> context){
        Node n=mock(PermissionNode.class);when(n.getKey()).thenReturn(key);when(n.getValue()).thenReturn(value);when(n.hasExpiry()).thenReturn(expiry);
        ImmutableContextSet set=mock(ImmutableContextSet.class);when(set.toMap()).thenReturn(context);when(set.getValues("server")).thenReturn(context.getOrDefault("server",Set.of()));
        when(n.getContexts()).thenReturn(set);return n;
    }
    static Integrations integration(UUID id,List<Node> nodes) throws Exception {
        RebornPlugin p=mock(RebornPlugin.class);p.journey=journey;when(p.getConfig()).thenReturn(config);
        LuckPerms luck=mock(LuckPerms.class);UserManager manager=mock(UserManager.class);User user=mock(User.class);
        List<Node> stored=new ArrayList<>(nodes);NodeMap data=mock(NodeMap.class);when(user.data()).thenReturn(data);when(data.toCollection()).thenReturn(stored);
        when(luck.getUserManager()).thenReturn(manager);when(manager.getUser(id)).thenReturn(user);when(user.getNodes()).thenReturn(stored);
        when(manager.saveUser(user)).thenReturn(CompletableFuture.completedFuture(null));
        when(data.add(any(Node.class))).thenAnswer(a->{stored.add(a.getArgument(0));return DataMutateResult.SUCCESS;});
        when(data.remove(any(Node.class))).thenAnswer(a->{stored.remove(a.getArgument(0));return DataMutateResult.SUCCESS;});
        Integrations backend=mock(Integrations.class,CALLS_REAL_METHODS);
        for(var e:Map.of("plugin",p,"luck",luck).entrySet()){var field=Integrations.class.getDeclaredField(e.getKey());field.setAccessible(true);field.set(backend,e.getValue());}
        return backend;
    }
    static org.mockito.MockedStatic<PermissionNode> builders() {
        var registry=mockStatic(PermissionNode.class);
        registry.when(()->PermissionNode.builder(anyString())).thenAnswer(a->{
            var builder=mock(PermissionNode.Builder.class,RETURNS_SELF);
            PermissionNode built=(PermissionNode)node(a.getArgument(0),true,false,Map.of("server",Set.of("smp")));
            when(builder.build()).thenReturn(built);return builder;
        });return registry;
    }
    public static void main(String[] args) throws Exception {
        config=JourneyUpdateChecks.readJar(args[0]);journey=new Journey(config);
        test("old direct permanent purchase markers restore ownership at Journey level one",()->{
            State s=new State(UUID.randomUUID(),"Test");s.points=4;var b=integration(s.uuid,List.of(node("townyreborn.perk.homes.12",true,false,Map.of("server",Set.of("smp")))));
            List<String> grants=b.importOwned(s);require(s.points==4&&s.owned.containsAll(List.of("homes8","homes12"))&&s.unlocked.containsAll(s.owned));
            require(grants.contains("essentials.sethome.multiple.creeper")&&grants.contains(Journey.perkMarker("homes12")));
        });
        test("temporary false and unrelated-context nodes cannot prove a permanent purchase",()->{
            State s=new State(UUID.randomUUID(),"Test");var b=integration(s.uuid,List.of(
                node("townyreborn.perk.fly",true,true,Map.of()),node("townyreborn.perk.homes.8",false,false,Map.of()),
                node("townyreborn.perk.vaults.4",true,false,Map.of("server",Set.of("hub"))),node("townyreborn.perk.trash",true,false,Map.of("world",Set.of("earth")))));
            b.importOwned(s);require(s.owned.isEmpty());
        });
        test("owner wildcard and supporter membership stay separate from paid ownership",()->{
            State s=new State(UUID.randomUUID(),"Test");var b=integration(s.uuid,List.of(node("*",true,false,Map.of()),node("group.owner",true,false,Map.of()),node("group.wither",true,false,Map.of())));
            b.importOwned(s);require(s.owned.isEmpty());Player p=mock(Player.class);when(p.hasPermission(anyString())).thenReturn(true);
            require(b.hasFeatureAccess(p,journey.perks.get("fly"))&&s.owned.isEmpty());
        });
        test("missing LP nodes do not remove already recorded purchases",()->{
            State s=new State(UUID.randomUUID(),"Test");s.owned.add("fly");var b=integration(s.uuid,List.of());List<String> grants=b.importOwned(s);
            require(s.owned.contains("fly")&&grants.contains("essentials.fly")&&s.unlocked.contains("fly"));
        });
        test("old direct town-flight purchase migrates to all towns without wilderness access or payment",()->{
            State s=new State(UUID.randomUUID(),"Test");s.points=7;Node old=node("townyflight.bypass",true,false,Map.of("server",Set.of("smp")));
            var b=integration(s.uuid,List.of(old));List<String> grants=b.importOwned(s);
            require(s.owned.containsAll(List.of("town_flight_bypass","tfly"))&&grants.contains("townyflight.alltowns")&&!grants.contains("townyflight.bypass"));
            try(var ignored=builders()){b.repairOwned(s,grants).join();}
            User user=b.luck.getUserManager().getUser(s.uuid);Set<String> nodes=b.explicitPermanentNodes(s.uuid);
            require(nodes.containsAll(List.of("townyflight.alltowns","townyflight.command.tfly",Journey.perkMarker("town_flight_bypass")))
                &&!nodes.contains("townyflight.bypass")&&!nodes.contains("townyflight.wilderness")&&!nodes.contains("essentials.fly")&&s.points==7);
            verify(user.data()).remove(old);List<Node> after=List.copyOf(user.data().toCollection());b.repairOwned(s,b.importOwned(s)).join();
            require(user.data().toCollection().equals(after)&&s.owned.contains("town_flight_bypass"));
        });
        test("town-flight migration preserves temporary negative world hub multi-server and other flight nodes",()->{
            State s=new State(UUID.randomUUID(),"Test");s.owned.add("town_flight_bypass");
            Node global=node("townyflight.bypass",true,false,Map.of());Node scoped=node("townyflight.bypass",true,false,Map.of("server",Set.of("smp")));
            List<Node> keep=List.of(node("townyflight.bypass",true,true,Map.of()),node("townyflight.bypass",false,false,Map.of()),
                node("townyflight.bypass",true,false,Map.of("world",Set.of("earth"))),node("townyflight.bypass",true,false,Map.of("server",Set.of("hub"))),
                node("townyflight.bypass",true,false,Map.of("server",Set.of("smp","hub"))),node("essentials.fly",true,false,Map.of()),node("group.helper",true,false,Map.of()));
            List<Node> original=new ArrayList<>(keep);original.add(global);original.add(scoped);var b=integration(s.uuid,original);
            try(var ignored=builders()){b.repairOwned(s,b.importOwned(s)).join();}
            var remaining=b.luck.getUserManager().getUser(s.uuid).data().toCollection();require(remaining.containsAll(keep)&&!remaining.contains(global)&&!remaining.contains(scoped));
        });
        test("staff and supporter memberships do not trigger a town-flight purchase migration",()->{
            State s=new State(UUID.randomUUID(),"Test");var b=integration(s.uuid,List.of(node("group.helper",true,false,Map.of()),node("group.wither",true,false,Map.of()),node("*",true,false,Map.of())));
            List<String> grants=b.importOwned(s);b.repairOwned(s,grants).join();require(grants.isEmpty()&&s.owned.isEmpty());
            Player p=mock(Player.class);when(p.hasPermission("townyflight.bypass")).thenReturn(true);
            require(b.hasFeatureAccess(p,journey.perks.get("town_flight_bypass"))&&s.owned.isEmpty());
            verify(b.luck.getUserManager(),never()).saveUser(any(User.class));verify(b.luck.getUserManager().getUser(s.uuid).data(),never()).remove(any(Node.class));
        });
        test("transient flight access cannot prove ownership or trigger a permanent flight migration",()->{
            State s=new State(UUID.randomUUID(),"Test");var b=integration(s.uuid,List.of());User user=b.luck.getUserManager().getUser(s.uuid);
            List<Node> transientNodes=List.of(node("townyflight.bypass",true,false,Map.of()),node(Journey.perkMarker("town_flight_bypass"),true,false,Map.of()));
            when(user.getNodes()).thenReturn(transientNodes);
            List<String> grants=b.importOwned(s);b.repairOwned(s,grants).join();require(grants.isEmpty()&&s.owned.isEmpty());verify(user.data(),never()).remove(any(Node.class));
        });
        test("town-flight permission save retries safely with unchanged ownership and points",()->{
            State s=new State(UUID.randomUUID(),"Test");s.points=7;var b=integration(s.uuid,List.of(node("townyflight.bypass",true,false,Map.of())));User user=b.luck.getUserManager().getUser(s.uuid);
            when(b.luck.getUserManager().saveUser(user)).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("LP storage unavailable")),CompletableFuture.completedFuture(null));
            try(var ignored=builders()){
                boolean failed=false;try{b.repairOwned(s,b.importOwned(s)).join();}catch(java.util.concurrent.CompletionException expected){failed=true;}require(failed);
                require(b.importOwned(s).isEmpty());b.repairOwned(s,List.of()).join();
            }
            verify(b.luck.getUserManager(),times(2)).saveUser(user);require(s.points==7&&s.owned.equals(Set.of("town_flight_bypass","tfly")));
        });
        if(args.length>1){
            JsonArray cases=JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonArray();int index=0;
            for(JsonElement row:cases){JsonObject c=row.getAsJsonObject();int number=++index;
                test("anonymous exported permission case "+number+" keeps confirmed purchases",()->{
                    List<Node> nodes=new ArrayList<>();
                    for(JsonElement elem:c.getAsJsonArray("nodes")){JsonObject n=elem.getAsJsonObject();Map<String,Set<String>> ctx=new HashMap<>();
                        if(n.has("context"))for(var e:n.getAsJsonObject("context").entrySet()){
                            Set<String> values=new HashSet<>();if(e.getValue().isJsonArray())e.getValue().getAsJsonArray().forEach(v->values.add(v.getAsString()));else values.add(e.getValue().getAsString());ctx.put(e.getKey(),values);
                        }
                        nodes.add(node(n.get("key").getAsString(),n.get("value").getAsBoolean(),n.has("expiry")&&!n.get("expiry").isJsonNull(),ctx));
                    }
                    State s=new State(UUID.randomUUID(),"Test");s.points=4;integration(s.uuid,nodes).importOwned(s);
                    Set<String> expected=new HashSet<>();c.getAsJsonArray("expected").forEach(v->expected.add(v.getAsString()));journey.inheritLowerPerks(expected);
                    require(s.owned.equals(expected)&&s.points==4&&s.unlocked.containsAll(expected));
                });
            }
        }
        System.out.println("RESULT "+checks+" permission import checks passed");
    }
}
