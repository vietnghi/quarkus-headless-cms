package com.quarkus.cms.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Strapi v5-compatible JSON envelope for single-entity responses.
 *
 * <p>Wraps a single data object with optional metadata following the Strapi Content API
 * response format: {@code {"data": {...}, "meta": {...}}}.
 */
@Schema(
    name = "SingleResponse",
    description = "Standard Strapi-compatible single-entity response envelope with data object and metadata")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"data", "meta"})
public class StrapiSingleResponse<T> {

  private T data;
  private Map<String, Object> meta;

  public StrapiSingleResponse() {}

  public StrapiSingleResponse(T data) {
    this.data = data;
    this.meta = Collections.emptyMap();
  }

  public StrapiSingleResponse(T data, Map<String, Object> meta) {
    this.data = data;
    this.meta = meta;
  }

  public T getData() {
    return data;
  }

  public void setData(T data) {
    this.data = data;
  }

  public Map<String, Object> getMeta() {
    return meta;
  }

  public void setMeta(Map<String, Object> meta) {
    this.meta = meta;
  }
}
