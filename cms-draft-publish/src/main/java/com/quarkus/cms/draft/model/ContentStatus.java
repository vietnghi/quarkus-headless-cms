package com.quarkus.cms.draft.model;

/**
 * Content lifecycle statuses.
 */
public enum ContentStatus {
    DRAFT("draft"),
    PUBLISHED("published"),
    UNPUBLISHED("unpublished");

    private final String value;

    ContentStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ContentStatus fromValue(String value) {
        for (ContentStatus s : values()) {
            if (s.value.equalsIgnoreCase(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown status: " + value);
    }
}
