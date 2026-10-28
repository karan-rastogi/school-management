package com.school.parent.web;

import com.school.attendance.service.AttendanceQueryService;
import com.school.attendance.service.AttendanceSummaryService;
import com.school.attendance.web.dto.AttendanceQueryRequest;
import com.school.attendance.web.dto.AttendanceQueryResponse;
import com.school.attendance.web.dto.ChildAttendanceResponse;
import com.school.auth.security.AuthenticatedUser;
import com.school.common.error.ApiException;
import com.school.common.error.ErrorCode;
import com.school.parent.service.ParentAccessService;
import com.school.student.domain.Student;
import com.school.student.service.ParentStudentLinkService;
import com.school.student.web.dto.ChildResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * The parent surface. A parent only ever sees children they are linked to; that link is checked
 * server-side on every request.
 */
@RestController
@RequestMapping("/parent")
@PreAuthorize("hasRole('PARENT')")
public class ParentController {

    private final ParentStudentLinkService parentStudentLinkService;
    private final ParentAccessService parentAccessService;
    private final AttendanceSummaryService attendanceSummaryService;
    private final AttendanceQueryService attendanceQueryService;

    public ParentController(
            ParentStudentLinkService parentStudentLinkService,
            ParentAccessService parentAccessService,
            AttendanceSummaryService attendanceSummaryService,
            AttendanceQueryService attendanceQueryService
    ) {
        this.parentStudentLinkService = parentStudentLinkService;
        this.parentAccessService = parentAccessService;
        this.attendanceSummaryService = attendanceSummaryService;
        this.attendanceQueryService = attendanceQueryService;
    }

    @GetMapping("/children")
    public List<ChildResponse> children(@AuthenticationPrincipal AuthenticatedUser user) {
        return parentStudentLinkService.listForParent(user.id());
    }

    @GetMapping("/children/{studentId}/attendance")
    public ChildAttendanceResponse attendance(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long studentId,
            @RequestParam(required = false) String month
    ) {
        Student child = parentAccessService.requireChild(user.id(), studentId);
        return attendanceSummaryService.summarise(child, parseMonth(month));
    }

    @PostMapping("/children/{studentId}/attendance-queries")
    @ResponseStatus(HttpStatus.CREATED)
    public AttendanceQueryResponse raiseQuery(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long studentId,
            @Valid @RequestBody AttendanceQueryRequest request
    ) {
        parentAccessService.requireChild(user.id(), studentId);
        return attendanceQueryService.raise(studentId, user.id(), request.date(), request.message());
    }

    private static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "The month must be in yyyy-MM format.");
        }
    }
}
