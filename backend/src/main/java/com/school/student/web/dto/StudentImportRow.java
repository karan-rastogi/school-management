package com.school.student.web.dto;

import java.util.List;

public record StudentImportRow(
        int rowNumber,
        StudentImportOutcome outcome,
        String admissionNo,
        String studentName,
        String parentName,
        String parentPhone,
        List<String> messages
) {
}
