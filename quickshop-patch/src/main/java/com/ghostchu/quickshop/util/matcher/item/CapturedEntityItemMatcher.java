package com.ghostchu.quickshop.util.matcher.item;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

import static com.ghostchu.quickshop.util.matcher.item.AnimalNbt.*;

/** Compares copies of saved data; never edits the shop template or transferred item. */
final class CapturedEntityItemMatcher {
    private static final NamespacedKey ENTITY = new NamespacedKey("catchball", "entity");
    private static final NamespacedKey ENTITY_TYPE = new NamespacedKey("catchball", "entitytype");
    private static final Set<String> RUNTIME_KEYS = Set.of(
        "UUID", "UUIDMost", "UUIDLeast", "Pos", "Motion", "Rotation",
        "WorldUUIDMost", "WorldUUIDLeast", "Dimension", "PortalCooldown", "FallDistance",
        "OnGround", "Fire", "Air", "DeathTime", "HurtTime", "HurtByTimestamp",
        "Paper.Origin", "Paper.OriginWorld", "Paper.SpawnReason", "Health", "AbsorptionAmount",
        "HuntingCooldown", "PersistenceRequired", "TicksLived", "ticks_lived", "Spigot.ticksLived",
        "fall_distance", "invulnerable_time", "current_impulse_context_reset_grace_time",
        "InLove", "LoveCause", "ForcedAge", "ForcedAgeTimer", "EggLayTime",
        "EatingHaystack", "FallFlying", "SleepingX", "SleepingY", "SleepingZ");
    // UUIDs below Owner, attributes, Offers, inventories or other plugin data are retained.
    private static final Set<String> MEMORY_KEYS = Set.of(
        "minecraft:home", "minecraft:job_site", "minecraft:potential_job_site",
        "minecraft:meeting_point", "minecraft:last_slept", "minecraft:last_woken",
        "minecraft:last_worked_at_poi", "minecraft:walk_target", "minecraft:look_target",
        "minecraft:path", "minecraft:nearest_living_entities", "minecraft:nearest_visible_living_entities",
        "minecraft:hunted_recently", "minecraft:breed_target", "minecraft:temptation_cooldown_ticks",
        "minecraft:is_tempted", "minecraft:attack_cooling_down");

    private CapturedEntityItemMatcher() {}

    static boolean isCaptured(ItemStack item) {
        if (item.getType() != Material.PLAYER_HEAD) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(ENTITY, PersistentDataType.STRING)
            && meta.getPersistentDataContainer().has(ENTITY_TYPE, PersistentDataType.STRING);
    }

