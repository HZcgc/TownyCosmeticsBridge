package com.townyreborn.petshops;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.bukkit.configuration.file.YamlConfiguration;

final class FilesIO {
    private FilesIO() {}
    static YamlConfiguration read(Path path) throws IOException {
        var yaml = new YamlConfiguration();
        yaml.options().parseComments(true);
        if (Files.exists(path)) {
            try { yaml.load(path.toFile()); }
            catch (org.bukkit.configuration.InvalidConfigurationException e) { throw new IOException("Invalid YAML: " + path, e); }
        }
        return yaml;
    }
    static void write(Path path, YamlConfiguration yaml) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path tmp = Files.createTempFile(path.toAbsolutePath().getParent(), "petshop-", ".tmp");
        try {
            Files.writeString(tmp, yaml.saveToString(), StandardCharsets.UTF_8);
            try { Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
    }
}
