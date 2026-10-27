package com.school.teacher.web;

import com.school.common.web.PageRequests;
import com.school.common.web.PageResponse;
import com.school.teacher.service.TeacherAssignmentService;
import com.school.teacher.web.dto.TeacherAssignmentRequest;
import com.school.teacher.web.dto.TeacherAssignmentResponse;
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
@RequestMapping("/admin/teacher-assignments")
@PreAuthorize("hasRole('ADMIN')")
public class TeacherAssignmentController {

    private final TeacherAssignmentService teacherAssignmentService;

    public TeacherAssignmentController(TeacherAssignmentService teacherAssignmentService) {
        this.teacherAssignmentService = teacherAssignmentService;
    }

    @GetMapping
    public PageResponse<TeacherAssignmentResponse> list(
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long teacherUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return teacherAssignmentService.list(classId, teacherUserId, PageRequests.of(page, size, Sort.by("id")));
    }

    @GetMapping("/{id}")
    public TeacherAssignmentResponse get(@PathVariable Long id) {
        return teacherAssignmentService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeacherAssignmentResponse create(@Valid @RequestBody TeacherAssignmentRequest request) {
        return teacherAssignmentService.create(request);
    }

    @PutMapping("/{id}")
    public TeacherAssignmentResponse update(
            @PathVariable Long id,
            @Valid @RequestBody TeacherAssignmentRequest request
    ) {
        return teacherAssignmentService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        teacherAssignmentService.delete(id);
    }
}
