package com.quarkus.cms.runtime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/** Health endpoint for the CMS extension. */
@Path("/api/health")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class CmsHealthResource {

  @Inject CmsHealthService healthService;

  @GET
  public Response health() {
    Map<String, String> health = healthService.getHealth();
    return Response.ok(health).build();
  }
}
