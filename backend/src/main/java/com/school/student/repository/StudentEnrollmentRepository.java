package com.school.student.repository;

import com.school.student.domain.StudentEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface StudentEnrollmentRepository extends JpaRepository<StudentEnrollment, Long> {

    List<StudentEnrollment> findByStudentIdOrderByFromDateDesc(Long studentId);

    boolean existsBySchoolClassId(Long schoolClassId);

    boolean existsByStudentIdAndFromDate(Long studentId, LocalDate fromDate);

    @Query("""
            select e from StudentEnrollment e
            join fetch e.schoolClass c
            join fetch c.academicYear
            where e.student.id in :studentIds and e.toDate is null
            """)
    List<StudentEnrollment> findOpenEnrollmentsFor(@Param("studentIds") Collection<Long> studentIds);

    @Query("""
            select count(e) > 0 from StudentEnrollment e
            where e.student.id = :studentId
              and e.fromDate <= :onDate
              and (e.toDate is null or e.toDate >= :onDate)
            """)
    boolean existsActiveEnrollment(@Param("studentId") Long studentId, @Param("onDate") LocalDate onDate);
}
