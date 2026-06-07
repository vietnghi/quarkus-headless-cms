package com.quarkus.cms.core.query;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Specifies which relation fields to populate (resolve target document data) in query results.
 *
 * <p>A {@code PopulateNode} mirrors Strapi's {@code populate} parameter. It can be a simple
 * field name (string) or a tree with nested population for deep relation resolution.
 *
 * <p>Supports field-level filtering via {@code fields} and optional depth override for
 * controlling how deep the population chain should go.
 */
public class PopulateNode {
    // Simple populate: just field name
    private String fieldName;
    // Deep populate: field name → child populate spec
    private List<PopulateNode> children;
    private boolean populateAll; // true for populate=*

    /**
     * Optional set of field names to include in the populated result.
     * When non-empty, only these fields (plus metadata id/documentId) are returned
     * for this populated relation.
     */
    private Set<String> fields;

    /**
     * Optional depth override for this branch of the population tree.
     * When set, overrides the default max depth for this populate chain.
     * 0 means populate only this level (no nested children), null means use default.
     */
    private Integer depthOverride;

    public PopulateNode() {}

    public PopulateNode(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }

    public List<PopulateNode> getChildren() { return children; }
    public void setChildren(List<PopulateNode> children) { this.children = children; }

    public boolean isPopulateAll() { return populateAll; }
    public void setPopulateAll(boolean populateAll) { this.populateAll = populateAll; }

    /** Returns the set of field names to include, or empty set for all fields. */
    public Set<String> getFields() { return fields; }
    public void setFields(Set<String> fields) { this.fields = fields; }

    /** Adds a field name to the field filter. */
    public void addField(String fieldName) {
        if (this.fields == null) {
            this.fields = new HashSet<>();
        }
        this.fields.add(fieldName);
    }

    /** Returns the optional depth override, or null if using default. */
    public Integer getDepthOverride() { return depthOverride; }
    public void setDepthOverride(Integer depthOverride) { this.depthOverride = depthOverride; }

    /**
     * Returns the effective max depth for this populate chain.
     * Uses the override if set, otherwise falls back to the default.
     */
    public int effectiveDepth(int defaultMaxDepth) {
        return depthOverride != null ? depthOverride : defaultMaxDepth;
    }

    @Override
    public String toString() {
        return "PopulateNode{" +
                "fieldName='" + fieldName + '\'' +
                ", populateAll=" + populateAll +
                ", fields=" + fields +
                ", depthOverride=" + depthOverride +
                ", children=" + (children != null ? children.size() : 0) + " children" +
                '}';
    }
}
