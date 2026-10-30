package com.school.auth.security;

import com.school.user.domain.Role;
import com.school.user.domain.User;
import com.school.user.repository.UserRepository;
import com.school.user.repository.UserRoleRepository;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class UserAccountService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public UserAccountService(UserRepository userRepository, UserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    public AuthenticatedUser loadActiveUser(Long userId, Role preferredActiveRole) {
        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElse(null);
        if (user == null) {
            return null;
        }

        Set<Role> roles = rolesOf(userId);
        if (roles.isEmpty()) {
            return null;
        }

        Role activeRole = preferredActiveRole != null && roles.contains(preferredActiveRole)
                ? preferredActiveRole
                : roles.iterator().next();

        return new AuthenticatedUser(
                user.getId(), user.getName(), user.getEmail(), user.getPhone(),
                roles, activeRole, user.isMustChangePassword());
    }

    public Set<Role> rolesOf(Long userId) {
        EnumSet<Role> roles = EnumSet.noneOf(Role.class);
        userRoleRepository.findByUserId(userId).forEach(userRole -> roles.add(userRole.getRole()));
        return new LinkedHashSet<>(roles);
    }
}
