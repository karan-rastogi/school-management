package com.school.student.web;

import com.school.common.web.PageRequests;
import com.school.common.web.PageResponse;
import com.school.student.domain.StudentStatus;
import com.school.student.service.StudentService;
import com.school.student.web.dto.EnrollmentRequest;
import com.school.student.web.dto.EnrollmentResponse;
import com.school.student.web.dto.QrTokenResponse;
import com.school.student.web.dto.StudentDetailResponse;
import com.school.student.web.dto.StudentRequest;
import com.school.student.web.dto.StudentResponse;
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

@RestController
@RequestMapping("/admin/students")
@PreAuthorize("hasRole('ADMIN')")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping
    public PageResponse<StudentResponse> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) StudentStatus status,
            @RequestParam(required = false) Long classId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return studentService.list(query, status, classId, PageRequests.of(page, size, Sort.by("fullName")));
    }

    @GetMapping("/{id}")
    public StudentDetailResponse get(@PathVariable Long id) {
        return studentService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StudentResponse create(@Valid @RequestBody StudentRequest request) {
        return studentService.create(request);
    }

    @PutMapping("/{id}")
    public StudentResponse update(@PathVariable Long id, @Valid @RequestBody StudentRequest request) {
        return studentService.update(id, request);
    }

    @PostMapping("/{id}/qr-token")
    public QrTokenResponse regenerateQrToken(@PathVariable Long id) {
        return studentService.regenerateQrToken(id);
    }

    @PostMapping("/{id}/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    public EnrollmentResponse enroll(@PathVariable Long id, @Valid @RequestBody EnrollmentRequest request) {
        return studentService.enroll(id, request);
    }

    @DeleteMapping("/{id}/enrollments/{enrollmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeEnrollment(@PathVariable Long id, @PathVariable Long enrollmentId) {
        studentService.removeEnrollment(id, enrollmentId);
    }
}
