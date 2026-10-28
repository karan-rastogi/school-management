package com.school.notice.web.dto;

/** A class the actor may post a CLASS notice to, with the sender label that would be stored. */
public record AssignableClassResponse(
        Long classId,
        String className,
        String section,
        String academicYearName,
        String senderLabel
) {
}
