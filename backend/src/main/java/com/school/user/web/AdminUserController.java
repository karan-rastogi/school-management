package com.school.user.web;

import com.school.common.web.PageRequests;
import com.school.common.web.PageResponse;
import com.school.user.domain.Role;
import com.school.user.service.UserAdminService;
import com.school.user.web.dto.UserCreateRequest;
import com.school.user.web.dto.UserResponse;
import com.school.user.web.dto.UserRolesRequest;
import com.school.user.web.dto.UserUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserAdminService userAdminService;

    public AdminUserController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    public PageResponse<UserResponse> list(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return userAdminService.list(role, query, active, PageRequests.of(page, size, Sort.by("name")));
    }

    @GetMapping("/options")
    public List<UserResponse> options(@RequestParam(required = false) Role role) {
        return userAdminService.options(role);
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return userAdminService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody UserCreateRequest request) {
        return userAdminService.create(request);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return userAdminService.update(id, request);
    }

    @PutMapping("/{id}/roles")
    public UserResponse replaceRoles(@PathVariable Long id, @Valid @RequestBody UserRolesRequest request) {
        return userAdminService.replaceRoles(id, request.roles());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        userAdminService.delete(id);
    }
}
