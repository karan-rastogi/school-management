package com.school.user.service;

import com.school.audit.service.AuditService;
import com.school.auth.service.PhoneNumbers;
import com.school.common.error.ApiException;
import com.school.common.error.ErrorCode;
import com.school.common.web.PageResponse;
import com.school.user.domain.Role;
import com.school.user.domain.User;
import com.school.user.domain.UserRole;
import com.school.user.repository.UserRepository;
import com.school.user.repository.UserRoleRepository;
import com.school.user.web.dto.UserCreateRequest;
import com.school.user.web.dto.UserResponse;
import com.school.user.web.dto.UserRolesRequest;
import com.school.user.web.dto.UserUpdateRequest;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuditService auditService;

    public UserAdminService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(Role role, String query, Boolean active, Pageable pageable) {
        Page<User> page = userRepository.findAll(specification(role, query, active), pageable);
        Map<Long, Set<Role>> rolesByUser = rolesFor(page.getContent().stream().map(User::getId).toList());

        return PageResponse.from(page, user -> toResponse(user, rolesByUser.getOrDefault(user.getId(), Set.of())));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> options(Role role) {
        List<User> users = userRepository.findAll(specification(role, null, Boolean.TRUE));
        Map<Long, Set<Role>> rolesByUser = rolesFor(users.stream().map(User::getId).toList());
        return users.stream()
                .sorted((left, right) -> left.getName().compareToIgnoreCase(right.getName()))
                .map(user -> toResponse(user, rolesByUser.getOrDefault(user.getId(), Set.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        User user = find(id);
        return toResponse(user, rolesOf(id));
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        String email = normalizeEmail(request.email());
        String phone = normalizePhone(request.phone());
        requireReachable(email, phone);

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPhone(phone);
        user.setActive(request.active() == null || request.active());
        requireUniqueContact(user, null);

        User saved = userRepository.saveAndFlush(user);
        replaceRoles(saved.getId(), request.roles());
        auditService.record("USER_CREATED", "USER", saved.getId());
        return toResponse(saved, rolesOf(saved.getId()));
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        User user = find(id);
        String email = normalizeEmail(request.email());
        String phone = normalizePhone(request.phone());
        requireReachable(email, phone);

        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPhone(phone);
        if (request.active() != null) {
            user.setActive(request.active());
        }
        requireUniqueContact(user, id);

        User saved = userRepository.saveAndFlush(user);
        auditService.record("USER_UPDATED", "USER", id);
        return toResponse(saved, rolesOf(id));
    }

    @Transactional
    public UserResponse replaceRoles(Long id, Set<Role> roles) {
        User user = find(id);
        if (roles == null || roles.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "A user needs at least one role, otherwise they cannot sign in.");
        }

        Set<Role> previous = rolesOf(id);
        userRoleRepository.deleteAll(userRoleRepository.findByUserId(id));

        Set<Role> ordered = new LinkedHashSet<>(EnumSet.copyOf(roles));
        for (Role role : ordered) {
            UserRole userRole = new UserRole();
            userRole.setUserId(id);
            userRole.setRole(role);
            userRoleRepository.save(userRole);
        }
        userRoleRepository.flush();

        auditService.record("USER_ROLES_CHANGED", "USER", id,
                "from=" + previous + " to=" + ordered);
        return toResponse(user, rolesOf(id));
    }

    @Transactional
    public void delete(Long id) {
        User user = find(id);
        // Record before the row disappears so the acting user still satisfies the audit FK.
        auditService.record("USER_DELETED", "USER", id);
        userRoleRepository.deleteAll(userRoleRepository.findByUserId(id));
        userRepository.delete(user);
    }

    private void replaceRoles(Long userId, Collection<Role> roles) {
        Set<Role> ordered = new LinkedHashSet<>(EnumSet.copyOf(roles));
        for (Role role : ordered) {
            UserRole userRole = new UserRole();
            userRole.setUserId(userId);
            userRole.setRole(role);
            userRoleRepository.save(userRole);
        }
        userRoleRepository.flush();
    }

    private Map<Long, Set<Role>> rolesFor(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRoleRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.groupingBy(
                        UserRole::getUserId,
                        Collectors.mapping(UserRole::getRole, Collectors.toCollection(() -> EnumSet.noneOf(Role.class)))));
    }

    private Set<Role> rolesOf(Long userId) {
        return orderedRoles(rolesFor(List.of(userId)).getOrDefault(userId, EnumSet.noneOf(Role.class)));
    }

    private Set<Role> orderedRoles(Set<Role> roles) {
        return new LinkedHashSet<>(roles);
    }

    private void requireUniqueContact(User candidate, Long existingId) {
        if (candidate.getEmail() != null) {
            userRepository.findByEmailIgnoreCase(candidate.getEmail())
                    .filter(other -> !other.getId().equals(existingId))
                    .ifPresent(other -> {
                        throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE,
                                "Another account already uses that email address.");
                    });
        }
        if (candidate.getPhone() != null) {
            userRepository.findByPhone(candidate.getPhone())
                    .filter(other -> !other.getId().equals(existingId))
                    .ifPresent(other -> {
                        throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE,
                                "Another account already uses that phone number.");
                    });
        }
    }

    private void requireReachable(String email, String phone) {
        if (email == null && phone == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "Provide an email address, a phone number, or both so the user can sign in.");
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim();
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return PhoneNumbers.normalize(phone);
    }

    private User find(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "User not found."));
    }

    private static Specification<User> specification(Role role, String query, Boolean active) {
        List<Specification<User>> specifications = new ArrayList<>();
        if (role != null) {
            specifications.add(hasRole(role));
        }
        if (query != null && !query.isBlank()) {
            specifications.add(matches(query));
        }
        if (active != null) {
            specifications.add(isActive(active));
        }
        return specifications.isEmpty() ? null : Specification.allOf(specifications);
    }

    private static Specification<User> hasRole(Role role) {
        return (root, query, builder) -> {
            Subquery<Long> members = query.subquery(Long.class);
            Root<UserRole> userRole = members.from(UserRole.class);
            members.select(userRole.get("userId")).where(builder.equal(userRole.get("role"), role));
            return root.get("id").in(members);
        };
    }

    private static Specification<User> matches(String query) {
        String pattern = "%" + query.trim().toLowerCase() + "%";
        return (root, criteriaQuery, builder) -> builder.or(
                builder.like(builder.lower(root.get("name")), pattern),
                builder.like(builder.lower(builder.coalesce(root.<String>get("email"), "")), pattern),
                builder.like(builder.coalesce(root.<String>get("phone"), ""), pattern));
    }

    private static Specification<User> isActive(Boolean active) {
        return (root, query, builder) -> builder.equal(root.get("active"), active);
    }

    private static UserResponse toResponse(User user, Set<Role> roles) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.isActive(),
                new LinkedHashSet<>(roles),
                user.getCreatedAt());
    }
}
