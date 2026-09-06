package com.wattwise.testutil;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Test annotation that populates the SecurityContext with a real
 * {@code UserPrincipal} (id 1 by default), so controllers that cast
 * {@code authentication.getPrincipal()} work in {@code @WebMvcTest} slices.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@WithSecurityContext(factory = WithMockPrincipalFactory.class)
public @interface WithMockPrincipal {

    long id() default 1L;

    String username() default "alice";

    String role() default "USER";
}