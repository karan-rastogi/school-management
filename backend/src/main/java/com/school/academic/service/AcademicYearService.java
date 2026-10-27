package com.school.academic.service;

import com.school.academic.domain.AcademicYear;
import com.school.academic.repository.AcademicYearRepository;
import com.school.academic.repository.SchoolClassRepository;
import com.school.academic.web.dto.AcademicYearRequest;
import com.school.academic.web.dto.AcademicYearResponse;
import com.school.common.error.ApiException;
import com.school.common.error.ErrorCode;
import com.school.common.web.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AcademicYearService {

    private final AcademicYearRepository academicYearRepository;
    private final SchoolClassRepository schoolClassRepository;

    public AcademicYearService(
            AcademicYearRepository academicYearRepository,
            SchoolClassRepository schoolClassRepository
    ) {
        this.academicYearRepository = academicYearRepository;
        this.schoolClassRepository = schoolClassRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AcademicYearResponse> list(String query, Pageable pageable) {
        Page<AcademicYear> page = query == null || query.isBlank()
                ? academicYearRepository.findAll(pageable)
                : academicYearRepository.findByNameContainingIgnoreCase(query.trim(), pageable);
        return PageResponse.from(page, AcademicYearService::toResponse);
    }

    @Transactional(readOnly = true)
    public List<AcademicYearResponse> options() {
        return academicYearRepository.findAll().stream()
                .sorted((left, right) -> right.getStartDate().compareTo(left.getStartDate()))
                .map(AcademicYearService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AcademicYearResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public AcademicYearResponse create(AcademicYearRequest request) {
        String name = request.name().trim();
        if (academicYearRepository.findByNameIgnoreCase(name).isPresent()) {
            throw duplicateName(name);
        }
        requireValidRange(request);

        if (request.current()) {
            clearCurrentFlag(null);
        }

        AcademicYear academicYear = new AcademicYear();
        apply(academicYear, request, name);
        return toResponse(academicYearRepository.saveAndFlush(academicYear));
    }

    @Transactional
    public AcademicYearResponse update(Long id, AcademicYearRequest request) {
        AcademicYear academicYear = find(id);
        String name = request.name().trim();
        if (academicYearRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw duplicateName(name);
        }
        requireValidRange(request);

        if (request.current()) {
            clearCurrentFlag(id);
        }

        apply(academicYear, request, name);
        return toResponse(academicYearRepository.saveAndFlush(academicYear));
    }

    @Transactional
    public void delete(Long id) {
        AcademicYear academicYear = find(id);
        if (schoolClassRepository.existsByAcademicYearId(id)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                    "This academic year still has classes. Remove those classes first.");
        }
        academicYearRepository.delete(academicYear);
    }

    private void clearCurrentFlag(Long exceptId) {
        academicYearRepository.findByCurrentIsTrue()
                .filter(existing -> !existing.getId().equals(exceptId))
                .ifPresent(existing -> {
                    existing.setCurrent(false);
                    academicYearRepository.saveAndFlush(existing);
                });
    }

    private void apply(AcademicYear academicYear, AcademicYearRequest request, String name) {
        academicYear.setName(name);
        academicYear.setStartDate(request.startDate());
        academicYear.setEndDate(request.endDate());
        academicYear.setCurrent(request.current());
    }

    private void requireValidRange(AcademicYearRequest request) {
        if (!request.endDate().isAfter(request.startDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "The end date must be after the start date.");
        }
    }

    private AcademicYear find(Long id) {
        return academicYearRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND,
                        "Academic year not found."));
    }

    private ApiException duplicateName(String name) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE,
                "An academic year named \"" + name + "\" already exists.");
    }

    static AcademicYearResponse toResponse(AcademicYear academicYear) {
        return new AcademicYearResponse(
                academicYear.getId(),
                academicYear.getName(),
                academicYear.getStartDate(),
                academicYear.getEndDate(),
                academicYear.isCurrent());
    }
}
