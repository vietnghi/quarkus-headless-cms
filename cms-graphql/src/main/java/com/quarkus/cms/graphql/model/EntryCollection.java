package com.quarkus.cms.graphql.model;

import java.util.List;

/**
 * Paginated collection wrapper for GraphQL responses.
 *
 * <p>Mirrors the Strapi v5 collection response structure with a {@code data} array and
 * {@code meta} pagination information.
 */
public class EntryCollection {

  private final List<Entry> data;
  private final PaginationMeta meta;

  public EntryCollection(List<Entry> data, int page, int pageSize, long total) {
    this.data = data;
    this.meta = new PaginationMeta(page, pageSize, total);
  }

  public List<Entry> getData() {
    return data;
  }

  public PaginationMeta getMeta() {
    return meta;
  }

  /** Pagination metadata type. */
  public static class PaginationMeta {

    private final int page;
    private final int pageSize;
    private final long total;
    private final int pageCount;

    public PaginationMeta(int page, int pageSize, long total) {
      this.page = page;
      this.pageSize = pageSize;
      this.total = total;
      this.pageCount = pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;
    }

    public int getPage() {
      return page;
    }

    public int getPageSize() {
      return pageSize;
    }

    public long getTotal() {
      return total;
    }

    public int getPageCount() {
      return pageCount;
    }
  }
}
