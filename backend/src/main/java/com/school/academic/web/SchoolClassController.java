package com.school.academic.web;

import com.school.academic.service.SchoolClassService;
import com.school.academic.web.dto.SchoolClassRequest;
import com.school.academic.web.dto.SchoolClassResponse;
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
@RequestMapping("/admin/classes")
@PreAuthorize("hasRole('ADMIN')")
public class SchoolClassController {

    private final SchoolClassService schoolClassService;

    public SchoolClassController(SchoolClassService schoolClassService) {
        this.schoolClassService = schoolClassService;
    }

    @GetMapping
    public PageResponse<SchoolClassResponse> list(
            @RequestParam(required = false) Long academicYearId,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Sort sort = Sort.by(Sort.Order.asc("name"), Sort.Order.asc("section"));
        return schoolClassService.list(academicYearId, query, PageRequests.of(page, size, sort));
    }

    @GetMapping("/options")
    public List<SchoolClassResponse> options(@RequestParam(required = false) Long academicYearId) {
        return schoolClassService.options(academicYearId);
    }

    @GetMapping("/{id}")
    public SchoolClassResponse get(@PathVariable Long id) {
        return schoolClassService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SchoolClassResponse create(@Valid @RequestBody SchoolClassRequest request) {
        return schoolClassService.create(request);
    }

    @PutMapping("/{id}")
    public SchoolClassResponse update(@PathVariable Long id, @Valid @RequestBody SchoolClassRequest request) {
        return schoolClassService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        schoolClassService.delete(id);
    }
}
