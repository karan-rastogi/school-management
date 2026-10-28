package com.school.attendance.web;

import com.school.attendance.domain.AttendanceQueryStatus;
import com.school.attendance.service.AttendanceQueryService;
import com.school.attendance.web.dto.AttendanceQueryResponse;
import com.school.attendance.web.dto.ResolveAttendanceQueryRequest;
import com.school.auth.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Teachers (and admins) see the attendance queries raised about students in their class and resolve
 * them. What a teacher may see is scoped to the classes they are the class teacher of.
 */
@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
public class AttendanceQueryController {

    private final AttendanceQueryService attendanceQueryService;

    public AttendanceQueryController(AttendanceQueryService attendanceQueryService) {
        this.attendanceQueryService = attendanceQueryService;
    }

    @GetMapping("/attendance/queries")
    public List<AttendanceQueryResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) AttendanceQueryStatus status
    ) {
        return attendanceQueryService.listForActor(user, status);
    }

    @PostMapping("/attendance/queries/{queryId}/resolve")
    public AttendanceQueryResponse resolve(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long queryId,
            @Valid @RequestBody ResolveAttendanceQueryRequest request
    ) {
        return attendanceQueryService.resolve(queryId, user, request.response());
    }
}
