package com.townyreborn.petshops;

import java.lang.reflect.Method;
import java.util.*;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

/** Public APIs, resolved from the installed Citizens and ModelEngine plugins. */
final class Integrations {
    private Integrations() {}
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
        Object create(String name) throws ReflectiveOperationException {
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
                return npc;
            } catch (ReflectiveOperationException|RuntimeException e) {
                try { destroy(npc); } catch (ReflectiveOperationException cleanup) { e.addSuppressed(cleanup); }
                throw e;
            }
        }
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
        private final Class<?> api, modeled, active, animations, base;
        final Class<? extends Event> interaction;
        Models(Plugin plugin) throws ReflectiveOperationException {
            ClassLoader l=plugin.getClass().getClassLoader();
            api=Class.forName("com.ticxo.modelengine.api.ModelEngineAPI",true,l);
            modeled=Class.forName("com.ticxo.modelengine.api.model.ModeledEntity",true,l);
            active=Class.forName("com.ticxo.modelengine.api.model.ActiveModel",true,l);
            animations=Class.forName("com.ticxo.modelengine.api.animation.handler.AnimationHandler",true,l);
            base=Class.forName("com.ticxo.modelengine.api.entity.BaseEntity",true,l);
            interaction=Class.forName("com.ticxo.modelengine.api.events.BaseEntityInteractEvent",true,l).asSubclass(Event.class);
        }
        boolean has(String id) throws ReflectiveOperationException { return api.getMethod("getBlueprint",String.class).invoke(null,id)!=null; }
        Handle attach(Entity entity, String id, String idle) throws ReflectiveOperationException {
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
                if (!idle.isBlank()) {
                    Object handler=active.getMethod("getAnimationHandler").invoke(model);
                    animations.getMethod("playAnimation",String.class,double.class,double.class,double.class,boolean.class)
                        .invoke(handler,idle,0.1,0.1,1.0,true);
                }
                return handle;
            } catch (ReflectiveOperationException|RuntimeException e) { detach(handle); throw e; }
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
        boolean rightClick(Event event) throws ReflectiveOperationException {
            return ((Enum<?>)interaction.getMethod("getAction").invoke(event)).name().contains("INTERACT")
                && interaction.getMethod("getSlot").invoke(event)==org.bukkit.inventory.EquipmentSlot.HAND;
        }
    }
}
