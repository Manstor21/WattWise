package com.wattwise.testutil;

import com.wattwise.security.UserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

/**
 * Builds a {@link SecurityContext} whose principal is a {@link UserPrincipal}.
 */
public class WithMockPrincipalFactory implements WithSecurityContextFactory<WithMockPrincipal> {

    @Override
    public SecurityContext createSecurityContext(WithMockPrincipal annotation) {
        UserPrincipal principal = new UserPrincipal(
                annotation.id(), annotation.username(), "test-password", annotation.role());
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        return context;
    }
}