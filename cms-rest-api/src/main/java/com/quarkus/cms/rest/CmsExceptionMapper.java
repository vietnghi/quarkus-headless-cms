package com.quarkus.cms.rest;

import com.quarkus.cms.rest.dto.StrapiErrorResponse;
import io.quarkus.logging.Log;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps application exceptions to Strapi v5-compatible JSON error responses.
 *
 * <p>Catches common exception types and translates them into the standard
 * {@code {"data": null, "error": {status, name, message, details}}} format.
 *
 * <p>Handles:
 * <ul>
 *   <li>{@link IllegalArgumentException} → 400 ValidationError
 *   <li>{@link jakarta.validation.ConstraintViolationException} → 400 ValidationError (with field-level details)
 *   <li>{@link IllegalStateException} → 409 ConflictError
 *   <li>{@link SecurityException} → 403 ForbiddenError
 *   <li>{@link jakarta.ws.rs.NotFoundException} → 404 NotFoundError
 *   <li>Generic exceptions → 500 Internal Server Error
 * </ul>
 */
@Provider
public class CmsExceptionMapper implements ExceptionMapper<Exception> {

  @Override
  public Response toResponse(Exception exception) {
    // Handle constraint violations from Bean Validation
    if (exception instanceof ConstraintViolationException cve) {
      return buildValidationError(cve);
    }

    // Handle Jakarta REST NotFoundException
    if (exception instanceof NotFoundException) {
      return buildError(Response.Status.NOT_FOUND, exception.getMessage());
    }

    // Unwrap cause chain for known application exceptions
    Throwable cause = unwind(exception);

    if (cause instanceof IllegalArgumentException e) {
      return buildError(Response.Status.BAD_REQUEST, "ValidationError", e.getMessage());
    } else if (cause instanceof IllegalStateException e) {
      return buildError(Response.Status.CONFLICT, "ConflictError", e.getMessage());
    } else if (cause instanceof SecurityException e) {
      return buildError(Response.Status.FORBIDDEN, "ForbiddenError", e.getMessage());
    }

    // Generic fallback
    Log.errorf(exception, "Unhandled exception: %s", exception.getMessage());
    return buildError(Response.Status.INTERNAL_SERVER_ERROR,
        "ApplicationError", "An unexpected error occurred");
  }

  /** Handles Bean Validation constraint violations with field-level details. */
  private Response buildValidationError(ConstraintViolationException cve) {
    Map<String, Object> details = new LinkedHashMap<>();
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    cve.getConstraintViolations().forEach(violation -> {
      String path = violation.getPropertyPath().toString();
      String message = violation.getMessage();
      fieldErrors.put(path, message);
    });
    details.put("errors", fieldErrors);

    StrapiErrorResponse error = StrapiErrorResponse.of(
        400,
        "ValidationError",
        "Validation failed: " + fieldErrors.size() + " field(s) have errors",
        details);
    return Response.status(Response.Status.BAD_REQUEST)
        .entity(error)
        .type(MediaType.APPLICATION_JSON)
        .build();
  }

  private Response buildError(Response.Status status, String message) {
    return buildError(status, toErrorName(status), message);
  }

  private Response buildError(Response.Status status, String name, String message) {
    return buildError(status, name, message, null);
  }

  private Response buildError(Response.Status status, String name, String message,
      Map<String, Object> details) {
    StrapiErrorResponse error = StrapiErrorResponse.of(
        status.getStatusCode(),
        name,
        message,
        details);
    return Response.status(status)
        .entity(error)
        .type(MediaType.APPLICATION_JSON)
        .build();
  }

  /** Unwinds exception cause chain to find the root cause. */
  private static Throwable unwind(Throwable t) {
    Throwable current = t;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    return current;
  }

  private String toErrorName(Response.Status status) {
    return switch (status.getStatusCode()) {
      case 400 -> "ValidationError";
      case 403 -> "ForbiddenError";
      case 404 -> "NotFoundError";
      case 409 -> "ConflictError";
      case 422 -> "UnprocessableEntityError";
      default -> "ApplicationError";
    };
  }
}
