package com.quarkus.cms.review;

/**
 * Review lifecycle statuses for content review workflows.
 */
public enum ReviewStatus {
    /** Awaiting reviewer action. */
    PENDING("pending"),
    /** Approved by reviewer, can proceed to publish. */
    APPROVED("approved"),
    /** Rejected by reviewer. */
    REJECTED("rejected"),
    /** Reviewer requested changes before re-review. */
    CHANGES_REQUESTED("changes_requested"),
    /** Review was cancelled by the submitter. */
    CANCELLED("cancelled");

    private final String value;

    ReviewStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ReviewStatus fromValue(String value) {
        for (ReviewStatus s : values()) {
            if (s.value.equalsIgnoreCase(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown review status: " + value);
    }

    /** Returns true if the review is still in a pending/active state. */
    public boolean isActive() {
        return this == PENDING || this == CHANGES_REQUESTED;
    }
}
