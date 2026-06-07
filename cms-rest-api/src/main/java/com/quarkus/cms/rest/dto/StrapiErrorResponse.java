package com.quarkus.cms.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Strapi v5-compatible error response envelope.
 *
 * <p>Format: {@code {"data": null, "error": {"status": 404, "name": "NotFoundError", "message":
 * "...", "details": {}}}}.
 */
@Schema(
    name = "ErrorResponse",
    description = "Standard Strapi-compatible error response envelope with null data and error details")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"data", "error"})
public class StrapiErrorResponse {

  private Object data;
  private ErrorBody error;

  public StrapiErrorResponse() {}

  private StrapiErrorResponse(int status, String name, String message, Map<String, Object> details) {
    this.data = null;
    this.error = new ErrorBody(status, name, message, details);
  }

  public static StrapiErrorResponse of(
      int status, String name, String message, Map<String, Object> details) {
    return new StrapiErrorResponse(status, name, message, details);
  }

  public static StrapiErrorResponse of(int status, String name, String message) {
    return new StrapiErrorResponse(status, name, message, null);
  }

  public Object getData() {
    return data;
  }

  public ErrorBody getError() {
    return error;
  }

  /** Nested error body matching Strapi's error structure. */
  @Schema(name = "ErrorBody", description = "Error details returned within an ErrorResponse")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class ErrorBody {
    @Schema(description = "HTTP status code", example = "404")
    private int status;
    @Schema(description = "Error name/type", example = "NotFoundError")
    private String name;
    @Schema(description = "Human-readable error message", example = "Entry not found")
    private String message;
    @Schema(description = "Additional error details (field-level errors, etc.)")
    private Map<String, Object> details;

    public ErrorBody() {}

    public ErrorBody(int status, String name, String message, Map<String, Object> details) {
      this.status = status;
      this.name = name;
      this.message = message;
      this.details = details;
    }

    public int getStatus() {
      return status;
    }

    public void setStatus(int status) {
      this.status = status;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getMessage() {
      return message;
    }

    public void setMessage(String message) {
      this.message = message;
    }

    public Map<String, Object> getDetails() {
      return details;
    }

    public void setDetails(Map<String, Object> details) {
      this.details = details;
    }
  }
}
