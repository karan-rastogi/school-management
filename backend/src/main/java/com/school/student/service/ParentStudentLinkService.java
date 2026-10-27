package com.school.student.service;

import com.school.common.error.ApiException;
import com.school.common.error.ErrorCode;
import com.school.student.domain.ParentStudentLink;
import com.school.student.domain.ParentStudentLinkId;
import com.school.student.domain.Student;
import com.school.student.domain.StudentEnrollment;
import com.school.student.repository.ParentStudentLinkRepository;
import com.school.student.repository.StudentEnrollmentRepository;
import com.school.student.repository.StudentRepository;
import com.school.student.web.dto.ChildResponse;
import com.school.student.web.dto.ParentStudentLinkRequest;
import com.school.student.web.dto.StudentParentResponse;
import com.school.user.domain.Role;
import com.school.user.domain.User;
import com.school.user.repository.UserRepository;
import com.school.user.repository.UserRoleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ParentStudentLinkService {

    private final ParentStudentLinkRepository parentStudentLinkRepository;
    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public ParentStudentLinkService(
            ParentStudentLinkRepository parentStudentLinkRepository,
            StudentRepository studentRepository,
            StudentEnrollmentRepository studentEnrollmentRepository,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository
    ) {
        this.parentStudentLinkRepository = parentStudentLinkRepository;
        this.studentRepository = studentRepository;
        this.studentEnrollmentRepository = studentEnrollmentRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Transactional(readOnly = true)
    public List<StudentParentResponse> listForStudent(Long studentId) {
        findStudent(studentId);
        List<ParentStudentLink> links = parentStudentLinkRepository.findByStudentId(studentId);
        Map<Long, User> parents = userRepository
                .findAllById(links.stream().map(ParentStudentLink::getParentUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        return links.stream()
                .map(link -> {
                    User parent = parents.get(link.getParentUserId());
                    return new StudentParentResponse(
                            link.getParentUserId(),
                            parent == null ? null : parent.getName(),
                            parent == null ? null : parent.getPhone(),
                            parent == null ? null : parent.getEmail(),
                            link.getRelationship());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChildResponse> listForParent(Long parentUserId) {
        requireParent(parentUserId);
        List<ParentStudentLink> links = parentStudentLinkRepository.findByParentUserIdOrderedByStudentName(parentUserId);
        if (links.isEmpty()) {
            return List.of();
        }

        List<Long> studentIds = links.stream().map(ParentStudentLink::getStudentId).toList();
        Map<Long, Student> students = studentRepository.findAllById(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, student -> student));
        Map<Long, StudentEnrollment> openEnrollments = studentEnrollmentRepository.findOpenEnrollmentsFor(studentIds).stream()
                .collect(Collectors.toMap(enrollment -> enrollment.getStudent().getId(), enrollment -> enrollment));

        return links.stream()
                .map(link -> {
                    Student student = students.get(link.getStudentId());
                    StudentEnrollment enrollment = openEnrollments.get(link.getStudentId());
                    return new ChildResponse(
                            link.getStudentId(),
                            student == null ? null : student.getFullName(),
                            student == null ? null : student.getAdmissionNo(),
                            student == null ? null : student.getStatus(),
                            link.getRelationship(),
                            enrollment == null ? null : enrollment.getSchoolClass().getId(),
                            enrollment == null ? null : enrollment.getSchoolClass().getName(),
                            enrollment == null ? null : enrollment.getSchoolClass().getSection());
                })
                .toList();
    }

    @Transactional
    public StudentParentResponse link(ParentStudentLinkRequest request) {
        User parent = requireParent(request.parentUserId());
        Student student = findStudent(request.studentId());

        if (parentStudentLinkRepository.existsByParentUserIdAndStudentId(parent.getId(), student.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE,
                    parent.getName() + " is already linked to " + student.getFullName() + ".");
        }

        ParentStudentLink link = new ParentStudentLink();
        link.setParentUserId(parent.getId());
        link.setStudentId(student.getId());
        link.setRelationship(blankToNull(request.relationship()));
        parentStudentLinkRepository.saveAndFlush(link);

        return new StudentParentResponse(parent.getId(), parent.getName(), parent.getPhone(), parent.getEmail(),
                link.getRelationship());
    }

    @Transactional
    public void unlink(Long parentUserId, Long studentId) {
        ParentStudentLinkId id = new ParentStudentLinkId(parentUserId, studentId);
        ParentStudentLink link = parentStudentLinkRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND,
                        "That parent is not linked to that student."));
        parentStudentLinkRepository.delete(link);
    }

    private User requireParent(Long parentUserId) {
        User parent = userRepository.findById(parentUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                        "The selected parent does not exist."));
        boolean isParent = userRoleRepository.findByUserId(parentUserId).stream()
                .anyMatch(userRole -> userRole.getRole() == Role.PARENT);
        if (!isParent) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "That user does not hold the PARENT role.");
        }
        return parent;
    }

    private Student findStudent(Long studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                        "The selected student does not exist."));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
