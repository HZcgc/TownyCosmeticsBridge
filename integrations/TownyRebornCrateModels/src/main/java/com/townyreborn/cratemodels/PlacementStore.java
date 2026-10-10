package com.townyreborn.cratemodels;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

final class PlacementStore {
    PlacementStore() {
    }

    static List<Placement> read(Path path) throws IOException, InvalidConfigurationException {
        if (!Files.exists(path, new LinkOption[0])) {
            return List.of();
        }
        YamlConfiguration yamlConfiguration = new YamlConfiguration();
        yamlConfiguration.load(path.toFile());
        int n = yamlConfiguration.getInt("version");
        if (n != 1 && n != 2) {
            throw new IOException("Unsupported placement format in " + String.valueOf(path));
        }
        ConfigurationSection configurationSection = yamlConfiguration.getConfigurationSection("placements");
        if (configurationSection == null) {
            throw new IOException("Missing placements section in " + String.valueOf(path));
        }
        ArrayList<Placement> arrayList = new ArrayList<Placement>();
        for (String string : configurationSection.getKeys(false)) {
            ConfigurationSection configurationSection2 = configurationSection.getConfigurationSection(string);
            if (configurationSection2 == null) {
                throw new IOException("Invalid placement section: " + string);
            }
            for (String string2 : List.of("crate", "model", "world-uuid", "world", "x", "y", "z", "yaw")) {
                if (configurationSection2.contains(string2)) continue;
                throw new IOException("Missing " + string2 + " in placement " + string);
            }
            for (String string2 : List.of("x", "y", "z", "yaw")) {
                if (configurationSection2.get(string2) instanceof Number) continue;
                throw new IOException("Non-numeric " + string2 + " in placement " + string);
            }
            try {
                arrayList.add(new Placement(string, configurationSection2.getString("crate"), configurationSection2.getString("model"), UUID.fromString(configurationSection2.getString("world-uuid")), configurationSection2.getString("world"), configurationSection2.getDouble("x"), configurationSection2.getDouble("y"), configurationSection2.getDouble("z"), (float)configurationSection2.getDouble("yaw"), n == 2 ? configurationSection2.getString("anchor-original") : null));
            }
            catch (IllegalArgumentException | NullPointerException runtimeException) {
                throw new IOException("Invalid saved placement: " + string, runtimeException);
            }
        }
        return arrayList;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    static void write(Path path, Collection<Placement> collection) throws IOException {
        YamlConfiguration yamlConfiguration = new YamlConfiguration();
        yamlConfiguration.set("version", (Object)2);
        yamlConfiguration.createSection("placements");
        HashSet<String> hashSet = new HashSet<String>();
        for (Placement placement : collection) {
            if (!hashSet.add(placement.id())) {
                throw new IOException("Duplicate placement ID: " + placement.id());
            }
            ConfigurationSection configurationSection = yamlConfiguration.createSection("placements." + placement.id());
            configurationSection.set("crate", (Object)placement.crateId());
            configurationSection.set("model", (Object)placement.modelId());
            configurationSection.set("world-uuid", (Object)placement.worldId().toString());
            configurationSection.set("world", (Object)placement.worldName());
            configurationSection.set("x", (Object)placement.x());
            configurationSection.set("y", (Object)placement.y());
            configurationSection.set("z", (Object)placement.z());
            configurationSection.set("yaw", (Object)Float.valueOf(placement.yaw()));
            if (placement.anchorOriginal() == null) continue;
            configurationSection.set("anchor-original", (Object)placement.anchorOriginal());
        }
        Files.createDirectories(path.toAbsolutePath().getParent(), new FileAttribute[0]);
        Path path2 = Files.createTempFile(path.toAbsolutePath().getParent(), "placements-", ".tmp", new FileAttribute[0]);
        try {
            Files.writeString(path2, (CharSequence)yamlConfiguration.saveToString(), StandardCharsets.UTF_8, new OpenOption[0]);
            try {
                Files.move(path2, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException atomicMoveNotSupportedException) {
                Files.move(path2, path, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally {
            Files.deleteIfExists(path2);
        }
    }

    record Placement(String id, String crateId, String modelId, UUID worldId, String worldName, double x, double y, double z, float yaw, String anchorOriginal) {
        Placement(String string, String string2, String string3, UUID uUID, String string4, double d, double d2, double d3, float f) {
            this(string, string2, string3, uUID, string4, d, d2, d3, f, null);
        }

        Placement {
            if (!(id.matches("[a-z0-9_-]{1,48}") && crateId.matches("[a-zA-Z0-9_-]{1,128}")
                    && modelId.matches("[a-zA-Z0-9_-]{1,128}") && worldId != null
                    && worldName != null && !worldName.isBlank() && Double.isFinite(x)
                    && Double.isFinite(y) && Double.isFinite(z) && Float.isFinite(yaw)
                    && Math.abs(x) <= 3.0E7 && Math.abs(z) <= 3.0E7)) {
                throw new IllegalArgumentException("Invalid crate placement: " + id);
            }
            if (anchorOriginal != null && !List.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air").contains(anchorOriginal)) {
                throw new IllegalArgumentException("Refusing a non-air block replacement: " + id);
            }
        }

        Placement anchored(String string) {
            return new Placement(this.id, this.crateId, this.modelId, this.worldId, this.worldName, this.x, this.y, this.z, this.yaw, string);
        }

        int chunkX() {
            return (int)Math.floor(this.x) >> 4;
        }

        int chunkZ() {
            return (int)Math.floor(this.z) >> 4;
        }
    }
}

