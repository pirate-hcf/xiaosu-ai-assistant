package com.xiaosu.knowledge;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.xiaosu.config.StorageProperties;

@Component
public class FileStore {

    private static final int BUFFER_SIZE = 8192;
    private static final int PREFIX_SIZE = 8192;

    private final Path root;
    private final long maxFileSize;

    public FileStore(StorageProperties properties) {
        this.root = properties.uploadDirectory().toAbsolutePath().normalize();
        this.maxFileSize = properties.maxFileSize().toBytes();
        if (maxFileSize <= 0) {
            throw new IllegalArgumentException("xiaosu.storage.max-file-size 必须大于 0");
        }
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw DocumentUploadException.storageFailure(exception);
        }
    }

    public StoredFile store(InputStream inputStream) {
        String storageKey = UUID.randomUUID().toString();
        Path target = resolve(storageKey);
        MessageDigest digest = sha256Digest();
        ByteArrayOutputStream prefix = new ByteArrayOutputStream(PREFIX_SIZE);
        long size = 0;

        try (InputStream input = inputStream;
                OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                size += read;
                if (size > maxFileSize) {
                    throw DocumentUploadException.tooLarge(maxFileSize);
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
                int prefixBytes = Math.min(read, PREFIX_SIZE - prefix.size());
                if (prefixBytes > 0) {
                    prefix.write(buffer, 0, prefixBytes);
                }
            }
            return new StoredFile(storageKey, HexFormat.of().formatHex(digest.digest()), size, prefix.toByteArray());
        } catch (DocumentUploadException exception) {
            deleteQuietly(storageKey);
            throw exception;
        } catch (IOException exception) {
            deleteQuietly(storageKey);
            throw DocumentUploadException.storageFailure(exception);
        }
    }

    public boolean exists(String storageKey) {
        return Files.isRegularFile(resolve(storageKey));
    }

    public void deleteQuietly(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException ignored) {
            // Best-effort cleanup; never expose the physical storage path.
        }
    }

    private Path resolve(String storageKey) {
        try {
            UUID.fromString(storageKey);
        } catch (IllegalArgumentException exception) {
            throw DocumentUploadException.badRequest("INVALID_STORAGE_KEY", "存储标识无效");
        }
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw DocumentUploadException.badRequest("INVALID_STORAGE_KEY", "存储标识无效");
        }
        return resolved;
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }

    public record StoredFile(String storageKey, String sha256, long size, byte[] prefix) {

        public StoredFile {
            prefix = prefix.clone();
        }

        @Override
        public byte[] prefix() {
            return prefix.clone();
        }
    }
}
