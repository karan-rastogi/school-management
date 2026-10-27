package com.school.teacher.web.dto;

public record TeacherAssignmentResponse(
        Long id,
        Long teacherUserId,
        String teacherName,
        Long classId,
        String className,
        String section,
        String academicYearName,
        String subjectName,
        boolean classTeacher,
        boolean active
) {
}
