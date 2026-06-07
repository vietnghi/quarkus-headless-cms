package com.quarkus.cms.auth.security;

import com.quarkus.cms.auth.entity.CmsUser;
import com.quarkus.cms.auth.service.PermissionService;
import com.quarkus.cms.core.security.PermissionCheck;
import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.UriInfo;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Interceptor for {@link PermissionCheck} annotation.
 *
 * <p>Evaluates the required permission against the authenticated user. If the user lacks the
 * permission, a {@link SecurityException} is thrown, which is mapped to a 403 response by {@code
 * CmsExceptionMapper}.
 *
 * <p>Supports template-based permission actions that resolve {@code {pathParam}} placeholders from
 * JAX-RS path parameters.
 */
@Interceptor
@PermissionCheck
@Priority(Interceptor.Priority.APPLICATION + 100)
public class PermissionCheckInterceptor {

  @Inject PermissionService permissionService;

  @Inject SecurityIdentity identity;

  @Inject UriInfo uriInfo;

  /** Pattern to match template placeholders like {@code {contentType}}. */
  private static final Pattern TEMPLATE_PLACEHOLDER = Pattern.compile("\\{([^}]+)\\}");

  @AroundInvoke
  public Object checkPermission(InvocationContext ctx) throws Exception {
    PermissionCheck annotation = ctx.getMethod().getAnnotation(PermissionCheck.class);
    if (annotation == null) {
      annotation = ctx.getTarget().getClass().getAnnotation(PermissionCheck.class);
    }
    if (annotation == null) {
      return ctx.proceed();
    }

    // Resolve the action string — prefer exact value, fall back to template
    String action = annotation.value();
    if (action == null || action.isEmpty()) {
      action = annotation.actionTemplate();
      if (action != null && !action.isEmpty()) {
        action = resolveTemplate(action, ctx.getMethod(), ctx.getParameters());
      }
    }

    if (action == null || action.isEmpty()) {
      return ctx.proceed();
    }

    // Get the current user
    CmsUser user = resolveCurrentUser();
    if (user == null) {
      // If auth is disabled, allow the request through
      String authEnabled = System.getProperty("quarkus.cms.auth.api-tokens.enabled", "true");
      if ("false".equalsIgnoreCase(authEnabled)) {
        return ctx.proceed();
      }
      throw new SecurityException("Authentication required for: " + action);
    }

    // Check permission
    if (!permissionService.isPermitted(user, action)) {
      Log.warnf("Permission denied for user %s on action %s", user.username, action);
      throw new SecurityException("Forbidden — insufficient permissions for: " + action);
    }

    return ctx.proceed();
  }

  /**
   * Resolves template placeholders in {@code template} using the method's {@link PathParam}
   * annotations and their corresponding argument values.
   *
   * <p>Placeholders like {@code {contentType}} are replaced with the actual path parameter value
   * from the intercepted method invocation.
   */
  private String resolveTemplate(String template, Method method, Object[] args) {
    String result = template;
    Parameter[] methodParams = method.getParameters();

    for (int i = 0; i < methodParams.length; i++) {
      PathParam pathParam = methodParams[i].getAnnotation(PathParam.class);
      if (pathParam != null && i < args.length && args[i] != null) {
        String placeholder = "{" + pathParam.value() + "}";
        if (result.contains(placeholder)) {
          result = result.replace(placeholder, args[i].toString());
        }
      }
    }

    return result;
  }

  /**
   * Resolves the current CMS user from the security context.
   *
   * <p>Looks for the user ID in the JWT subject claim, then fetches the user entity.
   */
  private CmsUser resolveCurrentUser() {
    if (identity == null || identity.isAnonymous()) {
      return null;
    }

    // Extract user ID from JWT
    if (identity.getPrincipal() instanceof JsonWebToken jwt) {
      String subject = jwt.getSubject();
      if (subject != null) {
        try {
          Long userId = Long.parseLong(subject);
          return CmsUser.findById(userId);
        } catch (NumberFormatException e) {
          Log.debugf("JWT subject is not a numeric user ID: %s", subject);
        }
      }
    }

    // Fallback: try the security identity principal name
    String name = identity.getPrincipal().getName();
    if (name != null) {
      CmsUser user = CmsUser.findByUsername(name);
      if (user != null) {
        return user;
      }
    }

    return null;
  }
}
