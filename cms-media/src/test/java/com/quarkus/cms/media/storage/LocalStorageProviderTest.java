package com.quarkus.cms.media.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LocalStorageProviderTest {

    private static final Path TEST_DIR = Path.of("target/test-uploads");

    private LocalStorageProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(TEST_DIR);
        provider = new LocalStorageProvider(TEST_DIR.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        if (Files.exists(TEST_DIR)) {
            Files.walk(TEST_DIR)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }

    @Test
    void shouldReturnProviderKey() {
        assertEquals("local", provider.providerKey());
    }

    @Test
    void shouldStoreFile() {
        byte[] data = "Hello, storage!".getBytes();
        StorageProvider.StoreResult result = provider.store(
            new ByteArrayInputStream(data), "test.txt", "text/plain",
            java.util.Map.of("folderPath", "/docs"));

        assertNotNull(result);
        assertNotNull(result.url());
        assertTrue(result.url().startsWith("/uploads/"));
        assertTrue(result.url().contains("docs/"));
        assertTrue(result.url().endsWith(".txt"));
        assertTrue(result.size() > 0);

        // File should exist on disk
        Path resolved = TEST_DIR.resolve(result.storageKey());
        assertTrue(Files.exists(resolved));
    }

    @Test
    void shouldRetrieveStoredFile() throws IOException {
        byte[] data = "Retrieve this!".getBytes();
        StorageProvider.StoreResult result = provider.store(
            new ByteArrayInputStream(data), "readme.txt", "text/plain",
            Map.of());

        InputStream stream = provider.retrieve(result.storageKey());
        assertNotNull(stream);
        byte[] readBack = stream.readAllBytes();
        assertArrayEquals(data, readBack);
    }

    @Test
    void shouldReturnNullForMissingFile() {
        InputStream stream = provider.retrieve("nonexistent/file.txt");
        assertNull(stream);
    }

    @Test
    void shouldDeleteFile() {
        byte[] data = "Delete me".getBytes();
        StorageProvider.StoreResult result = provider.store(
            new ByteArrayInputStream(data), "deletable.txt", "text/plain",
            Map.of());

        assertTrue(Files.exists(TEST_DIR.resolve(result.storageKey())));

        boolean deleted = provider.delete(result.storageKey());
        assertTrue(deleted);
        assertFalse(Files.exists(TEST_DIR.resolve(result.storageKey())));
    }

    @Test
    void shouldReturnFalseWhenDeletingNonExistentFile() {
        boolean deleted = provider.delete("nonexistent/file.txt");
        assertFalse(deleted);
    }

    @Test
    void shouldCheckExists() {
        byte[] data = "exists check".getBytes();
        StorageProvider.StoreResult result = provider.store(
            new ByteArrayInputStream(data), "exists.txt", "text/plain",
            Map.of());

        assertTrue(provider.exists(result.storageKey()));
        assertFalse(provider.exists("nonexistent/file.txt"));
    }

    @Test
    void shouldGetPublicUrl() {
        String url = provider.getPublicUrl("path/to/file.txt");
        assertEquals("/uploads/path/to/file.txt", url);
    }

    @Test
    void shouldPreventPathTraversalOnRetrieve() {
        assertThrows(SecurityException.class, () ->
            provider.retrieve("../../../etc/passwd"));
    }

    @Test
    void shouldPreventPathTraversalOnDelete() {
        assertThrows(SecurityException.class, () ->
            provider.delete("../../../etc/passwd"));
    }

    @Test
    void shouldGenerateUniqueNames() {
        byte[] data = "same content".getBytes();
        StorageProvider.StoreResult r1 = provider.store(
            new ByteArrayInputStream(data), "file.txt", "text/plain", Map.of());
        StorageProvider.StoreResult r2 = provider.store(
            new ByteArrayInputStream(data), "file.txt", "text/plain", Map.of());

        assertNotEquals(r1.storageKey(), r2.storageKey());
        assertNotEquals(r1.url(), r2.url());
    }

    @Test
    void shouldPreserveFileExtension() {
        byte[] data = "image data".getBytes();
        StorageProvider.StoreResult pngResult = provider.store(
            new ByteArrayInputStream(data), "photo.png", "image/png", Map.of());
        StorageProvider.StoreResult jpgResult = provider.store(
            new ByteArrayInputStream(data), "photo.jpg", "image/jpeg", Map.of());

        assertTrue(pngResult.url().endsWith(".png"));
        assertTrue(jpgResult.url().endsWith(".jpg"));
    }

    @Test
    void shouldCreateNestedFolders() {
        byte[] data = "deep".getBytes();
        StorageProvider.StoreResult result = provider.store(
            new ByteArrayInputStream(data), "deep.txt", "text/plain",
            Map.of("folderPath", "/a/b/c"));

        Path resolved = TEST_DIR.resolve(result.storageKey());
        assertTrue(Files.exists(resolved));
        assertTrue(resolved.toString().contains("a/b/c"));
    }
}
