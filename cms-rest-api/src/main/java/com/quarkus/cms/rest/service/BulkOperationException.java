package com.quarkus.cms.rest.service;

import com.quarkus.cms.rest.dto.BulkOperationResult;
import jakarta.enterprise.inject.Vetoed;
import java.util.List;

/**
 * Exception thrown when a bulk operation fails at a specific index.
 *
 * <p>Carries the partial results (operations that succeeded before the failure)
 * so the client can see which items were processed before the rollback point.
 */
@Vetoed
public class BulkOperationException extends RuntimeException {

  private final int failedIndex;
  private final List<BulkOperationResult> partialResults;

  public BulkOperationException(String message, int failedIndex,
                                 List<BulkOperationResult> partialResults) {
    super(message);
    this.failedIndex = failedIndex;
    this.partialResults = partialResults;
  }

  public int getFailedIndex() {
    return failedIndex;
  }

  public List<BulkOperationResult> getPartialResults() {
    return partialResults;
  }
}
