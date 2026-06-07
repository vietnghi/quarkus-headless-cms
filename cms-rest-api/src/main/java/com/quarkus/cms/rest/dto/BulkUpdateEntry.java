package com.quarkus.cms.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request entry for bulk update — pairs a document ID with its updated field data.
 */
@Schema(
    name = "BulkUpdateEntry",
    description = "Request entry for bulk update — pairs a document ID with its updated field data")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BulkUpdateEntry {

  @NotBlank(message = "documentId must not be blank")
  @Schema(description = "Document ID of the entry to update", required = true, example = "abc123def")
  private String documentId;

  @NotNull(message = "data must not be null")
  @Schema(description = "Field data to update (key-value pairs)", required = true)
  private Map<String, Object> data;

  @Schema(description = "Locale override for this entry")
  private String locale;

  public BulkUpdateEntry() {}

  public BulkUpdateEntry(String documentId, Map<String, Object> data) {
    this.documentId = documentId;
    this.data = data;
  }

  public String getDocumentId() {
    return documentId;
  }

  public void setDocumentId(String documentId) {
    this.documentId = documentId;
  }

  public Map<String, Object> getData() {
    return data;
  }

  public void setData(Map<String, Object> data) {
    this.data = data;
  }

  public String getLocale() {
    return locale;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }
}
