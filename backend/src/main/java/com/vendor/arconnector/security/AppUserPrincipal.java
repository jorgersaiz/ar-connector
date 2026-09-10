package com.vendor.arconnector.security;

import com.vendor.arconnector.domain.AppUser;
import com.vendor.arconnector.domain.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** Usuario autenticado en sesión: id, rol y persona asociada, sin arrastrar la entidad JPA. */
public class AppUserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final String displayName;
    private final UserRole role;
    private final Long personId;
    private final String personDisplayName;
    private final boolean active;

    public AppUserPrincipal(AppUser user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.displayName = user.getDisplayName();
        this.role = user.getRole();
        this.personId = user.personId();
        this.personDisplayName = user.getPerson() == null ? null : user.getPerson().getDisplayName();
        this.active = user.isActive();
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UserRole getRole() {
        return role;
    }

    public Long getPersonId() {
        return personId;
    }

    public String getPersonDisplayName() {
        return personDisplayName;
    }

    public boolean isManager() {
        return role == UserRole.MANAGER;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
