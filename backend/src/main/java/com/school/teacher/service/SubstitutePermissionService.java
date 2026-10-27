package com.school.teacher.service;

import com.school.academic.domain.SchoolClass;
import com.school.academic.repository.SchoolClassRepository;
import com.school.auth.security.AuthenticatedUser;
import com.school.common.error.ApiException;
import com.school.common.error.ErrorCode;
import com.school.teacher.domain.SubstitutePermission;
import com.school.teacher.repository.SubstitutePermissionRepository;
import com.school.teacher.web.dto.SubstitutePermissionRequest;
import com.school.teacher.web.dto.SubstitutePermissionResponse;
import com.school.user.domain.Role;
import com.school.user.domain.User;
import com.school.user.repository.UserRepository;
import com.school.user.repository.UserRoleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SubstitutePermissionService {

    private final SubstitutePermissionRepository substitutePermissionRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final SchoolClassRepository schoolClassRepository;

    public SubstitutePermissionService(
            SubstitutePermissionRepository substitutePermissionRepository,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            SchoolClassRepository schoolClassRepository
    ) {
        this.substitutePermissionRepository = substitutePermissionRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.schoolClassRepository = schoolClassRepository;
    }

    @Transactional
    public SubstitutePermissionResponse create(
            Long classId,
            SubstitutePermissionRequest request,
            AuthenticatedUser actor
    ) {
        SchoolClass schoolClass = findClass(classId);
        User teacher = requireTeacher(request.teacherUserId());

        if (request.validUntil().isBefore(request.validFrom())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "The permission's end date cannot be before its start date.");
        }

        SubstitutePermission permission = new SubstitutePermission();
        permission.setTeacher(teacher);
        permission.setSchoolClass(schoolClass);
        permission.setGrantedBy(userRepository.getReferenceById(actor.id()));
        permission.setValidFrom(request.validFrom());
        permission.setValidUntil(request.validUntil());
        permission.setReason(blankToNull(request.reason()));

        return toResponse(substitutePermissionRepository.saveAndFlush(permission));
    }

    @Transactional(readOnly = true)
    public List<SubstitutePermissionResponse> list(Long classId) {
        findClass(classId);
        return substitutePermissionRepository.findByClass(classId).stream()
                .map(SubstitutePermissionService::toResponse)
                .toList();
    }

    @Transactional
    public void delete(Long id) {
        SubstitutePermission permission = substitutePermissionRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND,
                        "Substitute permission not found."));
        substitutePermissionRepository.delete(permission);
    }

    private User requireTeacher(Long teacherUserId) {
        User teacher = userRepository.findById(teacherUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                        "The selected teacher does not exist."));
        boolean teaches = userRoleRepository.findByUserId(teacherUserId).stream()
                .anyMatch(userRole -> userRole.getRole() == Role.TEACHER);
        if (!teaches) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "That user does not hold the TEACHER role.");
        }
        return teacher;
    }

    private SchoolClass findClass(Long id) {
        return schoolClassRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Class not found."));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static SubstitutePermissionResponse toResponse(SubstitutePermission permission) {
        SchoolClass schoolClass = permission.getSchoolClass();
        return new SubstitutePermissionResponse(
                permission.getId(),
                permission.getTeacher().getId(),
                permission.getTeacher().getName(),
                schoolClass.getId(),
                schoolClass.getName(),
                schoolClass.getSection(),
                schoolClass.getAcademicYear().getName(),
                permission.getValidFrom(),
                permission.getValidUntil(),
                permission.getReason(),
                permission.getGrantedAt());
    }
}
