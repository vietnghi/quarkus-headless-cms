package com.quarkus.cms.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for the batch endpoint.
 *
 * <p>Accepts an ordered list of operations to execute sequentially within a single
 * database transaction. If any operation fails, all preceding operations are rolled back.
 */
@Schema(
    name = "BatchRequest",
    description = "Request body for the batch endpoint — an ordered list of operations executed sequentially within a single transaction")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BatchRequest {

  @NotEmpty(message = "requests must not be empty")
  @Valid
  @Schema(description = "Ordered list of operations to execute", required = true)
  private List<BatchOperation> requests;

  public BatchRequest() {}

  public BatchRequest(List<BatchOperation> requests) {
    this.requests = requests;
  }

  public List<BatchOperation> getRequests() {
    return requests;
  }

  public void setRequests(List<BatchOperation> requests) {
    this.requests = requests;
  }

  /** A single operation within a batch request. */
  @Schema(name = "BatchOperation", description = "A single operation within a batch request")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class BatchOperation {

    @NotBlank(message = "method must not be blank")
    @Schema(description = "HTTP method", example = "POST", required = true)
    private String method;

    @NotBlank(message = "path must not be blank")
    @Schema(description = "Request path (must start with /api/)", example = "/api/articles", required = true)
    private String path;

    @Schema(description = "Request body as a JSON object (key-value pairs)")
    private Map<String, Object> body;

    @Schema(description = "Optional query parameters")
    private Map<String, String> queryParams;

    public BatchOperation() {}

    public BatchOperation(String method, String path, Map<String, Object> body) {
      this.method = method;
      this.path = path;
      this.body = body;
    }

    public String getMethod() {
      return method;
    }

    public void setMethod(String method) {
      this.method = method;
    }

    public String getPath() {
      return path;
    }

    public void setPath(String path) {
      this.path = path;
    }

    public Map<String, Object> getBody() {
      return body;
    }

    public void setBody(Map<String, Object> body) {
      this.body = body;
    }

    public Map<String, String> getQueryParams() {
      return queryParams;
    }

    public void setQueryParams(Map<String, String> queryParams) {
      this.queryParams = queryParams;
    }
  }
}
