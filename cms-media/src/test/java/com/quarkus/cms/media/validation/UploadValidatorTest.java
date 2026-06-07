package com.quarkus.cms.media.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UploadValidatorTest {

    @Test
    void shouldParseSizeK() {
        assertEquals(1024, UploadValidator.parseSize("1K"));
    }

    @Test
    void shouldParseSizeM() {
        assertEquals(10 * 1024 * 1024, UploadValidator.parseSize("10M"));
    }

    @Test
    void shouldParseSizeG() {
        assertEquals(1024L * 1024 * 1024, UploadValidator.parseSize("1G"));
    }

    @Test
    void shouldParseSizeBareNumber() {
        assertEquals(100, UploadValidator.parseSize("100"));
    }

    @Test
    void shouldParseSizeWithTrailingB() {
        // "500B" → strips B → "500" → 500 bytes
        assertEquals(500, UploadValidator.parseSize("500B"));
    }

    @Test
    void shouldParseSizeDefaultOnNull() {
        assertEquals(10 * 1024 * 1024, UploadValidator.parseSize(null));
    }

    @Test
    void shouldParseSizeDefaultOnBlank() {
        assertEquals(10 * 1024 * 1024, UploadValidator.parseSize("   "));
    }

    @Test
    void shouldParseSizeDefaultOnInvalid() {
        assertEquals(10 * 1024 * 1024, UploadValidator.parseSize("not-a-size"));
    }

    @Test
    void shouldFormatBytes() {
        assertEquals("100 B", UploadValidator.formatSize(100));
    }

    @Test
    void shouldFormatKb() {
        assertEquals("1.0 KB", UploadValidator.formatSize(1024));
    }

    @Test
    void shouldFormatMb() {
        assertEquals("1.0 MB", UploadValidator.formatSize(1024 * 1024));
    }

    @Test
    void shouldFormatGb() {
        assertEquals("1.00 GB", UploadValidator.formatSize(1024L * 1024 * 1024));
    }

    @Test
    void shouldParseSizeCaseInsensitive() {
        assertEquals(1024, UploadValidator.parseSize("1k"));
        assertEquals(10 * 1024 * 1024, UploadValidator.parseSize("10m"));
    }
}
