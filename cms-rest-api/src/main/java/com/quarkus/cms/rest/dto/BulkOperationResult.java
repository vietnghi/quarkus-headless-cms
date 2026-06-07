package com.quarkus.cms.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Result of a single bulk operation entry.
 *
 * <p>Carries the outcome of one item within a bulk request — either the
 * resulting data on success, or error details on failure.
 */
@Schema(
    name = "BulkOperationResult",
    description = "Result of a single item within a bulk request — success data or error details")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BulkOperationResult {

  @Schema(description = "Zero-based index of this item in the original request", example = "0")
  private int index;
  @Schema(description = "HTTP status code for this item", example = "200")
  private Integer status;
  @Schema(description = "Resulting data on success")
  private Map<String, Object> data;
  @Schema(description = "Error message on failure")
  private String error;
  @Schema(description = "Error name/type on failure", example = "ValidationError")
  private String errorName;

  public BulkOperationResult() {}

  public BulkOperationResult(int index, Integer status, Map<String, Object> data) {
    this.index = index;
    this.status = status;
    this.data = data;
  }

  public BulkOperationResult(int index, Integer status, String error, String errorName) {
    this.index = index;
    this.status = status;
    this.error = error;
    this.errorName = errorName;
  }

  /** Creates a successful result with the given data. */
  public static BulkOperationResult success(int index, Map<String, Object> data) {
    return new BulkOperationResult(index, 200, data);
  }

  /** Creates a failed result with error details. */
  public static BulkOperationResult failure(int index, int status, String error, String errorName) {
    return new BulkOperationResult(index, status, error, errorName);
  }

  public int getIndex() {
    return index;
  }

  public void setIndex(int index) {
    this.index = index;
  }

  public Integer getStatus() {
    return status;
  }

  public void setStatus(Integer status) {
    this.status = status;
  }

  public Map<String, Object> getData() {
    return data;
  }

  public void setData(Map<String, Object> data) {
    this.data = data;
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
