package com.townyreborn.hunt;

import com.townyreborn.hunt.AtomicFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

final class HeadIndex {
    private final Path file;
    private Map<String, Head> heads = new LinkedHashMap<String, Head>();
    private final Map<String, Head> exactHeads = new HashMap<String, Head>();
    private final Map<String, Head> namedHeads = new HashMap<String, Head>();
    private final Set<String> exactProtected = new HashSet<String>();
    private final Set<String> namedProtected = new HashSet<String>();
    private int nextId = 1;

    HeadIndex(Path path, String string) throws IOException {
        this.file = path.resolve("events").resolve(AtomicFiles.id(string)).resolve("heads.properties");
        Properties properties = AtomicFiles.read(this.file);
        try {
            this.nextId = Integer.parseInt(properties.getProperty("next-id", "1"));
            if (this.nextId < 1) {
                throw new IllegalArgumentException("Invalid next head ID");
            }
            String string2 = properties.getProperty("ids", "");
            for (String string3 : string2.isEmpty() ? List.<String>of() : List.of(string2.split(","))) {
                Position position;
                AtomicFiles.id(string3);
                String[] stringArray = properties.getProperty("head." + string3, "").split(";", -1);
                if (stringArray.length != 9 || !stringArray[0].equals("true") && !stringArray[0].equals("false")) {
                    throw new IllegalArgumentException("Invalid head entry");
                }
                UUID uUID = UUID.fromString(stringArray[1]);
                String string4 = new String(Base64.getUrlDecoder().decode(stringArray[2]), StandardCharsets.UTF_8);
                if (string4.isBlank()) {
                    throw new IllegalArgumentException("Missing world name");
                }
                Position position2 = new Position(uUID, string4, Integer.parseInt(stringArray[3]), Integer.parseInt(stringArray[4]), Integer.parseInt(stringArray[5]));
                Head head = new Head(string3, position2, position = new Position(uUID, string4, Integer.parseInt(stringArray[6]), Integer.parseInt(stringArray[7]), Integer.parseInt(stringArray[8])), Boolean.parseBoolean(stringArray[0]));
                if (this.heads.putIfAbsent(string3, head) == null) continue;
                throw new IllegalArgumentException("Duplicate head ID");
            }
            this.validate(this.heads);
        }
        catch (IllegalArgumentException illegalArgumentException) {
            throw new IOException("Invalid hunt head data: " + String.valueOf(this.file), illegalArgumentException);
        }
        if (properties.isEmpty()) {
            this.save(this.heads, this.nextId);
        }
        this.rebuild();
    }

    private void validate(Map<String, Head> map) {
        ArrayList<String> arrayList = new ArrayList<String>();
        for (Head head : map.values()) {
            if (!head.active()) continue;
            String string = head.position().named();
            if (arrayList.contains(string)) {
                throw new IllegalArgumentException("Two ghosts share the same location");
            }
            arrayList.add(string);
        }
    }

    private void save(Map<String, Head> map, int n) throws IOException {
        this.validate(map);
        Properties properties = new Properties();
        properties.setProperty("next-id", Integer.toString(n));
        properties.setProperty("ids", String.join((CharSequence)",", map.keySet()));
        for (Head head : map.values()) {
            Position position = head.position();
            Position position2 = head.support();
            String string = Base64.getUrlEncoder().withoutPadding().encodeToString(position.worldName().getBytes(StandardCharsets.UTF_8));
            properties.setProperty("head." + head.id(), head.active() + ";" + String.valueOf(position.world()) + ";" + string + ";" + position.x() + ";" + position.y() + ";" + position.z() + ";" + position2.x() + ";" + position2.y() + ";" + position2.z());
        }
        AtomicFiles.write(this.file, properties);
    }

    private void rebuild() {
        this.exactHeads.clear();
        this.namedHeads.clear();
        this.exactProtected.clear();
        this.namedProtected.clear();
        for (Head head : this.heads.values()) {
            if (!head.active()) continue;
            this.exactHeads.put(head.position().exact(), head);
            this.namedHeads.put(head.position().named(), head);
            for (Position position : List.of(head.position(), head.support())) {
                this.exactProtected.add(position.exact());
                this.namedProtected.add(position.named());
            }
        }
    }

    Head at(Position position) {
        Head head = this.exactHeads.get(position.exact());
        return head == null ? this.namedHeads.get(position.named()) : head;
    }

    boolean protectedAt(Position position) {
        return this.exactProtected.contains(position.exact()) || this.namedProtected.contains(position.named());
    }

    int activeCount() {
        return (int)this.heads.values().stream().filter(Head::active).count();
    }

    List<Head> active() {
        return this.heads.values().stream().filter(Head::active).toList();
    }

    Head get(String string) {
        return this.heads.get(string);
    }

    Head add(String string, Position position, Position position2, int n) throws IOException {
        if (this.at(position) != null) {
            throw new IllegalArgumentException("This head is already registered.");
        }
        if (this.activeCount() >= n) {
            throw new IllegalArgumentException("The hunt already has " + n + " heads. Use /hunt move <id> to relocate one.");
        }
        int n2 = this.nextId;
        Object object = string;
        if (object == null) {
            while (this.heads.containsKey(object = "ghost" + String.format(Locale.ROOT, "%02d", n2++))) {
            }
        }
        AtomicFiles.id((String)object);
        if (this.heads.containsKey(object)) {
            throw new IllegalArgumentException("This ID already exists. Use /hunt move " + (String)object + " instead.");
        }
        Head head = new Head((String)object, position, position2, true);
        LinkedHashMap<String, Head> linkedHashMap = new LinkedHashMap<String, Head>(this.heads);
        linkedHashMap.put((String)object, head);
        this.save(linkedHashMap, n2);
        this.heads = linkedHashMap;
        this.nextId = n2;
        this.rebuild();
        return head;
    }

    Head move(String string, Position position, Position position2, int n) throws IOException {
        Head head = this.heads.get(AtomicFiles.id(string));
        if (head == null) {
            throw new IllegalArgumentException("Unknown ghost ID: " + string);
        }
        Head head2 = this.at(position);
        if (head2 != null && !head2.id().equals(string)) {
            throw new IllegalArgumentException("Another ghost is already at this location.");
        }
        if (!head.active() && this.activeCount() >= n) {
            throw new IllegalArgumentException("The hunt has reached its head limit.");
        }
        Head head3 = new Head(string, position, position2, true);
        LinkedHashMap<String, Head> linkedHashMap = new LinkedHashMap<String, Head>(this.heads);
        linkedHashMap.put(string, head3);
        this.save(linkedHashMap, this.nextId);
        this.heads = linkedHashMap;
        this.rebuild();
        return head3;
    }

    void remove(String string) throws IOException {
        Head head = this.heads.get(AtomicFiles.id(string));
        if (head == null || !head.active()) {
            throw new IllegalArgumentException("Unknown active ghost: " + string);
        }
        LinkedHashMap<String, Head> linkedHashMap = new LinkedHashMap<String, Head>(this.heads);
        linkedHashMap.put(string, new Head(string, head.position(), head.support(), false));
        this.save(linkedHashMap, this.nextId);
        this.heads = linkedHashMap;
        this.rebuild();
    }

    record Position(UUID world, String worldName, int x, int y, int z) {
        String exact() {
            return String.valueOf(this.world) + ":" + this.x + ":" + this.y + ":" + this.z;
        }

        String named() {
            return this.worldName + ":" + this.x + ":" + this.y + ":" + this.z;
        }
    }

    record Head(String id, Position position, Position support, boolean active) {
    }
}

