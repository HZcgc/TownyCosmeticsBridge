package com.townyreborn.petshops;

import java.lang.reflect.Method;
import java.util.*;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

/** Public APIs, resolved from the installed Citizens, ModelEngine and MCPets plugins. */
final class Integrations {
    private Integrations() {}
    enum Click { SHOP, INTERACT, IGNORE }
    static final class MCPets {
        private final Class<?> pet, globals, utils;
        private final Method find, owner, present, currentName, rename, limit;
        final Class<? extends Event> interaction;
        private final Method eventPet;
        MCPets(Plugin plugin) throws ReflectiveOperationException {
            var loader=plugin.getClass().getClassLoader();
            pet=Class.forName("fr.nocsy.mcpets.data.Pet",false,loader);
            globals=Class.forName("fr.nocsy.mcpets.data.config.GlobalConfig",false,loader);
            utils=Class.forName("fr.nocsy.mcpets.utils.Utils",false,loader);
            find=pet.getMethod("getFromEntity",Entity.class); owner=pet.getMethod("getOwner");
            present=pet.getMethod("isStillHere"); currentName=pet.getMethod("getCurrentName");
            rename=pet.getMethod("setDisplayName",String.class,boolean.class);
            Method length;
            try { length=globals.getMethod("getMaxNameLength"); }
            catch (NoSuchMethodException e) { length=globals.getMethod("getMaxNameLenght"); } // MCPets 4.1.5
            limit=length;
            interaction=Class.forName("fr.nocsy.mcpets.events.PetOwnerInteractEvent",false,loader).asSubclass(Event.class);
            eventPet=interaction.getMethod("getPet");
        }
        Object find(Entity entity) throws ReflectiveOperationException { return find.invoke(null,entity); }
        Object eventPet(Event event) throws ReflectiveOperationException { return eventPet.invoke(event); }
        UUID owner(Object value) throws ReflectiveOperationException { return (UUID)owner.invoke(value); }
        boolean present(Object value) throws ReflectiveOperationException { return (boolean)present.invoke(value); }
        String currentName(Object value) throws ReflectiveOperationException { return (String)currentName.invoke(value); }
        int maxNameLength() throws ReflectiveOperationException {
            Object config=globals.getMethod("getInstance").invoke(null);
            return Math.clamp(((Number)limit.invoke(config)).intValue(),1,128);
        }
        String blockedWord(String name) throws ReflectiveOperationException {
            return (String)utils.getMethod("isInBlackList",String.class).invoke(null,name);
        }
        void rename(Object value,String name) throws ReflectiveOperationException {
            rename.invoke(value,name,true); // The native player database persists the name across respawns/restarts.
            if (!Objects.equals(name,currentName(value))) throw new IllegalStateException("The pet name could not be applied.");
        }
        void openMenu(Player player,Object value) throws ReflectiveOperationException {
            pet.getMethod("setLastInteractedWith",Player.class).invoke(value,player);
            Class<?> menu=Class.forName("fr.nocsy.mcpets.data.inventories.PetInteractionMenu",true,pet.getClassLoader());
            Object instance=menu.getConstructor(pet,UUID.class).newInstance(value,player.getUniqueId());
            menu.getMethod("open",Player.class).invoke(instance,player);
        }
    }
    static final class Citizens {
        private final Class<?> api, npcType, registryType, metadataType, selectorType, metadataEnum;
        private final ClassLoader loader;
        Citizens(Plugin plugin) throws ReflectiveOperationException {
            loader=plugin.getClass().getClassLoader();
            api=load("net.citizensnpcs.api.CitizensAPI"); npcType=load("net.citizensnpcs.api.npc.NPC");
            registryType=load("net.citizensnpcs.api.npc.NPCRegistry");
            metadataType=load("net.citizensnpcs.api.npc.MetadataStore");
            metadataEnum=load("net.citizensnpcs.api.npc.NPC$Metadata");
            selectorType=load("net.citizensnpcs.api.npc.NPCSelector");
        }
        private Class<?> load(String name) throws ClassNotFoundException { return Class.forName(name,true,loader); }
        private Object registry() throws ReflectiveOperationException { return api.getMethod("getNPCRegistry").invoke(null); }
        private Object call(Object npc, String method, Class<?>[] types, Object... args) throws ReflectiveOperationException {
            return npcType.getMethod(method,types).invoke(npc,args);
        }
        Object create(String name, int renderDistance) throws ReflectiveOperationException {
            Object npc=registryType.getMethod("createNPC",EntityType.class,String.class).invoke(registry(),EntityType.ARMOR_STAND,name);
            try {
                // Only the pet model should ever be visible, including before
                // ModelEngine finishes loading or when it is unavailable.
                Class<?> traitType=load("net.citizensnpcs.trait.ArmorStandTrait");
                Object trait=call(npc,"getOrAddTrait",new Class<?>[]{Class.class},traitType);
                for (String setter:List.of("setVisible","setGravity","setHasArms","setHasBaseplate"))
                    traitType.getMethod(setter,boolean.class).invoke(trait,false);
                traitType.getMethod("setMarker",boolean.class).invoke(trait,true);
                call(npc,"setProtected",new Class<?>[]{boolean.class},true);
                call(npc,"setUseMinecraftAI",new Class<?>[]{boolean.class},false);
                metadata(npc,"NAMEPLATE_VISIBLE",false); metadata(npc,"COLLIDABLE",false);
                metadata(npc,"SILENT",true); metadata(npc,"KEEP_CHUNK_LOADED",false);
                tracking(npc, renderDistance);
                return npc;
            } catch (ReflectiveOperationException|RuntimeException e) {
                try { destroy(npc); } catch (ReflectiveOperationException cleanup) { e.addSuppressed(cleanup); }
                throw e;
            }
        }
        void tracking(Object npc, int distance) throws ReflectiveOperationException { metadata(npc,"TRACKING_RANGE",distance); }
        private void metadata(Object npc, String name, Object value) throws ReflectiveOperationException {
            Object data=call(npc,"data",new Class<?>[0]);
            Object key=Arrays.stream(metadataEnum.getEnumConstants()).filter(e->((Enum<?>)e).name().equals(name)).findFirst().orElseThrow();
            String text=(String)metadataEnum.getMethod("getKey").invoke(key);
            metadataType.getMethod("setPersistent",String.class,Object.class).invoke(data,text,value);
        }
        Object get(int id, UUID uuid) throws ReflectiveOperationException {
            Object npc=registryType.getMethod("getById",int.class).invoke(registry(),id);
            return npc!=null && uuid.equals(uuid(npc)) ? npc : null;
        }
        Object get(int id) throws ReflectiveOperationException { return registryType.getMethod("getById",int.class).invoke(registry(),id); }
        Object selected(CommandSender sender) throws ReflectiveOperationException {
            Object selector=api.getMethod("getDefaultNPCSelector").invoke(null);
            return selectorType.getMethod("getSelected",CommandSender.class).invoke(selector,sender);
        }
        int id(Object npc) throws ReflectiveOperationException { return (Integer)call(npc,"getId",new Class<?>[0]); }
        UUID uuid(Object npc) throws ReflectiveOperationException { return (UUID)call(npc,"getUniqueId",new Class<?>[0]); }
        Entity entity(Object npc) throws ReflectiveOperationException { return (Entity)call(npc,"getEntity",new Class<?>[0]); }
        boolean spawned(Object npc) throws ReflectiveOperationException { return Boolean.TRUE.equals(call(npc,"isSpawned",new Class<?>[0])); }
        boolean spawn(Object npc, Location location) throws ReflectiveOperationException {
            return Boolean.TRUE.equals(call(npc,"spawn",new Class<?>[]{Location.class},location));
        }
        void destroy(Object npc) throws ReflectiveOperationException { call(npc,"destroy",new Class<?>[0]); }
        void save() throws ReflectiveOperationException { registryType.getMethod("saveToStore").invoke(registry()); }
        Object eventNpc(Event e) throws ReflectiveOperationException { return e.getClass().getMethod("getNPC").invoke(e); }
        Player clicker(Event e) throws ReflectiveOperationException { return (Player)e.getClass().getMethod("getClicker").invoke(e); }
        Class<? extends Event> event(String name) throws ClassNotFoundException { return load("net.citizensnpcs.api.event."+name).asSubclass(Event.class); }
    }
    static final class Models {
        record Handle(UUID uuid, Object entity, Object model) {}
        record Playback(String animation, long durationMillis) {}
        private final Class<?> api, modeled, active, animations, base, data, blueprint, animation, property, loop;
        final Class<? extends Event> interaction;
        Models(Plugin plugin) throws ReflectiveOperationException {
            this(plugin.getClass().getClassLoader());
        }
        Models(ClassLoader l) throws ReflectiveOperationException {
            api=Class.forName("com.ticxo.modelengine.api.ModelEngineAPI",false,l);
            modeled=Class.forName("com.ticxo.modelengine.api.model.ModeledEntity",true,l);
            active=Class.forName("com.ticxo.modelengine.api.model.ActiveModel",true,l);
            animations=Class.forName("com.ticxo.modelengine.api.animation.handler.AnimationHandler",true,l);
            base=Class.forName("com.ticxo.modelengine.api.entity.BaseEntity",true,l);
            data=Class.forName("com.ticxo.modelengine.api.entity.data.IEntityData",true,l);
            blueprint=Class.forName("com.ticxo.modelengine.api.generator.blueprint.ModelBlueprint",true,l);
            animation=Class.forName("com.ticxo.modelengine.api.animation.BlueprintAnimation",true,l);
            property=Class.forName("com.ticxo.modelengine.api.animation.property.IAnimationProperty",true,l);
            loop=Class.forName("com.ticxo.modelengine.api.animation.BlueprintAnimation$LoopMode",true,l);
            interaction=Class.forName("com.ticxo.modelengine.api.events.BaseEntityInteractEvent",true,l).asSubclass(Event.class);
        }
        boolean has(String id) throws ReflectiveOperationException { return api.getMethod("getBlueprint",String.class).invoke(null,id)!=null; }
        Handle attach(Entity entity, String id, String idle, int renderDistance) throws ReflectiveOperationException {
            Object model=api.getMethod("createActiveModel",String.class).invoke(null,id);
            if (model==null) throw new IllegalArgumentException("Model is not loaded: "+id);
            Object holder=api.getMethod("createModeledEntity",Entity.class).invoke(null,entity);
            var handle=new Handle(entity.getUniqueId(),holder,model);
            try {
                active.getMethod("setCanHurt",boolean.class).invoke(model,false);
                modeled.getMethod("addModel",active,boolean.class).invoke(holder,model,false);
                modeled.getMethod("setBaseEntityVisible",boolean.class).invoke(holder,false);
                modeled.getMethod("setSaved",boolean.class).invoke(holder,false);
                modeled.getMethod("setYBodyRotImmediately",float.class).invoke(holder,entity.getLocation().getYaw());
                modeled.getMethod("setYHeadRotImmediately",float.class).invoke(holder,entity.getLocation().getYaw());
                modeled.getMethod("setModelRotationLocked",boolean.class).invoke(holder,true);
                rendering(handle, renderDistance);
                if (!idle.isBlank()) {
                    Object handler=active.getMethod("getAnimationHandler").invoke(model);
                    animations.getMethod("playAnimation",String.class,double.class,double.class,double.class,boolean.class)
                        .invoke(handler,idle,0.1,0.1,1.0,true);
                }
                return handle;
            } catch (ReflectiveOperationException|RuntimeException e) { detach(handle); throw e; }
        }
        Handle existing(Entity entity, String id) throws ReflectiveOperationException {
            Object holder=api.getMethod("getModeledEntity",UUID.class).invoke(null,entity.getUniqueId());
            if (holder==null) return null;
            Object models=modeled.getMethod("getModels").invoke(holder);
            if (!(models instanceof Map<?,?> entries) || entries.isEmpty()) return null;
            Object model=entries.get(id);
            if (model==null) model=entries.values().iterator().next();
            return new Handle(entity.getUniqueId(),holder,model);
        }
        void rendering(Handle handle, int distance) throws ReflectiveOperationException {
            Object b=modeled.getMethod("getBase").invoke(handle.entity());
            base.getMethod("setRenderRadius",int.class).invoke(b,distance);
            Object settings=base.getMethod("getData").invoke(b);
            if (settings!=null) {
                // Podest blocks can occlude the invisible armor-stand carrier.
                // Override culling only for this model, within its render radius.
                data.getMethod("setBlockedCull",Boolean.class).invoke(settings,Boolean.FALSE);
                data.getMethod("setBackCull",Boolean.class).invoke(settings,Boolean.FALSE);
                data.getMethod("setVerticalCullDistance",Double.class).invoke(settings,(double)distance);
            }
        }
        void face(Handle handle, float yaw) throws ReflectiveOperationException {
            modeled.getMethod("setYBodyRotImmediately",float.class).invoke(handle.entity(),yaw);
            modeled.getMethod("setYHeadRotImmediately",float.class).invoke(handle.entity(),yaw);
        }
        @SuppressWarnings("unchecked") private Map<String,Object> clips(Handle handle) throws ReflectiveOperationException {
            Object source=active.getMethod("getBlueprint").invoke(handle.model());
            return (Map<String,Object>)blueprint.getMethod("getAnimations").invoke(source);
        }
        static String chooseInteraction(Collection<String> clips, String configured) {
            if (configured.equalsIgnoreCase("off")) return null;
            if (!configured.equalsIgnoreCase("auto")) return clips.contains(configured)?configured:null;
            for (String name:List.of("interact","interaction","rightclick","right_click","greet","wave","honk"))
                if (clips.contains(name)) return name;
            Set<String> movement=Set.of("idle","walk","run","spawn","death","hurt","jump","fall","fly","sit","swim","attack");
            return clips.stream().filter(name->!movement.contains(name.toLowerCase(Locale.ROOT))).sorted().findFirst()
                .orElse(clips.contains("spawn")?"spawn":null);
        }
        Playback interact(Handle handle, String configured) throws ReflectiveOperationException {
            var clips=clips(handle);
            String selected=chooseInteraction(clips.keySet(),configured);
            if (selected==null) return null;
            Object handler=active.getMethod("getAnimationHandler").invoke(handle.model());
            Object played=animations.getMethod("playAnimation",String.class,double.class,double.class,double.class,boolean.class)
                .invoke(handler,selected,0.1,0.1,1.0,true);
            if (played==null) return null;
            Object once=Arrays.stream(loop.getEnumConstants()).filter(value->((Enum<?>)value).name().equals("ONCE")).findFirst().orElseThrow();
            property.getMethod("setForceLoopMode",loop).invoke(played,once);
            double length=((Number)animation.getMethod("getLength").invoke(clips.get(selected))).doubleValue();
            return new Playback(selected,Math.clamp((long)Math.ceil((length+0.2)*1000),350,60000));
        }
        void detach(Handle handle) throws ReflectiveOperationException {
            api.getMethod("removeModeledEntity",UUID.class).invoke(null,handle.uuid());
            modeled.getMethod("destroy").invoke(handle.entity());
        }
        UUID baseId(Event event) throws ReflectiveOperationException {
            Object b=interaction.getMethod("getBaseEntity").invoke(event);
            return (UUID)base.getMethod("getUUID").invoke(b);
        }
        Player player(Event event) throws ReflectiveOperationException { return (Player)interaction.getMethod("getPlayer").invoke(event); }
        Click click(Event event) throws ReflectiveOperationException {
            String action=((Enum<?>)interaction.getMethod("getAction").invoke(event)).name();
            if (action.equals("ATTACK")) return Click.SHOP;
            return action.contains("INTERACT") && interaction.getMethod("getSlot").invoke(event)==org.bukkit.inventory.EquipmentSlot.HAND
                ? Click.INTERACT : Click.IGNORE;
        }
    }
}
