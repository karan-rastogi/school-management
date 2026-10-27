package com.school.student.web.dto;

import java.util.List;

public record StudentImportReport(
        boolean dryRun,
        boolean committed,
        int totalRows,
        int studentsToCreate,
        int parentsToCreate,
        int linksToCreate,
        int duplicateLinks,
        int errorCount,
        boolean canCommit,
        List<StudentImportRow> rows
) {
}
