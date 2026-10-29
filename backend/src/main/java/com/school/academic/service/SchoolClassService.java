package com.school.academic.service;

import com.school.academic.domain.AcademicYear;
import com.school.academic.domain.SchoolClass;
import com.school.academic.repository.AcademicYearRepository;
import com.school.academic.repository.SchoolClassRepository;
import com.school.academic.web.dto.SchoolClassRequest;
import com.school.academic.web.dto.SchoolClassResponse;
import com.school.audit.service.AuditService;
import com.school.common.error.ApiException;
import com.school.common.error.ErrorCode;
import com.school.common.web.PageResponse;
import com.school.student.repository.StudentEnrollmentRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SchoolClassService {

    private final SchoolClassRepository schoolClassRepository;
    private final AcademicYearRepository academicYearRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final AuditService auditService;

    public SchoolClassService(
            SchoolClassRepository schoolClassRepository,
            AcademicYearRepository academicYearRepository,
            StudentEnrollmentRepository studentEnrollmentRepository,
            AuditService auditService
    ) {
        this.schoolClassRepository = schoolClassRepository;
        this.academicYearRepository = academicYearRepository;
        this.studentEnrollmentRepository = studentEnrollmentRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<SchoolClassResponse> list(Long academicYearId, String query, Pageable pageable) {
        String pattern = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase() + "%";
        return PageResponse.from(
                schoolClassRepository.search(academicYearId, pattern, pageable),
                SchoolClassService::toResponse);
    }

    @Transactional(readOnly = true)
    public List<SchoolClassResponse> options(Long academicYearId) {
        if (academicYearId != null) {
            return schoolClassRepository.findByAcademicYearIdOrderByNameAscSectionAsc(academicYearId).stream()
                    .map(SchoolClassService::toResponse)
                    .toList();
        }
        return schoolClassRepository.findAll().stream()
                .sorted((left, right) -> {
                    int byName = left.getName().compareToIgnoreCase(right.getName());
                    return byName != 0 ? byName : left.getSection().compareToIgnoreCase(right.getSection());
                })
                .map(SchoolClassService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SchoolClassResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public SchoolClassResponse create(SchoolClassRequest request) {
        AcademicYear academicYear = findAcademicYear(request.academicYearId());
        String name = request.name().trim();
        String section = request.section().trim();

        schoolClassRepository
                .findByNameIgnoreCaseAndSectionIgnoreCaseAndAcademicYearId(name, section, academicYear.getId())
                .ifPresent(existing -> {
                    throw duplicate(name, section, academicYear.getName());
                });

        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setName(name);
        schoolClass.setSection(section);
        schoolClass.setAcademicYear(academicYear);

        SchoolClass saved = schoolClassRepository.saveAndFlush(schoolClass);
        auditService.record("CLASS_CREATED", "SCHOOL_CLASS", saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public SchoolClassResponse update(Long id, SchoolClassRequest request) {
        SchoolClass schoolClass = find(id);
        AcademicYear academicYear = findAcademicYear(request.academicYearId());
        String name = request.name().trim();
        String section = request.section().trim();

        if (schoolClassRepository.existsByNameIgnoreCaseAndSectionIgnoreCaseAndAcademicYearIdAndIdNot(
                name, section, academicYear.getId(), id)) {
            throw duplicate(name, section, academicYear.getName());
        }

        schoolClass.setName(name);
        schoolClass.setSection(section);
        schoolClass.setAcademicYear(academicYear);

        SchoolClass saved = schoolClassRepository.saveAndFlush(schoolClass);
        auditService.record("CLASS_UPDATED", "SCHOOL_CLASS", saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        SchoolClass schoolClass = find(id);
        if (studentEnrollmentRepository.existsBySchoolClassId(id)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                    "Students are still enrolled in this class. Move them before deleting it.");
        }
        auditService.record("CLASS_DELETED", "SCHOOL_CLASS", id);
        schoolClassRepository.delete(schoolClass);
    }

    private AcademicYear findAcademicYear(Long id) {
        return academicYearRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                        "The selected academic year does not exist."));
    }

    private SchoolClass find(Long id) {
        return schoolClassRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND,
                        "Class not found."));
    }

    private ApiException duplicate(String name, String section, String academicYearName) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE,
                "Class " + name + " " + section + " already exists in " + academicYearName + ".");
    }

    static SchoolClassResponse toResponse(SchoolClass schoolClass) {
        return new SchoolClassResponse(
                schoolClass.getId(),
                schoolClass.getName(),
                schoolClass.getSection(),
                schoolClass.getAcademicYear().getId(),
                schoolClass.getAcademicYear().getName());
    }
}
