package com.school.student.web;

import com.school.student.service.ParentStudentLinkService;
import com.school.student.web.dto.ChildResponse;
import com.school.student.web.dto.ParentStudentLinkRequest;
import com.school.student.web.dto.StudentParentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
public class ParentStudentLinkController {

    private final ParentStudentLinkService parentStudentLinkService;

    public ParentStudentLinkController(ParentStudentLinkService parentStudentLinkService) {
        this.parentStudentLinkService = parentStudentLinkService;
    }

    @GetMapping("/students/{studentId}/parents")
    public List<StudentParentResponse> parentsOfStudent(@PathVariable Long studentId) {
        return parentStudentLinkService.listForStudent(studentId);
    }

    @GetMapping("/parents/{parentUserId}/students")
    public List<ChildResponse> childrenOfParent(@PathVariable Long parentUserId) {
        return parentStudentLinkService.listForParent(parentUserId);
    }

    @PostMapping("/parent-student-links")
    @ResponseStatus(HttpStatus.CREATED)
    public StudentParentResponse link(@Valid @RequestBody ParentStudentLinkRequest request) {
        return parentStudentLinkService.link(request);
    }

    @DeleteMapping("/parent-student-links/{parentUserId}/{studentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@PathVariable Long parentUserId, @PathVariable Long studentId) {
        parentStudentLinkService.unlink(parentUserId, studentId);
    }
}
