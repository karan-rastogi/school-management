package com.school.student.web;

import com.school.student.service.StudentImportService;
import com.school.student.web.dto.StudentImportReport;
import com.school.student.web.dto.StudentImportRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/students/import")
@PreAuthorize("hasRole('ADMIN')")
public class StudentImportController {

    private final StudentImportService studentImportService;

    public StudentImportController(StudentImportService studentImportService) {
        this.studentImportService = studentImportService;
    }

    @PostMapping
    public StudentImportReport importStudents(@Valid @RequestBody StudentImportRequest request) {
        return request.dryRun()
                ? studentImportService.preview(request.csv())
                : studentImportService.commit(request.csv());
    }
}
