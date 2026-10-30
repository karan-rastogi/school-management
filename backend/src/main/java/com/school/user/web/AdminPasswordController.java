package com.school.user.web;

import com.school.user.service.UserAdminService;
import com.school.user.web.dto.SetPasswordRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only password reset. Only created when {@code app.auth.password-login.enabled=true};
 * otherwise the path is unhandled and returns 404.
 */
@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@ConditionalOnProperty(name = "app.auth.password-login.enabled", havingValue = "true")
public class AdminPasswordController {

    private final UserAdminService userAdminService;

    public AdminPasswordController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @PostMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPassword(@PathVariable Long id, @Valid @RequestBody SetPasswordRequest request) {
        userAdminService.setPassword(id, request.newPassword());
    }
}
