package com.wattwise.testutil;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotación de test que rellena el SecurityContext con un {@code UserPrincipal}
 * real (id 1 por defecto), para que los controllers que hacen cast de
 * {@code authentication.getPrincipal()} funcionen en slices {@code @WebMvcTest}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@WithSecurityContext(factory = WithMockPrincipalFactory.class)
public @interface WithMockPrincipal {

    long id() default 1L;

    String username() default "alice";

    String role() default "USER";
}