package com.careercopilot.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LocalDiskStorageService implements StorageService {

    private final Path root;

    public LocalDiskStorageService(@Value("${app.storage.dir:./storage}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    @Override
    public String store(String key, InputStream in, long size) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return key;
        } catch (IOException e) {
            throw new StorageException("Could not store file", e);
        }
    }

    @Override
    public InputStream open(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (IOException e) {
            throw new StorageException("Could not open file", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new StorageException("Could not delete file", e);
        }
    }

    /** Resolves the key under the root and rejects anything that escapes it. */
    private Path resolve(String key) {
        if (key == null || key.isBlank()) {
            throw new StorageException("Invalid storage key");
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new StorageException("Invalid storage key");
        }
        return path;
    }
}
