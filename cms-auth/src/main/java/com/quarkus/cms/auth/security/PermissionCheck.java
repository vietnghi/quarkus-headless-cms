package com.quarkus.cms.auth.security;

/**
 * Deprecated: use {@link com.quarkus.cms.core.security.PermissionCheck} instead.
 *
 * <p>This annotation has been moved to {@code com.quarkus.cms.core.security} so it can be shared
 * between cms-auth and cms-rest-api modules without creating a circular dependency.
 */
@Deprecated
@java.lang.annotation.Inherited
@jakarta.interceptor.InterceptorBinding
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@java.lang.annotation.Target({java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.TYPE})
public @interface PermissionCheck {

  @jakarta.enterprise.util.Nonbinding String value() default "";

  @jakarta.enterprise.util.Nonbinding String actionTemplate() default "";
}
