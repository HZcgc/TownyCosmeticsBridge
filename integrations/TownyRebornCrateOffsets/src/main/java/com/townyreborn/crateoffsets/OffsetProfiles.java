package com.townyreborn.crateoffsets;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import su.nightexpress.excellentcrates.animation.pipeline.AnimationProfilePipelineStage;
import su.nightexpress.excellentcrates.animation.style.csgo.CSGOAnimationProfile;
import su.nightexpress.excellentcrates.animation.style.simpleroll.SimpleRollProfile;
import su.nightexpress.excellentcrates.api.animation.AnimationContext;
import su.nightexpress.excellentcrates.api.animation.AnimationInstance;
import su.nightexpress.excellentcrates.api.animation.AnimationProfile;
import su.nightexpress.excellentcrates.api.animation.AnimationRegistry;
import su.nightexpress.excellentcrates.api.crate.event.CratePipelinePreStartEvent;
import su.nightexpress.nightcore.bridge.key.AdaptedKey;

final class OffsetProfiles implements Listener {
    private final Field registryField = NativeAnimationOffsets.field(AnimationProfilePipelineStage.class, "registry", AnimationRegistry.class);
    private final NativeAnimationOffsets positions = new NativeAnimationOffsets();
    private final Set<AnimationRegistry> registries = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Function<String, Offset> offsets;
    private final Logger logger;

    OffsetProfiles(Function<String, Offset> offsets, Logger logger) {
        this.offsets = offsets;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beforeOpening(CratePipelinePreStartEvent event) {
        try {
            for (var stage : event.getStages()) {
                if (stage instanceof AnimationProfilePipelineStage)
                    wrap((AnimationRegistry) registryField.get(stage));
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logger.log(Level.WARNING, "Could not apply crate animation offsets; keeping the native opening", exception);
        }
    }

    void wrap(AnimationRegistry registry) {
        registries.add(registry);
        // EC reload replaces the profiles; reattach before the next opening without stacking wrappers.
        for (AnimationProfile profile : registry.getProfiles())
            if (profile instanceof CSGOAnimationProfile || profile instanceof SimpleRollProfile)
                registry.registerProfile(new Profile(profile));
    }

    void restore() {
        for (AnimationRegistry registry : registries)
            for (AnimationProfile profile : registry.getProfiles())
                if (profile instanceof OffsetProfiles.Profile wrapped && wrapped.owner() == this)
                    registry.registerProfile(wrapped.delegate);
        registries.clear();
    }

    private final class Profile implements AnimationProfile {
        private final AnimationProfile delegate;
        Profile(AnimationProfile delegate) { this.delegate = delegate; }
        OffsetProfiles owner() { return OffsetProfiles.this; }
        @Override public AdaptedKey getKey() { return delegate.getKey(); }
        @Override public String getName() { return delegate.getName(); }

        @Override public AnimationInstance createInstance(AnimationContext context, Runnable onComplete) {
            AnimationInstance instance = delegate.createInstance(context, onComplete);
            try { positions.apply(instance, offsets.apply(context.crate().idString())); }
            catch (ReflectiveOperationException | RuntimeException exception) {
                logger.log(Level.WARNING, "Could not move animation for crate " + context.crate().idString() + "; keeping the native opening", exception);
            }
            // Return the original native instance: EC still owns ticking, rewards, guards and cleanup.
            return instance;
        }
    }
}
