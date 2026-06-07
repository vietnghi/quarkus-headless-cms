package com.quarkus.cms.plugin.endpoint;

import java.util.Map;

/**
 * Context passed to an endpoint handler containing request parameters, headers, and plugin
 * configuration.
 */
public class EndpointContext {

  private final Map<String, String> pathParams;
  private final Map<String, String> queryParams;
  private final Map<String, String> headers;
  private final String body;
  private final Long userId;

  public EndpointContext(
      Map<String, String> pathParams,
      Map<String, String> queryParams,
      Map<String, String> headers,
      String body,
      Long userId) {
    this.pathParams = pathParams == null ? Map.of() : Map.copyOf(pathParams);
    this.queryParams = queryParams == null ? Map.of() : Map.copyOf(queryParams);
    this.headers = headers == null ? Map.of() : Map.copyOf(headers);
    this.body = body;
    this.userId = userId;
  }

  public Map<String, String> getPathParams() {
    return pathParams;
  }

  public Map<String, String> getQueryParams() {
    return queryParams;
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public String getBody() {
    return body;
  }

  public Long getUserId() {
    return userId;
  }

  public String getPathParam(String name) {
    return pathParams.get(name);
  }

  public String getQueryParam(String name) {
    return queryParams.get(name);
  }

  public String getHeader(String name) {
    return headers.get(name);
  }
}
