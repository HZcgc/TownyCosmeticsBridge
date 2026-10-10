package com.townyreborn.petshops;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

final class NpcBindings {
    record Binding(UUID uuid, int id, String pet, boolean managed) {}
    private final Path file;
    private Map<UUID,Binding> entries=new LinkedHashMap<>();
    NpcBindings(Path file) throws IOException {
        this.file=file; var y=FilesIO.read(file);
        if (y.contains("version") && y.getInt("version")!=1) throw new IOException("Unsupported NPC bindings version");
        var root=y.getConfigurationSection("npcs");
        try {
            if (root!=null) for (String uuid:root.getKeys(false)) {
                var s=Objects.requireNonNull(root.getConfigurationSection(uuid));
                UUID u=UUID.fromString(uuid); String pet=s.getString("pet"); PetCatalog.validId(pet);
                if (!s.isInt("id") || s.getInt("id")<0) throw new IllegalArgumentException("Invalid NPC id");
                entries.put(u,new Binding(u,s.getInt("id"),pet,s.getBoolean("managed",false)));
            }
        } catch (RuntimeException e) { throw new IOException("Invalid NPC bindings",e); }
    }
    Binding get(UUID uuid) { return entries.get(uuid); }
    Collection<Binding> all() { return List.copyOf(entries.values()); }
    void put(Binding b) throws IOException {
        var changed=new LinkedHashMap<>(entries); changed.put(b.uuid(),b); write(changed); entries=changed;
    }
    void remove(UUID uuid) throws IOException {
        var changed=new LinkedHashMap<>(entries); changed.remove(uuid); write(changed); entries=changed;
    }
    private void write(Map<UUID,Binding> changed) throws IOException {
        var y=new YamlConfiguration(); y.set("version",1); y.createSection("npcs");
        for (Binding b:changed.values()) {
            var s=y.createSection("npcs."+b.uuid()); s.set("id",b.id()); s.set("pet",b.pet()); s.set("managed",b.managed());
        }
        FilesIO.write(file,y);
    }
}
