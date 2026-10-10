package com.townyreborn.hunt;

import com.townyreborn.hunt.AtomicFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Stream;

final class HuntLedger {
    private final Path players;
    private final String event;

    HuntLedger(Path path, String string) throws IOException {
        this.players = path.resolve("players");
        this.event = AtomicFiles.id(string);
        Files.createDirectories(this.players, new FileAttribute[0]);
    }

    private Path file(UUID uUID) {
        return this.players.resolve(uUID.toString()).resolve(this.event + ".properties");
    }

    private Properties load(UUID uUID) throws IOException {
        Properties properties = AtomicFiles.read(this.file(uUID));
        for (String string : properties.stringPropertyNames()) {
            if (!string.startsWith("head.")) continue;
            String string2 = properties.getProperty(string);
            if (!string2.equals("FOUND") && !string2.equals("PENDING")) {
                throw new IOException("Invalid find state for " + String.valueOf(uUID) + ": " + string);
            }
            AtomicFiles.id(string.substring(5));
        }
        return properties;
    }

    private Summary summary(Properties properties) {
        int n = 0;
        int n2 = 0;
        for (String string : properties.stringPropertyNames()) {
            if (!string.startsWith("head.")) continue;
            if (properties.getProperty(string).equals("FOUND")) {
                ++n;
                continue;
            }
            ++n2;
        }
        return new Summary(n, n2);
    }

    synchronized Summary summary(UUID uUID) throws IOException {
        return this.summary(this.load(uUID));
    }

    synchronized String state(UUID uUID, String string) throws IOException {
        return this.load(uUID).getProperty("head." + AtomicFiles.id(string), "");
    }

    synchronized Begin reserve(UUID uUID, String string, String string2, int n, String string3, int n2) throws IOException {
        AtomicFiles.id(string2);
        AtomicFiles.id(string3);
        Properties properties = this.load(uUID);
        String string4 = properties.getProperty("head." + string2, "");
        if (string4.equals("FOUND")) {
            return Begin.ALREADY_FOUND;
        }
        if (string4.equals("PENDING")) {
            return Begin.PENDING;
        }
        if (this.summary(properties).claimed() >= n) {
            return Begin.LIMIT;
        }
        properties.setProperty("name", string);
        properties.setProperty("head." + string2, "PENDING");
        properties.setProperty("time." + string2, Instant.now().toString());
        properties.setProperty("key." + string2, string3);
        properties.setProperty("balance-before." + string2, Integer.toString(n2));
        AtomicFiles.write(this.file(uUID), properties);
        return Begin.RESERVED;
    }

    synchronized void finish(UUID uUID, String string) throws IOException {
        Properties properties = this.load(uUID);
        if (!"PENDING".equals(properties.getProperty("head." + string))) {
            throw new IOException("Find is not pending: " + string);
        }
        properties.setProperty("head." + string, "FOUND");
        AtomicFiles.write(this.file(uUID), properties);
    }

    synchronized void rollback(UUID uUID, String string) throws IOException {
        Properties properties = this.load(uUID);
        if (!"PENDING".equals(properties.getProperty("head." + string))) {
            return;
        }
        for (String string2 : List.of("head.", "time.", "key.", "balance-before.")) {
            properties.remove(string2 + string);
        }
        AtomicFiles.write(this.file(uUID), properties);
    }

    synchronized List<String> pending(UUID uUID) throws IOException {
        Properties properties = this.load(uUID);
        ArrayList<String> arrayList = new ArrayList<String>();
        for (String string : properties.stringPropertyNames()) {
            if (!string.startsWith("head.") || !properties.getProperty(string).equals("PENDING")) continue;
            arrayList.add(string.substring(5));
        }
        return arrayList.stream().sorted().toList();
    }

    synchronized int reset(UUID uUID) throws IOException {
        Properties properties = this.load(uUID);
        int n = this.summary(properties).claimed();
        Properties properties2 = new Properties();
        properties2.setProperty("name", properties.getProperty("name", uUID.toString()));
        AtomicFiles.write(this.file(uUID), properties2);
        return n;
    }

    synchronized UUID byName(String string) throws IOException {
        try (Stream<Path> stream = Files.list(this.players);){
            for (Path path2 : stream.filter(path -> Files.isDirectory(path, new LinkOption[0])).toList()) {
                UUID uUID;
                try {
                    uUID = UUID.fromString(path2.getFileName().toString());
                }
                catch (IllegalArgumentException illegalArgumentException) {
                    continue;
                }
                Properties properties = this.load(uUID);
                if (!string.equalsIgnoreCase(properties.getProperty("name", ""))) continue;
                UUID uUID2 = uUID;
                return uUID2;
            }
        }
        return null;
    }

    record Summary(int found, int pending) {
        int claimed() {
            return this.found + this.pending;
        }
    }

    static enum Begin {
        RESERVED,
        ALREADY_FOUND,
        PENDING,
        LIMIT;

    }
}

