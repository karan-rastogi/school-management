package com.school.notice.service;

import com.school.teacher.domain.TeacherAssignment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoticeServiceTest {

    @Test
    void senderLabelIsBuiltFromTheAssignmentSubjectAndTeacherName() {
        TeacherAssignment assignment = new TeacherAssignment();
        assignment.setSubjectName("Maths");

        assertThat(NoticeService.teacherLabel(assignment, "Mrs Sharma"))
                .isEqualTo("Maths Teacher - Mrs Sharma");
    }

    @Test
    void aVeryLongLabelIsTruncatedToTheColumnLength() {
        TeacherAssignment assignment = new TeacherAssignment();
        assignment.setSubjectName("Integrated Environmental Sciences ".repeat(5));

        assertThat(NoticeService.teacherLabel(assignment, "Mrs Sharma")).hasSize(100);
    }
}
