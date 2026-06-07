package com.quarkus.cms.core.security;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative permission check annotation for REST endpoints.
 *
 * <p>Apply to JAX-RS resource methods to enforce that the authenticated user has the required
 * permission. The interceptor {@code PermissionCheckInterceptor} (in cms-auth) reads the
 * authenticated user from the security context and evaluates the permission using {@code
 * PermissionService}.
 *
 * <h3>Usage:</h3>
 *
 * <pre>{@code
 * @POST
 * @PermissionCheck("api::article.article.create")
 * public Response createArticle(CreateArticleRequest req) { ... }
 * }</pre>
 *
 * <p>For dynamic content-type endpoints, use the {@code actionTemplate} parameter with path
 * parameter placeholders:
 *
 * <pre>{@code
 * @PUT
 * @Path("/{contentType}/{documentId}")
 * @PermissionCheck(actionTemplate = "api::{contentType}.{contentType}.update")
 * public Response update(@PathParam("contentType") String ct, ...) { ... }
 * }</pre>
 */
@Inherited
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface PermissionCheck {

  /**
   * The exact permission action string required (e.g. "api::article.article.read").
   *
   * <p>If set, {@link #actionTemplate()} is ignored.
   */
  @Nonbinding String value() default "";

  /**
   * A template for the permission action with path parameter placeholders in curly braces (e.g.
   * "api::{contentType}.{contentType}.update"). The interceptor resolves placeholders from the
   * JAX-RS request context.
   *
   * <p>Only used when {@link #value()} is empty.
   */
  @Nonbinding String actionTemplate() default "";
}
