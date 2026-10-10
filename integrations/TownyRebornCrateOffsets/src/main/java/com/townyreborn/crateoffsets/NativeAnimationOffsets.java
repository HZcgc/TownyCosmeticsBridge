package com.townyreborn.crateoffsets;

import java.lang.reflect.Field;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import su.nightexpress.excellentcrates.animation.style.csgo.CSGOAnimationInstance;
import su.nightexpress.excellentcrates.animation.style.simpleroll.SimpleRollAnimation;
import su.nightexpress.excellentcrates.api.animation.AnimationInstance;

/** EC7 has no public position setter. Verify its fields once, then move only visuals. */
final class NativeAnimationOffsets {
    private final Field csgoLocation = field(CSGOAnimationInstance.class, "displayLocation", Location.class);
    private final Field rollLocation = field(SimpleRollAnimation.class, "rewardLocation", Location.class);
    private final Field rollDisplay = field(SimpleRollAnimation.class, "rewardDisplay", Item.class);

    static Field field(Class<?> owner, String name, Class<?> expected) {
        try {
            Field field = owner.getDeclaredField(name);
            if (field.getType() != expected) throw new IllegalStateException("Unexpected EC7 field type: " + owner.getName() + "." + name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unsupported ExcellentCrates animation API: " + owner.getName() + "." + name, exception);
        }
    }

    void apply(AnimationInstance instance, Offset offset) throws IllegalAccessException {
        if (offset.zero()) return;
        Field locationField;
        Item item = null;
        if (instance instanceof CSGOAnimationInstance) locationField = csgoLocation;
        else if (instance instanceof SimpleRollAnimation) {
            locationField = rollLocation;
            item = (Item) rollDisplay.get(instance);
        } else return;
        Location location = (Location) locationField.get(instance);
        Location original = location.clone();
        location.add(offset.x(), offset.y(), offset.z());
        if (item != null) {
            try {
                if (!item.teleport(location.clone())) throw new IllegalStateException("The reward display could not be moved.");
            } catch (RuntimeException exception) {
                location.setX(original.getX());
                location.setY(original.getY());
                location.setZ(original.getZ());
                throw exception;
            }
        }
    }
}
