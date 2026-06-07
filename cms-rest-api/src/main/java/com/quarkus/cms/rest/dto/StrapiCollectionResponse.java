package com.quarkus.cms.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Strapi v5-compatible JSON envelope for collection (list) responses.
 *
 * <p>Wraps a list of data objects with pagination metadata following the Strapi Content API
 * response format: {@code {"data": [...], "meta": {"pagination": {...}}}}.
 */
@Schema(
    name = "CollectionResponse",
    description = "Standard Strapi-compatible collection response envelope with data array and metadata")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"data", "meta"})
public class StrapiCollectionResponse<T> {

  private List<T> data;
  private Map<String, Object> meta;

  public StrapiCollectionResponse() {}

  public StrapiCollectionResponse(List<T> data) {
    this.data = data;
    this.meta = Collections.emptyMap();
  }

  public StrapiCollectionResponse(List<T> data, PaginationMeta pagination) {
    this.data = data;
    this.meta =
        pagination != null ? Map.of("pagination", (Object) pagination) : Collections.emptyMap();
  }

  public StrapiCollectionResponse(List<T> data, Map<String, Object> meta) {
    this.data = data;
    this.meta = meta != null ? meta : Collections.emptyMap();
  }

  public List<T> getData() {
    return data;
  }

  public void setData(List<T> data) {
    this.data = data;
  }

  public Map<String, Object> getMeta() {
    return meta;
  }

  public void setMeta(Map<String, Object> meta) {
    this.meta = meta;
  }
}
