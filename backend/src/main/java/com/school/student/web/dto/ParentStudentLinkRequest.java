package com.school.student.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ParentStudentLinkRequest(
        @NotNull Long parentUserId,
        @NotNull Long studentId,
        @Size(max = 50) String relationship
) {
}
