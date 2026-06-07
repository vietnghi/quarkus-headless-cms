package com.quarkus.cms.graphql.model;

import org.eclipse.microprofile.graphql.DefaultValue;
import org.eclipse.microprofile.graphql.Input;
import org.eclipse.microprofile.graphql.Name;

/**
 * Pagination input for GraphQL list queries.
 *
 * <p>Mirrors Strapi v5 pagination with {@code page} and {@code pageSize} semantics.
 */
@Input("PaginationInput")
public class PaginationInput {

  @DefaultValue("1")
  private int page = 1;

  @DefaultValue("25")
  @Name("pageSize")
  private int pageSize = 25;

  public PaginationInput() {}

  public PaginationInput(int page, int pageSize) {
    this.page = page;
    this.pageSize = pageSize;
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
}
