package com.quarkus.cms.core.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import jakarta.persistence.*;

/**
 * Generic relation table for storing directional links between documents.
 *
 * <p>Enables many-to-many, one-to-many, and polymorphic relations without requiring DDL
 * alterations. Each row represents a single edge from a source document to a target document,
 * identified by a field name and ordered by {@code orderIndex}.
 */
@Entity
@Table(
    name = "cms_relations",
    indexes = {
      @Index(name = "idx_relations_source", columnList = "source_document_id, field_name"),
      @Index(name = "idx_relations_target", columnList = "target_document_id")
    })
public class CmsRelation extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "field_name", nullable = false, length = 100)
  public String fieldName;

  @Column(name = "source_document_id", nullable = false, length = 36)
  public String sourceDocumentId;

  @Column(name = "source_type", nullable = false, length = 100)
  public String sourceType;

  @Column(name = "target_document_id", nullable = false, length = 36)
  public String targetDocumentId;

  @Column(name = "target_type", nullable = false, length = 100)
  public String targetType;

  @Column(name = "order_index")
  public Integer orderIndex = 0;

  // ---- Helper Finders ----

  /** Finds all relations from a source document for a given field, ordered by index. */
  public static java.util.List<CmsRelation> findRelations(
      String sourceDocumentId, String fieldName) {
    return list(
        "sourceDocumentId = ?1 and fieldName = ?2 order by orderIndex asc",
        sourceDocumentId,
        fieldName);
  }

  /** Finds all relations targeting a specific document (reverse lookup). */
  public static java.util.List<CmsRelation> findTargeting(String targetDocumentId) {
    return list("targetDocumentId", targetDocumentId);
  }

  /** Deletes all relations for a given source document and field name. */
  public static long deleteRelations(String sourceDocumentId, String fieldName) {
    return delete("sourceDocumentId = ?1 and fieldName = ?2", sourceDocumentId, fieldName);
  }
}
