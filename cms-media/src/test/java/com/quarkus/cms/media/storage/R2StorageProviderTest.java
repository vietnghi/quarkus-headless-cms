package com.quarkus.cms.media.storage;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class R2StorageProviderTest {

    private final R2StorageProvider provider = new R2StorageProvider(
        "r2-bucket",
        Optional.of("https://abc123.r2.cloudflarestorage.com"),
        Optional.of("access-key"),
        Optional.of("secret-key"),
        Optional.empty()
    );

    @Test
    void shouldReturnProviderKey() {
        assertEquals("r2", provider.providerKey());
    }

    @Test
    void shouldStoreFile() {
        byte[] data = "r2 test".getBytes();
        StorageProvider.StoreResult result = provider.store(
            new ByteArrayInputStream(data), "image.png", "image/png",
            Map.of());

        assertNotNull(result);
        assertNotNull(result.storageKey());
        assertTrue(result.url().contains("r2-bucket"));
    }

    @Test
    void shouldGetPublicUrl() {
        String url = provider.getPublicUrl("my-file.png");
        assertNotNull(url);
        // Without publicUrlPrefix, uses r2.dev default
        assertTrue(url.contains("pub-") || url.contains("r2-bucket"));
    }

    @Test
    void shouldReturnNullOnRetrieve() {
        assertNull(provider.retrieve("any-key"));
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
