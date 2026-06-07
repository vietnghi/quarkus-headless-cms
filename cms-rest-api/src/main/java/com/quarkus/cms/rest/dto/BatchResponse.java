package com.quarkus.cms.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Response from a batch operation.
 *
 * <p>Returns the results of each operation in order, along with a summary
 * of successes and failures.
 */
@Schema(
    name = "BatchResponse",
    description = "Response from a batch operation with per-operation results and summary metadata")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BatchResponse {

  @Schema(description = "Per-operation results in execution order")
  private List<BatchOperationResponse> responses;
  @Schema(description = "Summary metadata (total, succeeded, failed)")
  private Map<String, Object> meta;

  public BatchResponse() {}

  public BatchResponse(List<BatchOperationResponse> responses, Map<String, Object> meta) {
    this.responses = responses;
    this.meta = meta;
  }

  public List<BatchOperationResponse> getResponses() {
    return responses;
  }

  public void setResponses(List<BatchOperationResponse> responses) {
    this.responses = responses;
  }

  public Map<String, Object> getMeta() {
    return meta;
  }

  public void setMeta(Map<String, Object> meta) {
    this.meta = meta;
  }

  /** Result of a single operation within a batch. */
  @Schema(name = "BatchOperationResponse", description = "Result of a single operation within a batch")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class BatchOperationResponse {

    @Schema(description = "HTTP status code", example = "200")
    private int status;
    @Schema(description = "Request path", example = "/api/articles")
    private String path;
    @Schema(description = "HTTP method", example = "POST")
    private String method;
    @Schema(description = "Response body (success payload)")
    private Object body;
    @Schema(description = "Error message if the operation failed")
    private String error;
    @Schema(description = "Error name/type if the operation failed", example = "ValidationError")
    private String errorName;

    public BatchOperationResponse() {}

    public BatchOperationResponse(int status, String path, String method, Object body) {
      this.status = status;
      this.path = path;
      this.method = method;
      this.body = body;
    }

    public BatchOperationResponse(int status, String path, String method,
                                   String error, String errorName) {
      this.status = status;
      this.path = path;
      this.method = method;
      this.error = error;
      this.errorName = errorName;
    }

    public int getStatus() {
      return status;
    }

    public void setStatus(int status) {
      this.status = status;
    }

    public String getPath() {
      return path;
    }

    public void setPath(String path) {
      this.path = path;
    }

    public String getMethod() {
      return method;
    }

    public void setMethod(String method) {
      this.method = method;
    }

    public Object getBody() {
      return body;
    }

    public void setBody(Object body) {
      this.body = body;
    }

    public String getError() {
      return error;
    }

    public void setError(String error) {
      this.error = error;
    }

    public String getErrorName() {
      return errorName;
    }

    public void setErrorName(String errorName) {
      this.errorName = errorName;
    }
  }
}
