package com.school.teacher.service;

import com.school.academic.domain.SchoolClass;
import com.school.academic.repository.SchoolClassRepository;
import com.school.audit.service.AuditService;
import com.school.common.error.ApiException;
import com.school.common.error.ErrorCode;
import com.school.common.web.PageResponse;
import com.school.teacher.domain.TeacherAssignment;
import com.school.teacher.repository.TeacherAssignmentRepository;
import com.school.teacher.web.dto.TeacherAssignmentRequest;
import com.school.teacher.web.dto.TeacherAssignmentResponse;
import com.school.user.domain.Role;
import com.school.user.domain.User;
import com.school.user.repository.UserRepository;
import com.school.user.repository.UserRoleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class TeacherAssignmentService {

    private final TeacherAssignmentRepository teacherAssignmentRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final AuditService auditService;

    public TeacherAssignmentService(
            TeacherAssignmentRepository teacherAssignmentRepository,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            SchoolClassRepository schoolClassRepository,
            AuditService auditService
    ) {
        this.teacherAssignmentRepository = teacherAssignmentRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<TeacherAssignmentResponse> list(Long classId, Long teacherUserId, Pageable pageable) {
        List<Specification<TeacherAssignment>> specifications = new ArrayList<>();
        if (classId != null) {
            specifications.add((root, query, builder) -> builder.equal(root.get("schoolClass").get("id"), classId));
        }
        if (teacherUserId != null) {
            specifications.add((root, query, builder) -> builder.equal(root.get("teacher").get("id"), teacherUserId));
        }
        Specification<TeacherAssignment> specification =
                specifications.isEmpty() ? null : Specification.allOf(specifications);

        Page<TeacherAssignment> page = teacherAssignmentRepository.findAll(specification, pageable);
        return PageResponse.from(page, TeacherAssignmentService::toResponse);
    }

    @Transactional(readOnly = true)
    public TeacherAssignmentResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public TeacherAssignmentResponse create(TeacherAssignmentRequest request) {
        User teacher = findTeacher(request.teacherUserId());
        SchoolClass schoolClass = findClass(request.classId());
        String subject = request.subjectName().trim();

        if (teacherAssignmentRepository.existsByTeacherIdAndSchoolClassIdAndSubjectNameIgnoreCase(
                teacher.getId(), schoolClass.getId(), subject)) {
            throw duplicateSubject(teacher, schoolClass, subject);
        }

        if (request.classTeacher()) {
            requireNoActiveClassTeacher(schoolClass, null);
        }

        TeacherAssignment assignment = new TeacherAssignment();
        assignment.setTeacher(teacher);
        assignment.setSchoolClass(schoolClass);
        assignment.setSubjectName(subject);
        assignment.setClassTeacher(request.classTeacher());
        assignment.setActive(request.active() == null || request.active());

        TeacherAssignment saved = teacherAssignmentRepository.saveAndFlush(assignment);
        auditService.record("TEACHER_ASSIGNMENT_CREATED", "TEACHER_ASSIGNMENT", saved.getId(),
                "classId=" + schoolClass.getId() + " teacherUserId=" + teacher.getId());
        return toResponse(saved);
    }

    @Transactional
    public TeacherAssignmentResponse update(Long id, TeacherAssignmentRequest request) {
        TeacherAssignment assignment = find(id);
        User teacher = findTeacher(request.teacherUserId());
        SchoolClass schoolClass = findClass(request.classId());
        String subject = request.subjectName().trim();

        if (teacherAssignmentRepository.existsByTeacherIdAndSchoolClassIdAndSubjectNameIgnoreCaseAndIdNot(
                teacher.getId(), schoolClass.getId(), subject, id)) {
            throw duplicateSubject(teacher, schoolClass, subject);
        }

        if (request.classTeacher()) {
            requireNoActiveClassTeacher(schoolClass, id);
        }

        assignment.setTeacher(teacher);
        assignment.setSchoolClass(schoolClass);
        assignment.setSubjectName(subject);
        assignment.setClassTeacher(request.classTeacher());
        if (request.active() != null) {
            assignment.setActive(request.active());
        }

        TeacherAssignment saved = teacherAssignmentRepository.saveAndFlush(assignment);
        auditService.record("TEACHER_ASSIGNMENT_UPDATED", "TEACHER_ASSIGNMENT", saved.getId(),
                "classId=" + schoolClass.getId() + " teacherUserId=" + teacher.getId());
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        find(id);
        auditService.record("TEACHER_ASSIGNMENT_DELETED", "TEACHER_ASSIGNMENT", id);
        teacherAssignmentRepository.deleteById(id);
    }

    private void requireNoActiveClassTeacher(SchoolClass schoolClass, Long exceptId) {
        boolean taken = exceptId == null
                ? teacherAssignmentRepository.existsBySchoolClassIdAndClassTeacherIsTrueAndActiveIsTrue(schoolClass.getId())
                : teacherAssignmentRepository
                        .existsBySchoolClassIdAndClassTeacherIsTrueAndActiveIsTrueAndIdNot(schoolClass.getId(), exceptId);

        if (taken) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                    "Class " + schoolClass.getName() + " " + schoolClass.getSection()
                            + " already has an active class teacher.");
        }
    }

    private User findTeacher(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                        "The selected teacher does not exist."));
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "That teacher's account is deactivated.");
        }
        boolean teaches = userRoleRepository.findByUserId(id).stream()
                .anyMatch(userRole -> userRole.getRole() == Role.TEACHER);
        if (!teaches) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "That user does not hold the TEACHER role. Add the role first.");
        }
        return user;
    }

    private SchoolClass findClass(Long id) {
        return schoolClassRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                        "The selected class does not exist."));
    }

    private TeacherAssignment find(Long id) {
        return teacherAssignmentRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND,
                        "Teacher assignment not found."));
    }

    private ApiException duplicateSubject(User teacher, SchoolClass schoolClass, String subject) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE,
                teacher.getName() + " already teaches " + subject + " for "
                        + schoolClass.getName() + " " + schoolClass.getSection() + ".");
    }

    private static TeacherAssignmentResponse toResponse(TeacherAssignment assignment) {
        SchoolClass schoolClass = assignment.getSchoolClass();
        return new TeacherAssignmentResponse(
                assignment.getId(),
                assignment.getTeacher().getId(),
                assignment.getTeacher().getName(),
                schoolClass.getId(),
                schoolClass.getName(),
                schoolClass.getSection(),
                schoolClass.getAcademicYear().getName(),
                assignment.getSubjectName(),
                assignment.isClassTeacher(),
                assignment.isActive());
    }
}
