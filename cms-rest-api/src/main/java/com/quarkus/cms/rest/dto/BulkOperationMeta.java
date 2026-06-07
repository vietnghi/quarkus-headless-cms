package com.quarkus.cms.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Metadata for bulk operation responses — summary counts of successes and failures.
 */
@Schema(
    name = "BulkOperationMeta",
    description = "Summary counts of successes and failures in a bulk operation")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BulkOperationMeta {

  @Schema(description = "Total number of items in the bulk request", example = "10")
  private int total;
  @Schema(description = "Number of succeeded operations", example = "8")
  private int succeeded;
  @Schema(description = "Number of failed operations", example = "2")
  private int failed;

  public BulkOperationMeta() {}

  public BulkOperationMeta(int total, int succeeded, int failed) {
    this.total = total;
    this.succeeded = succeeded;
    this.failed = failed;
  }

  public int getTotal() {
    return total;
  }

  public void setTotal(int total) {
    this.total = total;
  }

  public int getSucceeded() {
    return succeeded;
  }

  public void setSucceeded(int succeeded) {
    this.succeeded = succeeded;
  }

  public int getFailed() {
    return failed;
  }

  public void setFailed(int failed) {
    this.failed = failed;
  }
}
