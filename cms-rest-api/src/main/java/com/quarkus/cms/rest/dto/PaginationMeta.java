package com.quarkus.cms.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Strapi v5-compatible pagination metadata.
 *
 * <p>Included in the {@code meta} object of collection responses:
 * {@code {"pagination": {"page": 1, "pageSize": 25, "pageCount": 1, "total": 10}}}.
 */
@Schema(
    name = "PaginationMeta",
    description = "Pagination metadata included in collection responses")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"page", "pageSize", "pageCount", "total"})
public class PaginationMeta {

  @Schema(description = "Current page number", example = "1")
  private int page;
  @Schema(description = "Number of items per page", example = "25")
  private int pageSize;
  @Schema(description = "Total number of pages", example = "1")
  private int pageCount;
  @Schema(description = "Total number of items across all pages", example = "10")
  private long total;

  public PaginationMeta() {}

  public PaginationMeta(int page, int pageSize, long total) {
    this.page = page;
    this.pageSize = pageSize;
    this.total = total;
    this.pageCount = pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;
  }

  public int getPage() {
    return page;
  }

  public void setPage(int page) {
    this.page = page;
  }

  public int getPageSize() {
    return pageSize;
  }

  public void setPageSize(int pageSize) {
    this.pageSize = pageSize;
  }

  public int getPageCount() {
    return pageCount;
  }

  public void setPageCount(int pageCount) {
    this.pageCount = pageCount;
  }

  public long getTotal() {
    return total;
  }

  public void setTotal(long total) {
    this.total = total;
  }
}
