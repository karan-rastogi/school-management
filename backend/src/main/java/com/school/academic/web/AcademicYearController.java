package com.school.academic.web;

import com.school.academic.service.AcademicYearService;
import com.school.academic.web.dto.AcademicYearRequest;
import com.school.academic.web.dto.AcademicYearResponse;
import com.school.common.web.PageRequests;
import com.school.common.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/academic-years")
@PreAuthorize("hasRole('ADMIN')")
public class AcademicYearController {

    private final AcademicYearService academicYearService;

    public AcademicYearController(AcademicYearService academicYearService) {
        this.academicYearService = academicYearService;
    }

    @GetMapping
    public PageResponse<AcademicYearResponse> list(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return academicYearService.list(query, PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "startDate")));
    }

    @GetMapping("/options")
    public List<AcademicYearResponse> options() {
        return academicYearService.options();
    }

    @GetMapping("/{id}")
    public AcademicYearResponse get(@PathVariable Long id) {
        return academicYearService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AcademicYearResponse create(@Valid @RequestBody AcademicYearRequest request) {
        return academicYearService.create(request);
    }

    @PutMapping("/{id}")
    public AcademicYearResponse update(@PathVariable Long id, @Valid @RequestBody AcademicYearRequest request) {
        return academicYearService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        academicYearService.delete(id);
    }
}
