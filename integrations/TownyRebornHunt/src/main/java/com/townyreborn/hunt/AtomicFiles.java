package com.townyreborn.hunt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.util.Properties;

final class AtomicFiles {
    private AtomicFiles() {
    }

    static Properties read(Path path) throws IOException {
        Properties properties = new Properties();
        if (Files.notExists(path, new LinkOption[0])) {
            Path path2;
            for (path2 = path.toAbsolutePath().getParent(); path2 != null && Files.notExists(path2, new LinkOption[0]); path2 = path2.getParent()) {
            }
            if (path2 == null || !Files.isDirectory(path2, new LinkOption[0])) {
                throw new IOException("Unreadable player data directory: " + String.valueOf(path));
            }
            return properties;
        }
        if (!Files.isRegularFile(path, new LinkOption[0]) || Files.size(path) == 0L) {
            throw new IOException("Refusing to replace unreadable or empty data: " + String.valueOf(path));
        }
        try (InputStream inputStream = Files.newInputStream(path, new OpenOption[0]);){
            properties.load(inputStream);
        }
        if (!"1".equals(properties.getProperty("format"))) {
            throw new IOException("Unsupported data format: " + String.valueOf(path));
        }
        return properties;
    }

    static void write(Path path, Properties properties) throws IOException {
        properties.setProperty("format", "1");
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        properties.store(byteArrayOutputStream, "Towny Reborn Hunt - edit with the server stopped");
        AtomicFiles.writeBytes(path, byteArrayOutputStream.toByteArray());
    }

    static void writeBytes(Path path, byte[] byArray) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent(), new FileAttribute[0]);
        Path path2 = Files.createTempFile(path.toAbsolutePath().getParent(), ".hunt-", ".tmp", new FileAttribute[0]);
        try {
            try (FileChannel fileChannel = FileChannel.open(path2, StandardOpenOption.WRITE);){
                ByteBuffer byteBuffer = ByteBuffer.wrap(byArray);
                while (byteBuffer.hasRemaining()) {
                    fileChannel.write(byteBuffer);
                }
                fileChannel.force(true);
            }
            try {
                Files.move(path2, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException atomicMoveNotSupportedException) {
                Files.move(path2, path, StandardCopyOption.REPLACE_EXISTING);
            }
            try (FileChannel directoryChannel = FileChannel.open(path.toAbsolutePath().getParent(), StandardOpenOption.READ)) {
                directoryChannel.force(true);
            }
            catch (IOException | UnsupportedOperationException exception) {
                // empty catch block
            }
        }
        finally {
            Files.deleteIfExists(path2);
        }
    }

    static String id(String string) {
        if (string == null || !string.matches("[a-z0-9_]{1,64}")) {
            throw new IllegalArgumentException("Use a lowercase ID containing only letters, numbers and underscores.");
        }
        return string;
    }
}

