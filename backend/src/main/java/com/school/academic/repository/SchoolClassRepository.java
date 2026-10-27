package com.school.academic.repository;

import com.school.academic.domain.SchoolClass;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {

    @Query("""
            select c from SchoolClass c
            where (:academicYearId is null or c.academicYear.id = :academicYearId)
              and (:pattern is null
                   or lower(c.name) like :pattern
                   or lower(c.section) like :pattern)
            """)
    Page<SchoolClass> search(
            @Param("academicYearId") Long academicYearId,
            @Param("pattern") String pattern,
            Pageable pageable);

    List<SchoolClass> findByAcademicYearIdOrderByNameAscSectionAsc(Long academicYearId);

    Optional<SchoolClass> findByNameIgnoreCaseAndSectionIgnoreCaseAndAcademicYearId(
            String name, String section, Long academicYearId);

    Optional<SchoolClass> findFirstByNameIgnoreCaseAndSectionIgnoreCaseAndAcademicYearCurrentIsTrue(
            String name, String section);

    boolean existsByNameIgnoreCaseAndSectionIgnoreCaseAndAcademicYearIdAndIdNot(
            String name, String section, Long academicYearId, Long id);

    boolean existsByAcademicYearId(Long academicYearId);
}
