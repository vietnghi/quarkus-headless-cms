package com.quarkus.cms.core.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Parameter object for querying content entries.
 *
 * Encapsulates filters, sorting, pagination, locale, status, fields selection, and populate
 * parameters that are translated into PostgreSQL JSONB queries by {@link CmsQueryBuilder}.
 */
public class CmsQuery {

  private String contentType;
  private String locale;
  private String status;
  private FilterNode filter;
  private List<SortOrder> sort = new ArrayList<>();
  private int page = 1;
  private int pageSize = 25;
  private List<PopulateNode> populate;

  /**
   * Optional field selection: when non-null and non-empty, only these field names
   * are included in the response data. Follows Strapi v5 {@code fields[]} syntax.
   */
  private Set<String> fields;

  public CmsQuery() {}

  public CmsQuery(String contentType) {
    this.contentType = contentType;
  }

  public String getContentType() {
    return contentType;
  }

  public CmsQuery setContentType(String contentType) {
    this.contentType = contentType;
    return this;
  }

  public String getLocale() {
    return locale;
  }

  public CmsQuery setLocale(String locale) {
    this.locale = locale;
    return this;
  }

  public String getStatus() {
    return status;
  }

  public CmsQuery setStatus(String status) {
    this.status = status;
    return this;
  }

  public FilterNode getFilter() {
    return filter;
  }

  public CmsQuery setFilter(FilterNode filter) {
    this.filter = filter;
    return this;
  }

  public List<SortOrder> getSort() {
    return sort;
  }

  public CmsQuery setSort(List<SortOrder> sort) {
    this.sort = sort;
    return this;
  }

  public CmsQuery addSort(String field, SortOrder.Direction direction) {
    this.sort.add(new SortOrder(field, direction));
    return this;
  }

  public int getPage() {
    return page;
  }

  public CmsQuery setPage(int page) {
    this.page = page;
    return this;
  }

  public int getPageSize() {
    return pageSize;
  }

  public CmsQuery setPageSize(int pageSize) {
    this.pageSize = pageSize;
    return this;
  }

  public List<PopulateNode> getPopulate() {
    return populate;
  }

  public CmsQuery setPopulate(List<PopulateNode> populate) {
    this.populate = populate;
    return this;
  }

  public CmsQuery addPopulate(String fieldName) {
    if (this.populate == null) {
      this.populate = new ArrayList<>();
    }
    this.populate.add(new PopulateNode(fieldName));
    return this;
  }

  /** Shorthand to mark all relations for population. */
  public CmsQuery setPopulateAll() {
    var all = new PopulateNode();
    all.setPopulateAll(true);
    this.populate = List.of(all);
    return this;
  }

  public Set<String> getFields() {
    return fields;
  }

  public CmsQuery setFields(Set<String> fields) {
    this.fields = fields;
    return this;
  }

  /** Returns the 0-based offset for SQL queries. */
  public int getOffset() {
    return (page - 1) * pageSize;
  }

  /** Returns the maximum number of results per page. */
  public int getLimit() {
    return pageSize;
  }
}
