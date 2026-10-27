package com.school.attendance.web;

import com.school.attendance.service.AttendanceService;
import com.school.attendance.web.dto.AttendanceRosterResponse;
import com.school.attendance.web.dto.AttendanceScanRequest;
import com.school.attendance.web.dto.AttendanceScanResponse;
import com.school.attendance.web.dto.BulkAttendanceRequest;
import com.school.attendance.web.dto.MarkableClassResponse;
import com.school.auth.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @GetMapping("/attendance/classes")
    public List<MarkableClassResponse> markableClasses(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return attendanceService.markableClasses(user, date);
    }

    @GetMapping("/classes/{classId}/attendance")
    public AttendanceRosterResponse roster(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long classId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return attendanceService.roster(classId, date, user);
    }

    @PutMapping("/classes/{classId}/attendance")
    public AttendanceRosterResponse mark(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long classId,
            @Valid @RequestBody BulkAttendanceRequest request
    ) {
        return attendanceService.markBulk(classId, request, user);
    }

    @PostMapping("/attendance/scan")
    public AttendanceScanResponse scan(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AttendanceScanRequest request
    ) {
        return attendanceService.scan(request, user);
    }
}
