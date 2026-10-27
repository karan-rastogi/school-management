package com.school.academic.repository;

import com.school.academic.domain.AcademicYear;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, Long> {

    Optional<AcademicYear> findByCurrentIsTrue();

    Optional<AcademicYear> findByNameIgnoreCase(String name);

    Page<AcademicYear> findByNameContainingIgnoreCase(String name, Pageable pageable);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
