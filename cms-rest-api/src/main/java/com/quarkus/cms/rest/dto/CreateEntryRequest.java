package com.quarkus.cms.rest.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for creating or updating a content entry.
 *
 * <p>Accepts arbitrary field data that is stored in the JSONB {@code data} column, mirroring
 * Strapi's flexible schema approach. The {@code data} map carries the content-type fields.
 */
@Schema(
    name = "CreateEntryRequest",
    description = "Request body for creating or updating a content entry with flexible key-value data")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateEntryRequest {

  @NotBlank(message = "contentType must not be blank")
  @Schema(description = "Content type name", required = true, example = "article")
  private String contentType;

  @Schema(description = "Locale code (e.g., en, fr)")
  private String locale;

  @Schema(description = "Entry status (draft, published)")
  private String status;

  @Schema(description = "Content-type field data (key-value pairs)")
  private Map<String, Object> data = new HashMap<>();

  @Schema(description = "Relation IDs to connect")
  private List<Long> connect;
  @Schema(description = "Relation IDs to disconnect")
  private List<Long> disconnect;

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public String getLocale() {
    return locale;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public List<Long> getConnect() {
    return connect;
  }

  public void setConnect(List<Long> connect) {
    this.connect = connect;
  }

  public List<Long> getDisconnect() {
    return disconnect;
  }

  public void setDisconnect(List<Long> disconnect) {
    this.disconnect = disconnect;
  }

  @JsonAnyGetter
  public Map<String, Object> getData() {
    return data;
  }

  @JsonAnySetter
  public void setDataField(String key, Object value) {
    if (data == null) {
      data = new HashMap<>();
    }
    data.put(key, value);
  }

  /** Returns only the content-type fields (excludes metadata keys). */
  public Map<String, Object> extractContentFields() {
    Map<String, Object> fields = new HashMap<>(data != null ? data : Map.of());
    fields.remove("contentType");
    fields.remove("locale");
    fields.remove("status");
    fields.remove("connect");
    fields.remove("disconnect");
    return fields;
  }

  /** Copies non-sensitive metadata from the data map into typed fields. */
  public void normalize() {
    if (data != null) {
      Object ct = data.remove("contentType");
      if (ct != null && contentType == null) {
        contentType = ct.toString();
      }
      Object loc = data.remove("locale");
      if (loc != null && locale == null) {
        locale = loc.toString();
      }
      Object st = data.remove("status");
      if (st != null && status == null) {
        status = st.toString();
      }
    }
  }
}
