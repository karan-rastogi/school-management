package com.school.teacher.web;

import com.school.auth.security.AuthenticatedUser;
import com.school.teacher.service.SubstitutePermissionService;
import com.school.teacher.web.dto.SubstitutePermissionRequest;
import com.school.teacher.web.dto.SubstitutePermissionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class SubstitutePermissionController {

    private final SubstitutePermissionService substitutePermissionService;

    public SubstitutePermissionController(SubstitutePermissionService substitutePermissionService) {
        this.substitutePermissionService = substitutePermissionService;
    }

    @GetMapping("/classes/{classId}/substitute-permissions")
    public List<SubstitutePermissionResponse> list(@PathVariable Long classId) {
        return substitutePermissionService.list(classId);
    }

    @PostMapping("/classes/{classId}/substitute-permissions")
    @ResponseStatus(HttpStatus.CREATED)
    public SubstitutePermissionResponse create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long classId,
            @Valid @RequestBody SubstitutePermissionRequest request
    ) {
        return substitutePermissionService.create(classId, request, user);
    }

    @DeleteMapping("/substitute-permissions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        substitutePermissionService.delete(id);
    }
}
