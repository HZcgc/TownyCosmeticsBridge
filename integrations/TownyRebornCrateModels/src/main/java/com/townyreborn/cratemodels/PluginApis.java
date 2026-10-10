package com.townyreborn.cratemodels;

import java.lang.reflect.Method;
import com.townyreborn.compat.ExcellentCrates7;
import org.bukkit.Location;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

final class PluginApis {
    private final ExcellentCrates7 crates;
    private final Method blueprint;
    private final Method createEntity;
    private final Method createModel;
    private final Method removeEntity;
    private final Method addModel;
    private final Method visible;
    private final Method saved;
    private final Method rotationLocked;
    private final Method bodyRot;
    private final Method headRot;
    private final Method pitchRot;
    private final Method destroy;
    private final Method animationHandler;
    private final Method canHurt;
    private final Method play;
    private final Method stop;
    private final Method modeledBase;
    private final Method renderRadius;
    private final Method baseData;
    private final Method blockedCull;
    private final Method backCull;
    private final Method verticalCull;
    private final Method viewRange;
    final Class<? extends Event> openEvent;
    final Class<? extends Event> modelInteractEvent;
    private final Method openPlayer;
    private final Method openCrate;
    private final Method interactPlayer;
    private final Method interactBase;
    private final Method interactAction;
    private final Method interactSlot;
    private final Method baseUuid;

    PluginApis(Plugin plugin, Plugin plugin2) throws ReflectiveOperationException {
        this.crates = new ExcellentCrates7(plugin2);
        this.openEvent = this.crates.openingEvent();
        this.openPlayer = this.openEvent.getMethod("getPlayer");
        this.openCrate = this.openEvent.getMethod("getCrate");
        ClassLoader classLoader2 = plugin.getClass().getClassLoader();
        Class<?> clazz5 = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI", true, classLoader2);
        Class<?> clazz6 = Class.forName("com.ticxo.modelengine.api.model.ModeledEntity", true, classLoader2);
        Class<?> clazz7 = Class.forName("com.ticxo.modelengine.api.model.ActiveModel", true, classLoader2);
        Class<?> clazz8 = Class.forName("com.ticxo.modelengine.api.animation.handler.AnimationHandler", true, classLoader2);
        this.blueprint = clazz5.getMethod("getBlueprint", String.class);
        this.createEntity = clazz5.getMethod("createModeledEntity", Entity.class);
        this.createModel = clazz5.getMethod("createActiveModel", String.class);
        this.removeEntity = clazz5.getMethod("removeModeledEntity", UUID.class);
        this.addModel = clazz6.getMethod("addModel", clazz7, Boolean.TYPE);
        this.visible = clazz6.getMethod("setBaseEntityVisible", Boolean.TYPE);
        this.saved = clazz6.getMethod("setSaved", Boolean.TYPE);
        this.rotationLocked = clazz6.getMethod("setModelRotationLocked", Boolean.TYPE);
        this.bodyRot = clazz6.getMethod("setYBodyRotImmediately", Float.TYPE);
        this.headRot = clazz6.getMethod("setYHeadRotImmediately", Float.TYPE);
        this.pitchRot = clazz6.getMethod("setXHeadRotImmediately", Float.TYPE);
        this.destroy = clazz6.getMethod("destroy", new Class[0]);
        this.animationHandler = clazz7.getMethod("getAnimationHandler", new Class[0]);
        this.canHurt = clazz7.getMethod("setCanHurt", Boolean.TYPE);
        this.play = clazz8.getMethod("playAnimation", String.class, Double.TYPE, Double.TYPE, Double.TYPE, Boolean.TYPE);
        this.stop = clazz8.getMethod("forceStopAnimation", String.class);
        this.modelInteractEvent = Class.forName("com.ticxo.modelengine.api.events.BaseEntityInteractEvent", true, classLoader2).asSubclass(Event.class);
        this.interactPlayer = this.modelInteractEvent.getMethod("getPlayer", new Class[0]);
        this.interactBase = this.modelInteractEvent.getMethod("getBaseEntity", new Class[0]);
        this.interactAction = this.modelInteractEvent.getMethod("getAction", new Class[0]);
        this.interactSlot = this.modelInteractEvent.getMethod("getSlot", new Class[0]);
        Class<?> clazz9 = Class.forName("com.ticxo.modelengine.api.entity.BaseEntity", true, classLoader2);
        this.baseUuid = clazz9.getMethod("getUUID", new Class[0]);
        Class<?> clazz10 = Class.forName("com.ticxo.modelengine.api.entity.data.IEntityData", true, classLoader2);
        this.modeledBase = clazz6.getMethod("getBase", new Class[0]);
        this.renderRadius = clazz9.getMethod("setRenderRadius", Integer.TYPE);
        this.baseData = clazz9.getMethod("getData", new Class[0]);
        this.blockedCull = clazz10.getMethod("setBlockedCull", Boolean.class);
        this.backCull = clazz10.getMethod("setBackCull", Boolean.class);
        this.verticalCull = clazz10.getMethod("setVerticalCull", Boolean.class);
        this.viewRange = clazz7.getMethod("setViewRange", Float.TYPE);
    }

