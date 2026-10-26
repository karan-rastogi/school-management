package com.school.auth.web;

import com.school.auth.security.AuthenticatedUser;
import com.school.auth.service.AuthService;
import com.school.auth.web.dto.ActiveRoleRequest;
import com.school.auth.web.dto.CurrentUserResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    private final AuthService authService;

    public MeController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return CurrentUserResponse.from(user);
    }

    @PostMapping("/me/active-role")
    public CurrentUserResponse switchActiveRole(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ActiveRoleRequest body,
            HttpServletResponse response
    ) {
        return CurrentUserResponse.from(authService.switchActiveRole(user, body.role(), response));
    }
}
