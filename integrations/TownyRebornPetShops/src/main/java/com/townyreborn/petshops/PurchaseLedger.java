package com.townyreborn.petshops;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

final class PurchaseLedger {
    enum Phase { PREPARED, CHARGED, DONE }
    record Key(UUID player, String pet) {}
    record Entry(Key key, String permission, double price, Phase phase, String source, String transaction, boolean announcementHandled) {
        Entry(Key key, String permission, double price, Phase phase, String source, String transaction) {
            this(key,permission,price,phase,source,transaction,phase==Phase.DONE);
        }
        Entry phase(Phase p) { return new Entry(key,permission,price,p,source,transaction,announcementHandled); }
        Entry completed() { return new Entry(key,permission,price,Phase.DONE,source,transaction,true); }
    }
    private final Path file;
    private Map<Key, Entry> entries=new LinkedHashMap<>();
    PurchaseLedger(Path file) throws IOException {
        this.file=file;
        var y=FilesIO.read(file);
        if (y.contains("version") && y.getInt("version")!=1) throw new IOException("Unsupported transaction format");
        var root=y.getConfigurationSection("players");
        try {
            if (root!=null) for (String uuid:root.getKeys(false)) {
                var p=root.getConfigurationSection(uuid);
                if (p==null) throw new IllegalArgumentException("Invalid player section");
                for (String id:p.getKeys(false)) {
                    PetCatalog.validId(id); var s=Objects.requireNonNull(p.getConfigurationSection(id));
                    var key=new Key(UUID.fromString(uuid),id);
                    String permission=Objects.requireNonNull(s.getString("permission"));
                    double price=s.getDouble("price");
                    if (!permission.matches("[A-Za-z0-9_.-]{1,200}") || !Double.isFinite(price) || price<0)
                        throw new IllegalArgumentException("Invalid transaction");
                    Phase phase=Phase.valueOf(s.getString("phase"));
                    if (s.contains("announcement-handled") && !s.isBoolean("announcement-handled"))
                        throw new IllegalArgumentException("Invalid announcement state");
                    entries.put(key,new Entry(key,permission,price,phase,
                            s.getString("source","unknown"),s.getString("transaction",""),
                            s.getBoolean("announcement-handled",phase==Phase.DONE)));
                }
            }
        } catch (RuntimeException e) { throw new IOException("Invalid transactions; refusing purchases",e); }
    }
    Entry get(Key key) { return entries.get(key); }
    Collection<Entry> entries() { return List.copyOf(entries.values()); }
    void put(Entry entry) throws IOException {
        var changed=new LinkedHashMap<>(entries); changed.put(entry.key(),entry); write(changed); entries=changed;
    }
    void remove(Key key) throws IOException {
        var changed=new LinkedHashMap<>(entries); changed.remove(key); write(changed); entries=changed;
    }
    private void write(Map<Key, Entry> changed) throws IOException {
        var y=new YamlConfiguration(); y.set("version",1); y.createSection("players");
        for (Entry e:changed.values()) {
            var s=y.createSection("players."+e.key().player()+"."+e.key().pet());
            s.set("permission",e.permission()); s.set("price",e.price()); s.set("phase",e.phase().name());
            s.set("source",e.source()); s.set("transaction",e.transaction());
            s.set("announcement-handled",e.announcementHandled());
        }
        FilesIO.write(file,y);
    }
}
