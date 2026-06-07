package com.quarkus.cms.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for bulk delete — specifies which documents to remove.
 */
@Schema(
    name = "BulkDeleteRequest",
    description = "Request body for bulk delete — specifies which documents to remove")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BulkDeleteRequest {

  @NotEmpty(message = "documentIds must not be empty")
  @Schema(description = "Document IDs of the entries to delete", required = true, example = "[\"abc123\", \"def456\"]")
  private List<@NotBlank(message = "documentId must not be blank") String> documentIds;

  public BulkDeleteRequest() {}

  public BulkDeleteRequest(List<String> documentIds) {
    this.documentIds = documentIds;
  }

  public List<String> getDocumentIds() {
    return documentIds;
  }

  public void setDocumentIds(List<String> documentIds) {
    this.documentIds = documentIds;
  }
}
