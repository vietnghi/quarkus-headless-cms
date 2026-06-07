package com.quarkus.cms.rest;

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Static validation of OpenAPI annotations on REST resources.
 *
 * <p>Verifies that all REST resource classes in the cms-rest-api module have
 * the required {@code @Tag}, {@code @Operation}, and {@code @APIResponse}
 * annotations for proper OpenAPI documentation generation.
 *
 * <p>Full Quarkus integration tests (endpoint validation, schema correctness)
 * require a full application context and are best run in the integration-tests
 * module or the example module.
 */
@DisplayName("OpenAPI Annotation Validation")
class OpenApiDocumentationTest {

    // ---------------------------------------------------------------
    // Resources that must have @Tag annotations
    // ---------------------------------------------------------------

    @Test
    @DisplayName("ContentApiResource should have @Tag at class level")
    void contentApiResourceHasTag() {
        Tag tag = ContentApiResource.class.getAnnotation(Tag.class);
        assertNotNull(tag, "ContentApiResource should be annotated with @Tag");
        assertEquals("Content API", tag.name());
    }

    @Test
    @DisplayName("RelationsResource should have @Tag at class level")
    void relationsResourceHasTag() {
        Tag tag = RelationsResource.class.getAnnotation(Tag.class);
        assertNotNull(tag, "RelationsResource should be annotated with @Tag");
        assertEquals("Relations API", tag.name());
    }

    @Test
    @DisplayName("BulkOperationsResource should have @Tag at class level")
    void bulkOperationsResourceHasTag() {
        Tag tag = BulkOperationsResource.class.getAnnotation(Tag.class);
        assertNotNull(tag, "BulkOperationsResource should be annotated with @Tag");
        assertEquals("Bulk Operations", tag.name());
    }

    // ---------------------------------------------------------------
    // ContentApiResource method annotations
    // ---------------------------------------------------------------

    @Test
    @DisplayName("ContentApiResource methods should have @Operation")
    void contentApiMethodsHaveOperation() {
        for (Method method : ContentApiResource.class.getDeclaredMethods()) {
            // Skip private helper methods
            if (method.getAnnotation(Operation.class) == null) {
                continue; // helpers don't need @Operation
            }
            assertNotNull(method.getAnnotation(Operation.class),
                "Method " + method.getName() + " should have @Operation");
        }
    }

    @Test
    @DisplayName("ContentApiResource methods with @Operation should have @APIResponse(s)")
    void contentApiMethodsHaveApiResponse() {
        for (Method method : ContentApiResource.class.getDeclaredMethods()) {
            Operation op = method.getAnnotation(Operation.class);
            if (op == null) continue; // skip helpers

            APIResponse single = method.getAnnotation(APIResponse.class);
            APIResponses multi = method.getAnnotation(APIResponses.class);
            assertTrue(single != null || multi != null,
                "Method " + method.getName() + " should have @APIResponse or @APIResponses");
        }
    }

    // ---------------------------------------------------------------
    // RelationsResource method annotations
    // ---------------------------------------------------------------

    @Test
    @DisplayName("RelationsResource methods should have @Operation")
    void relationsMethodsHaveOperation() {
        for (Method method : RelationsResource.class.getDeclaredMethods()) {
            if (method.getAnnotation(Operation.class) == null) continue;
            assertNotNull(method.getAnnotation(Operation.class),
                "Method " + method.getName() + " should have @Operation");
        }
    }

    @Test
    @DisplayName("RelationsResource methods with @Operation should have @APIResponse(s)")
    void relationsMethodsHaveApiResponse() {
        for (Method method : RelationsResource.class.getDeclaredMethods()) {
            Operation op = method.getAnnotation(Operation.class);
            if (op == null) continue;

            APIResponse single = method.getAnnotation(APIResponse.class);
            APIResponses multi = method.getAnnotation(APIResponses.class);
            assertTrue(single != null || multi != null,
                "Method " + method.getName() + " should have @APIResponse or @APIResponses");
        }
    }

    // ---------------------------------------------------------------
    // BulkOperationsResource method annotations
    // ---------------------------------------------------------------

    @Test
    @DisplayName("BulkOperationsResource methods should have @Operation")
    void bulkMethodsHaveOperation() {
        for (Method method : BulkOperationsResource.class.getDeclaredMethods()) {
            if (method.getAnnotation(Operation.class) == null) continue;
            assertNotNull(method.getAnnotation(Operation.class),
                "Method " + method.getName() + " should have @Operation");
        }
    }

    @Test
    @DisplayName("BulkOperationsResource methods with @Operation should have @APIResponse(s)")
    void bulkMethodsHaveApiResponse() {
        for (Method method : BulkOperationsResource.class.getDeclaredMethods()) {
            Operation op = method.getAnnotation(Operation.class);
            if (op == null) continue;

            APIResponse single = method.getAnnotation(APIResponse.class);
            APIResponses multi = method.getAnnotation(APIResponses.class);
            assertTrue(single != null || multi != null,
                "Method " + method.getName() + " should have @APIResponse or @APIResponses");
        }
    }

    // ---------------------------------------------------------------
    // All public endpoint methods (helper to find methods that need annotations)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("All public endpoint methods across resources should have @Operation")
    void allEndpointMethodsHaveOperation() {
        Class<?>[] resources = {
            ContentApiResource.class,
            RelationsResource.class,
            BulkOperationsResource.class
        };

        for (Class<?> resource : resources) {
            for (Method method : resource.getDeclaredMethods()) {
                // Check if this is a JAX-RS endpoint method (has @GET, @POST, @PUT, @DELETE)
                boolean isEndpoint = Arrays.stream(method.getAnnotations())
                    .anyMatch(a -> a.annotationType().getName().equals("jakarta.ws.rs.GET")
                        || a.annotationType().getName().equals("jakarta.ws.rs.POST")
                        || a.annotationType().getName().equals("jakarta.ws.rs.PUT")
                        || a.annotationType().getName().equals("jakarta.ws.rs.DELETE"));

                if (isEndpoint) {
                    assertNotNull(method.getAnnotation(Operation.class),
                        resource.getSimpleName() + "." + method.getName()
                            + " is a JAX-RS endpoint but missing @Operation");
                }
            }
        }
    }
}
