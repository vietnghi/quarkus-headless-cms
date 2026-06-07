package com.quarkus.cms.core.schema.relation;

import com.quarkus.cms.core.domain.CmsRelation;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.RelationDefinition;
import com.quarkus.cms.core.schema.model.RelationType;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * Runtime service for managing relations between content entries.
 *
 * <p>Wraps the generic {@link CmsRelation} entity with schema-aware operations, enforcing
 * cardinality constraints and managing join-table entries.
 */
@ApplicationScoped
public class RelationService {

  @Inject SchemaStorageService schemaService;

  /**
   * Attaches a relation from a source document to a target document. Validates that the relation is
   * defined in the source content type's schema.
   */
  @Transactional
  public CmsRelation attach(
      String sourceDocumentId,
      String sourceType,
      String targetDocumentId,
      String targetType,
      String fieldName,
      int orderIndex) {

    // Validate relation exists in schema
    ContentTypeDefinition sourceCT = schemaService.getContentType(sourceType);
    if (sourceCT == null) {
      throw new IllegalArgumentException("Unknown source content type: " + sourceType);
    }

    RelationDefinition relDef =
        sourceCT.getRelations().stream()
            .filter(r -> r.getFieldName().equals(fieldName))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "No relation '" + fieldName + "' defined in " + sourceType));

    // For non-morph relations, validate target type
    if (!relDef.isMorph() && !"*".equals(relDef.getTarget())) {
      if (!relDef.getTarget().equals(targetType)) {
        throw new IllegalArgumentException(
            "Relation '"
                + fieldName
                + "' expects target type '"
                + relDef.getTarget()
                + "', got '"
                + targetType
                + "'");
      }
    }

    // Enforce cardinality: for one-to-one / one-to-many dominant side,
    // remove existing relations first
    if (relDef.getType() == RelationType.ONE_TO_ONE
        || relDef.getType() == RelationType.ONE_TO_MANY) {
      CmsRelation.deleteRelations(sourceDocumentId, fieldName);
    }

    CmsRelation rel = new CmsRelation();
    rel.fieldName = fieldName;
    rel.sourceDocumentId = sourceDocumentId;
    rel.sourceType = sourceType;
    rel.targetDocumentId = targetDocumentId;
    rel.targetType = targetType;
    rel.orderIndex = orderIndex;
    rel.persist();

    return rel;
  }

  /** Detaches (removes) a specific relation. */
  @Transactional
  public void detach(String sourceDocumentId, String fieldName, String targetDocumentId) {
    CmsRelation.delete(
        "sourceDocumentId = ?1 and fieldName = ?2 and targetDocumentId = ?3",
        sourceDocumentId,
        fieldName,
        targetDocumentId);
  }

  /** Detaches all relations for a given source document + field. */
  @Transactional
  public void detachAll(String sourceDocumentId, String fieldName) {
    CmsRelation.deleteRelations(sourceDocumentId, fieldName);
  }

  /** Finds all target document IDs for a given relation field on a source document. */
  public List<String> findTargetIds(String sourceDocumentId, String fieldName) {
    return CmsRelation.findRelations(sourceDocumentId, fieldName).stream()
        .map(r -> r.targetDocumentId)
        .toList();
  }

  /** Finds all target document IDs with full metadata. */
  public List<CmsRelation> findRelations(String sourceDocumentId, String fieldName) {
    return CmsRelation.findRelations(sourceDocumentId, fieldName);
  }

  /** Reverse lookup: find all source documents targeting a given document. */
  public List<CmsRelation> findTargeting(String targetDocumentId) {
    return CmsRelation.findTargeting(targetDocumentId);
  }

  /** Reorders relations for a given field by providing an ordered list of target IDs. */
  @Transactional
  public void reorder(String sourceDocumentId, String fieldName, List<String> orderedTargetIds) {
    List<CmsRelation> existing = CmsRelation.findRelations(sourceDocumentId, fieldName);
    for (int i = 0; i < orderedTargetIds.size(); i++) {
      String targetId = orderedTargetIds.get(i);
      for (CmsRelation rel : existing) {
        if (rel.targetDocumentId.equals(targetId)) {
          rel.orderIndex = i;
          break;
        }
      }
    }
  }

  /**
   * Removes all relations involving a given document (source or target). Called when a document is
   * deleted (soft cascade).
   */
  @Transactional
  public long removeAllForDocument(String documentId) {
    long count = CmsRelation.delete("sourceDocumentId = ?1 or targetDocumentId = ?1", documentId);
    return count;
  }

  /** Checks whether a relation definition is valid for the given source content type. */
  public boolean isValidRelation(String sourceType, String fieldName) {
    ContentTypeDefinition ct = schemaService.getContentType(sourceType);
    if (ct == null) return false;
    return ct.getRelations().stream().anyMatch(r -> r.getFieldName().equals(fieldName));
  }
}
