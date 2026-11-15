package com.careercopilot.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalDiskStorageServiceTest {

    @TempDir
    Path dir;

    @Test
    void storesOpensAndDeletes() throws IOException {
        LocalDiskStorageService storage = new LocalDiskStorageService(dir.toString());

        storage.store("resumes/1/a.pdf", new ByteArrayInputStream(new byte[] {1, 2, 3}), 3);

        assertThat(Files.exists(dir.resolve("resumes/1/a.pdf"))).isTrue();
        try (var in = storage.open("resumes/1/a.pdf")) {
            assertThat(in.readAllBytes()).containsExactly(1, 2, 3);
        }
        storage.delete("resumes/1/a.pdf");
        assertThat(Files.exists(dir.resolve("resumes/1/a.pdf"))).isFalse();
        storage.delete("resumes/1/a.pdf");
    }

    @Test
    void rejectsKeysThatEscapeTheRoot() {
        LocalDiskStorageService storage = new LocalDiskStorageService(dir.resolve("root").toString());

        assertThatThrownBy(() -> storage.store("../evil.pdf", new ByteArrayInputStream(new byte[] {1}), 1))
                .isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> storage.delete("resumes/../../evil.pdf")).isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> storage.open("")).isInstanceOf(StorageException.class);
    }
}
