package com.school.auth.security;

import com.school.user.domain.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public final class AuthenticatedUser implements UserDetails {

    private final Long id;
    private final String name;
    private final String email;
    private final String phone;
    private final Set<Role> roles;
    private final Role activeRole;

    public AuthenticatedUser(Long id, String name, String email, String phone, Set<Role> roles, Role activeRole) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.roles = Set.copyOf(new LinkedHashSet<>(roles));
        this.activeRole = activeRole;
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String email() {
        return email;
    }

    public String phone() {
        return phone;
    }

    public Set<Role> roles() {
        return roles;
    }

    public Role activeRole() {
        return activeRole;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return String.valueOf(id);
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String toString() {
        return "AuthenticatedUser[id=" + id + "]";
    }
}
