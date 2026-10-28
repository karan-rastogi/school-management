package com.school.parent.service;

import com.school.common.error.ApiException;
import com.school.common.error.ErrorCode;
import com.school.student.domain.Student;
import com.school.student.repository.ParentStudentLinkRepository;
import com.school.student.repository.StudentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guards parent-facing access: a parent may only reach a child they are linked to. Authorisation is
 * enforced here on the server, never by the UI.
 */
@Service
public class ParentAccessService {

    private final ParentStudentLinkRepository parentStudentLinkRepository;
    private final StudentRepository studentRepository;

    public ParentAccessService(
            ParentStudentLinkRepository parentStudentLinkRepository,
            StudentRepository studentRepository
    ) {
        this.parentStudentLinkRepository = parentStudentLinkRepository;
        this.studentRepository = studentRepository;
    }

    @Transactional(readOnly = true)
    public Student requireChild(Long parentUserId, Long studentId) {
        if (!parentStudentLinkRepository.existsByParentUserIdAndStudentId(parentUserId, studentId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                    "You can only view your own linked children.");
        }
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Student not found."));
    }
}
