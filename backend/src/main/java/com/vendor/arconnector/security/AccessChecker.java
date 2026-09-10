package com.vendor.arconnector.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Comprobaciones de acceso usadas desde las anotaciones @PreAuthorize, p.ej.
 * {@code @PreAuthorize("hasRole('MANAGER') or @access.self(#id)")}.
 */
@Component("access")
public class AccessChecker {

    /** true si el usuario autenticado es la persona con ese id. */
    public boolean self(Long personId) {
        AppUserPrincipal me = current();
        return me != null && personId != null && personId.equals(me.getPersonId());
    }

    public static AppUserPrincipal current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserPrincipal p)) {
            return null;
        }
        return p;
    }
}