    Object crate(String id) throws ReflectiveOperationException {
        return this.crates.crate(id);
    }

    List<String> crateIds() throws ReflectiveOperationException {
        return this.crates.crateIds();
    }

    String blockCrateId(Block block) throws ReflectiveOperationException {
        return this.crates.blockCrateId(block.getLocation());
    }

    boolean holdingCrate(ItemStack item) throws ReflectiveOperationException {
        return this.crates.holdingCrate(item);
    }

    void interact(Player player, String id, boolean preview, Location location) throws ReflectiveOperationException {
        this.crates.interact(player, id, preview, location);
    }

    boolean hasModel(String string) throws ReflectiveOperationException {
        return this.blueprint.invoke(null, string) != null;
    }

    ModelHandle attach(Entity entity, String string, float f, int n) throws ReflectiveOperationException {
        Object object = this.createEntity.invoke(null, entity);
        try {
            Object object2 = this.createModel.invoke(null, string);
            if (object2 == null) {
                throw new IllegalArgumentException("ModelEngine model is not loaded: " + string);
            }
            this.canHurt.invoke(object2, false);
            this.viewRange.invoke(object2, Float.valueOf(Math.max(2.0f, (float)n / 32.0f)));
            this.addModel.invoke(object, object2, false);
            Object object3 = this.modeledBase.invoke(object, new Object[0]);
            this.renderRadius.invoke(object3, n);
            Object object4 = this.baseData.invoke(object3, new Object[0]);
            this.blockedCull.invoke(object4, Boolean.FALSE);
            this.backCull.invoke(object4, Boolean.FALSE);
            this.verticalCull.invoke(object4, Boolean.FALSE);
            this.visible.invoke(object, false);
            this.saved.invoke(object, false);
            this.bodyRot.invoke(object, Float.valueOf(f));
            this.headRot.invoke(object, Float.valueOf(f));
            this.pitchRot.invoke(object, Float.valueOf(0.0f));
            this.rotationLocked.invoke(object, true);
            return new ModelHandle(entity.getUniqueId(), object, object2);
        }
        catch (ReflectiveOperationException | RuntimeException exception) {
            try {
                this.removeEntity.invoke(null, entity.getUniqueId());
                this.destroy.invoke(object, new Object[0]);
            }
            catch (ReflectiveOperationException reflectiveOperationException) {
                exception.addSuppressed(reflectiveOperationException);
            }
            throw exception;
        }
    }

    boolean play(ModelHandle modelHandle, String string) throws ReflectiveOperationException {
        return Boolean.TRUE.equals(this.play.invoke(this.animationHandler.invoke(modelHandle.model, new Object[0]), string, 0.1, 0.15, 1.0, true));
    }

    void stop(ModelHandle modelHandle, String string) throws ReflectiveOperationException {
        this.stop.invoke(this.animationHandler.invoke(modelHandle.model, new Object[0]), string);
    }

    void detach(ModelHandle modelHandle) throws ReflectiveOperationException {
        this.removeEntity.invoke(null, modelHandle.uuid);
        this.destroy.invoke(modelHandle.modeled, new Object[0]);
    }

    Player openingPlayer(Event event) throws ReflectiveOperationException {
        return (Player)this.openPlayer.invoke(event, new Object[0]);
    }

    String openingCrateId(Event event) throws ReflectiveOperationException {
        return this.crates.crateId(this.openCrate.invoke(event));
    }

    Player modelPlayer(Event event) throws ReflectiveOperationException {
        return (Player)this.interactPlayer.invoke(event, new Object[0]);
    }

    UUID modelBaseUuid(Event event) throws ReflectiveOperationException {
        return (UUID)this.baseUuid.invoke(this.interactBase.invoke(event, new Object[0]), new Object[0]);
    }

    String modelAction(Event event) throws ReflectiveOperationException {
        return ((Enum)this.interactAction.invoke(event, new Object[0])).name();
    }

    Object modelSlot(Event event) throws ReflectiveOperationException {
        return this.interactSlot.invoke(event, new Object[0]);
    }

    record ModelHandle(UUID uuid, Object modeled, Object model) {
    }
}

