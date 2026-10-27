package com.school.student.web.dto;

public record StudentParentResponse(
        Long parentUserId,
        String parentName,
        String parentPhone,
        String parentEmail,
        String relationship
) {
}
