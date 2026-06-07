package com.quarkus.cms.media.storage;

/**
 * Unchecked exception for storage provider failures.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
