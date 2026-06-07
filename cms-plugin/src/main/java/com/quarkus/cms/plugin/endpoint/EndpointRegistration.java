package com.quarkus.cms.plugin.endpoint;

import java.util.Map;
import java.util.function.Function;

/**
 * Describes a custom API endpoint registered by a plugin.
 *
 * <p>Each endpoint defines an HTTP method, a path (relative to the API base path), and a handler
 * function that processes the request. This allows plugins to expose custom REST endpoints without
 * modifying the core CMS routing.
 *
 * <p>Example: an SEO plugin registers {@code GET /api/seo/analyze/<contentType>} to return SEO
 * analysis for a given content type.
 */
public class EndpointRegistration {

  private final String method;
  private final String path;
  private final String description;
  private final Map<String, String> parameters;
  private final Function<EndpointContext, Object> handler;

  public EndpointRegistration(
      String method,
      String path,
      String description,
      Map<String, String> parameters,
      Function<EndpointContext, Object> handler) {
    if (method == null || method.isBlank())
      throw new IllegalArgumentException("HTTP method is required");
    if (path == null || path.isBlank()) throw new IllegalArgumentException("Path is required");
    if (handler == null) throw new IllegalArgumentException("Handler is required");
    this.method = method.toUpperCase();
    this.path = path;
    this.description = description;
    this.parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    this.handler = handler;
  }

  /** HTTP method: GET, POST, PUT, DELETE, PATCH. */
  public String getMethod() {
    return method;
  }

  /** Path relative to the API base, e.g. {@code "/seo/analyze/{contentType}"}. */
  public String getPath() {
    return path;
  }

  /** Human-readable description of what this endpoint does. */
  public String getDescription() {
    return description;
  }

  /** Map of path/query parameter names to descriptions. */
  public Map<String, String> getParameters() {
    return parameters;
  }

  /** The handler function that processes requests. */
  public Function<EndpointContext, Object> getHandler() {
    return handler;
  }

  public static Builder builder(
      String method, String path, Function<EndpointContext, Object> handler) {
    return new Builder(method, path, handler);
  }

  public static class Builder {
    private String method;
    private String path;
    private String description;
    private Map<String, String> parameters;
    private Function<EndpointContext, Object> handler;

    Builder(String method, String path, Function<EndpointContext, Object> handler) {
      this.method = method;
      this.path = path;
      this.handler = handler;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder parameters(Map<String, String> parameters) {
      this.parameters = parameters;
      return this;
    }

    public EndpointRegistration build() {
      return new EndpointRegistration(method, path, description, parameters, handler);
    }
  }
}
