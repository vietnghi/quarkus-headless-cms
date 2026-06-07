package com.quarkus.cms.example;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * Legacy example resource. The main demo API has moved to {@link DemoResource}.
 * This endpoint is preserved for backward compatibility.
 */
@Path("/api/example")
@Produces(MediaType.APPLICATION_JSON)
public class ExampleResource {

    @GET
    @Path("/health")
    public Response health() {
        return Response.ok(Map.of(
            "status", "ok",
            "cms", "quarkus-headless-cms",
            "version", "1.0.0-SNAPSHOT",
            "note", "See /demo/ for the full demo API"
        )).build();
    }
}
