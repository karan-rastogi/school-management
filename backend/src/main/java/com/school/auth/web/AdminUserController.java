package com.school.auth.web;

import com.school.auth.web.dto.AdminUserResponse;
import com.school.user.domain.Role;
import com.school.user.domain.User;
import com.school.user.domain.UserRole;
import com.school.user.repository.UserRepository;
import com.school.user.repository.UserRoleRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
public class AdminUserController {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public AdminUserController(UserRepository userRepository, UserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AdminUserResponse> listUsers() {
        Map<Long, Set<Role>> rolesByUser = userRoleRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        UserRole::getUserId,
                        Collectors.mapping(UserRole::getRole, Collectors.toCollection(() -> EnumSet.noneOf(Role.class)))));

        return userRepository.findAll().stream()
                .map(user -> toResponse(user, rolesByUser.getOrDefault(user.getId(), Set.of())))
                .toList();
    }

    private AdminUserResponse toResponse(User user, Set<Role> roles) {
        return new AdminUserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.isActive(),
                new LinkedHashSet<>(roles));
    }
}
