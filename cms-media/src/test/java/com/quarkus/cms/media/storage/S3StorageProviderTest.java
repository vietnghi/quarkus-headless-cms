package com.quarkus.cms.media.storage;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class S3StorageProviderTest {

    private final S3StorageProvider provider = new S3StorageProvider(
        "test-bucket",
        Optional.of("https://s3.example.com"),
        "us-west-2",
        Optional.of("access-key"),
        Optional.of("secret-key"),
        Optional.of("https://cdn.example.com")
    );

    @Test
    void shouldReturnProviderKey() {
        assertEquals("s3", provider.providerKey());
    }

    @Test
    void shouldStoreFileAndGenerateUrl() {
        byte[] data = "s3 test data".getBytes();
        StorageProvider.StoreResult result = provider.store(
            new ByteArrayInputStream(data), "photo.jpg", "image/jpeg",
            Map.of("folderPath", "/images"));

        assertNotNull(result);
        assertNotNull(result.url());
        assertNotNull(result.storageKey());
        assertTrue(result.url().contains("test-bucket") || result.url().contains("cdn.example.com"));
    }

    @Test
    void shouldGetPublicUrl() {
        String url = provider.getPublicUrl("abc123.jpg");
        assertNotNull(url);
        assertTrue(url.contains("abc123.jpg"));
    }

    @Test
    void shouldReturnNullOnRetrieve() {
        InputStream result = provider.retrieve("any-key");
        assertNull(result);
    }

    @Test
    void shouldReturnTrueOnDelete() {
        assertTrue(provider.delete("any-key"));
    }

    @Test
    void shouldReturnFalseOnExists() {
        assertFalse(provider.exists("any-key"));
    }
}
