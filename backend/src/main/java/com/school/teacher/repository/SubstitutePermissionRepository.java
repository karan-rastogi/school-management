package com.school.teacher.repository;

import com.school.teacher.domain.SubstitutePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SubstitutePermissionRepository extends JpaRepository<SubstitutePermission, Long> {

    @Query("""
            select count(p) > 0 from SubstitutePermission p
            where p.teacher.id = :teacherUserId
              and p.schoolClass.id = :classId
              and p.validFrom <= :date
              and p.validUntil >= :date
            """)
    boolean existsActive(
            @Param("teacherUserId") Long teacherUserId,
            @Param("classId") Long classId,
            @Param("date") LocalDate date);

    @Query("""
            select p from SubstitutePermission p
            join fetch p.schoolClass c
            join fetch c.academicYear
            join fetch p.teacher
            where p.schoolClass.id = :classId
            order by p.validFrom desc, p.id desc
            """)
    List<SubstitutePermission> findByClass(@Param("classId") Long classId);

    @Query("""
            select p from SubstitutePermission p
            join fetch p.schoolClass c
            join fetch c.academicYear
            where p.teacher.id = :teacherUserId
              and p.validFrom <= :date
              and p.validUntil >= :date
            """)
    List<SubstitutePermission> findActiveForTeacher(
            @Param("teacherUserId") Long teacherUserId,
            @Param("date") LocalDate date);
}
