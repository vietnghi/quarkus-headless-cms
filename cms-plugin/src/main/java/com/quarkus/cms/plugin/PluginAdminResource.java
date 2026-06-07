package com.quarkus.cms.plugin;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

/**
 * Admin REST resource for managing the Plugin System.
 *
 * <p>Provides endpoints to list, inspect, and manage plugins. These endpoints are available under
 * the admin API path.
 */
@Path("/admin/plugins")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class PluginAdminResource {

  @Inject PluginManager pluginManager;

  @Inject PluginRegistry pluginRegistry;

  @GET
  public Response listPlugins() {
    List<PluginMetadata> plugins = pluginManager.listPlugins();
    return Response.ok(
            Map.of(
                "data",
                plugins,
                "meta",
                Map.of(
                    "count", plugins.size(),
                    "active", pluginManager.getActivePluginCount(),
                    "total", pluginManager.getTotalPluginCount())))
        .build();
  }

  @GET
  @Path("/status")
  public Response systemStatus() {
    return Response.ok(pluginManager.getSystemStatus()).build();
  }

  @GET
  @Path("/{name}")
  public Response getPlugin(@PathParam("name") String name) {
    PluginManager.PluginDetail detail = pluginManager.getPluginDetail(name);
    if (detail == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(Map.of("error", "Plugin not found: " + name))
          .build();
    }
    return Response.ok(Map.of("data", detail)).build();
  }

  @GET
  @Path("/content-type-extensions")
  public Response getContentTypeExtensions() {
    return Response.ok(Map.of("data", pluginManager.getContentTypeExtensions())).build();
  }

  @GET
  @Path("/endpoint-registrations")
  public Response getEndpointRegistrations() {
    return Response.ok(Map.of("data", pluginManager.getEndpointRegistrations())).build();
  }

  @GET
  @Path("/admin-pages")
  public Response getAdminPages() {
    return Response.ok(Map.of("data", pluginManager.getAdminPages())).build();
  }
}
