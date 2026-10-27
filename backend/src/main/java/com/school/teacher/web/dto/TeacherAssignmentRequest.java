package com.school.teacher.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TeacherAssignmentRequest(
        @NotNull Long teacherUserId,
        @NotNull Long classId,
        @NotBlank @Size(max = 100) String subjectName,
        boolean classTeacher,
        Boolean active
) {
}
