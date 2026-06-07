package com.quarkus.cms.rest.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for creating an entry as part of a bulk operation.
 *
 * <p>Supports the same flexible data format as single-entry creation, but
 * without the content-type path parameter (content type is inferred from
 * the enclosing bulk endpoint path).
 */
@Schema(
    name = "BulkCreateEntry",
    description = "Request entry for bulk create — flexible key-value data fields")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BulkCreateEntry {

  @JsonIgnore
  private Map<String, Object> dataFields = new HashMap<>();

  @Size(min = 1, message = "Entry data must contain at least one field")
  @JsonAnyGetter
  public Map<String, Object> getDataFields() {
    return dataFields;
  }

  @JsonAnySetter
  public void setDataField(String key, Object value) {
    dataFields.put(key, value);
  }
}