    /** null means this pair belongs to the original Bukkit matcher. */
    static Boolean matches(ItemStack first, ItemStack second) {
        boolean bucket = first.getType() == Material.AXOLOTL_BUCKET;
        boolean captured = isCaptured(first);
        if (!bucket && !captured) {
            return second.getType() == Material.AXOLOTL_BUCKET || isCaptured(second) ? false : null;
        }
        if (first.getType() != second.getType() || (captured && !isCaptured(second))) return false;
        try {
            List<String> templates = captured ? catchBallLoreTemplates() : List.of();
            return comparison(first, captured, templates).equals(comparison(second, captured, templates));
        } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
            // Unsupported or malformed captured data must not become a wildcard.
            return false;
        }
    }

    static Tag comparison(ItemStack item, boolean captured, List<String> templates) throws IOException {
        Tag root = read(item.serializeAsBytes());
        Map<String, Tag> data = compound(root);
        data.remove("count");
        Map<String, Tag> components = compound(data.get("components"));
        if (captured) {
            Map<String, Tag> pdc = compound(compound(components.get("minecraft:custom_data")).get("PublicBukkitValues"));
            Tag type = pdc.get("catchball:entitytype"), entity = pdc.get("catchball:entity");
            if (type == null || type.type() != 8 || entity == null || entity.type() != 8)
                throw new IllegalArgumentException("Incomplete CatchBall data");
            // Only a real Bukkit entity type is accepted, never an empty or arbitrary identifier.
            org.bukkit.entity.EntityType.valueOf((String) type.value());
            Tag savedEntity = parse((String) entity.value());
            normalizeEntity(compound(savedEntity));
            pdc.put("catchball:entity", savedEntity);
            pdc.remove("catchball:skulldata");
            Tag profile = components.get("minecraft:profile");
            if (profile != null) {
                Map<String, Tag> fields = compound(profile);
                fields.remove("id");
                fields.remove("Id");
            }
            normalizeLore(components, item.getItemMeta(), templates, (String) type.value());
        } else {
            Tag bucket = components.get("minecraft:bucket_entity_data");
            if (bucket == null) bucket = new Tag(10, new HashMap<String, Tag>());
            normalizeEntity(compound(bucket));
            if (compound(bucket).isEmpty()) components.remove("minecraft:bucket_entity_data");
            else components.put("minecraft:bucket_entity_data", bucket);
            // Missing variant is Minecraft's default lucy, not a wildcard for rare blue.
            components.putIfAbsent("minecraft:axolotl/variant", string("lucy"));
        }
        return root;
    }

    static void normalizeEntity(Map<String, Tag> entity) {
        RUNTIME_KEYS.forEach(entity::remove);
        Tag age = entity.remove("Age");
        if (age != null) {
            if (!(age.value() instanceof Number number)) throw new IllegalArgumentException("Invalid entity age");
            // Growth and breeding cooldown ticks vary; baby versus adult does not.
            entity.put("Age", new Tag(3, number.longValue() < 0 ? -1 : 0));
        } else entity.put("Age", new Tag(3, 0));
        Tag locked = entity.get("AgeLocked");
        if (locked != null && locked.value() instanceof Number number && number.longValue() == 0)
            entity.remove("AgeLocked");
        Tag brain = entity.get("Brain");
        if (brain != null) {
            Map<String, Tag> fields = compound(brain);
            Tag memories = fields.get("memories");
            if (memories != null) {
                Map<String, Tag> values = compound(memories);
                MEMORY_KEYS.forEach(values::remove);
                if (values.isEmpty()) fields.remove("memories");
            }
            if (fields.isEmpty()) entity.remove("Brain");
        }
    }

    private static void normalizeLore(Map<String, Tag> components, ItemMeta meta, List<String> templates, String type) {
        if (templates.isEmpty() || !components.containsKey("minecraft:lore") || meta == null || meta.lore() == null) return;
        var lines = meta.lore();
        // Only configured capture-history lines may vary. All extra/plugin lore remains strict.
        if (lines.size() < templates.size()) return;
        Tag rawLore = components.get("minecraft:lore");
        if (rawLore.type() != 9 || !(rawLore.value() instanceof TagList rawLines)
                || rawLines.values().size() != lines.size()) return;
        var normalized = new ArrayList<Tag>();
        for (int i = 0; i < lines.size(); i++) {
            String line = LegacyComponentSerializer.legacySection().serialize(lines.get(i));
            if (i < templates.size()) {
                String template = legacy(templates.get(i)).replace("{ENTITY}", type);
                String canonical = normalizeHistoryLine(line, template);
                // If the lore layout was edited, retain the entire original component comparison.
                if (canonical == null) return;
                line = canonical;
            }
            normalized.add(i < templates.size() ? string(line) : rawLines.values().get(i));
        }
        components.put("minecraft:lore", new Tag(9, new TagList(0, normalized)));
    }

    static String normalizeHistoryLine(String line, String template) {
        StringBuilder regex = new StringBuilder("^");
        int offset = 0;
        var placeholder = Pattern.compile("\\{(?:PLAYER|TIME|LOCATION)\\}").matcher(template);
        boolean dynamic = false;
        while (placeholder.find()) {
            regex.append(Pattern.quote(template.substring(offset, placeholder.start()))).append(".*?");
            offset = placeholder.end();
            dynamic = true;
        }
        regex.append(Pattern.quote(template.substring(offset))).append("$");
        return dynamic && Pattern.compile(regex.toString()).matcher(line).matches() ? template
            : (!dynamic && template.equals(line) ? line : null);
    }

    private static String legacy(String text) {
        return text.replaceAll("(?i)&([0-9a-fk-or])", "\u00a7$1");
    }

    @SuppressWarnings("unchecked")
    private static List<String> catchBallLoreTemplates() {
        var plugin = Bukkit.getPluginManager().getPlugin("CatchBall");
        if (plugin == null || !plugin.isEnabled()) return List.of();
        try {
            var type = Class.forName("com.github.nutt1101.utils.TranslationFileReader", false, plugin.getClass().getClassLoader());
            Object value = type.getField("dropSkullLore").get(null);
            if (value instanceof List<?> list && list.stream().allMatch(String.class::isInstance))
                return List.copyOf((List<String>) list);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Unknown CatchBall versions keep strict lore matching.
        }
        return List.of();
    }
}
