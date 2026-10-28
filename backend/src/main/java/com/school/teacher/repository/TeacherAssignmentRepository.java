package com.school.teacher.repository;

import com.school.teacher.domain.TeacherAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TeacherAssignmentRepository
        extends JpaRepository<TeacherAssignment, Long>, JpaSpecificationExecutor<TeacherAssignment> {

    boolean existsByTeacherIdAndSchoolClassIdAndSubjectNameIgnoreCase(
            Long teacherId, Long schoolClassId, String subjectName);

    boolean existsByTeacherIdAndSchoolClassIdAndSubjectNameIgnoreCaseAndIdNot(
            Long teacherId, Long schoolClassId, String subjectName, Long id);

    boolean existsBySchoolClassIdAndClassTeacherIsTrueAndActiveIsTrueAndIdNot(Long schoolClassId, Long id);

    boolean existsBySchoolClassIdAndClassTeacherIsTrueAndActiveIsTrue(Long schoolClassId);

    @Query("""
            select count(a) > 0 from TeacherAssignment a
            where a.teacher.id = :teacherUserId
              and a.schoolClass.id = :classId
              and a.classTeacher = true
              and a.active = true
            """)
    boolean existsActiveClassTeacher(@Param("teacherUserId") Long teacherUserId, @Param("classId") Long classId);

    @Query("""
            select a from TeacherAssignment a
            join fetch a.schoolClass c
            join fetch c.academicYear
            where a.teacher.id = :teacherUserId
              and a.classTeacher = true
              and a.active = true
            """)
    List<TeacherAssignment> findActiveClassTeacherAssignments(@Param("teacherUserId") Long teacherUserId);

    @Query("""
            select a from TeacherAssignment a
            join fetch a.schoolClass c
            join fetch c.academicYear
            where a.teacher.id = :teacherUserId
              and a.active = true
            order by c.name, c.section, a.id
            """)
    List<TeacherAssignment> findActiveAssignmentsForTeacher(@Param("teacherUserId") Long teacherUserId);

    @Query("""
            select a from TeacherAssignment a
            where a.teacher.id = :teacherUserId
              and a.schoolClass.id = :classId
              and a.active = true
            order by a.classTeacher desc, a.id asc
            """)
    List<TeacherAssignment> findActiveAssignmentsForClass(
            @Param("teacherUserId") Long teacherUserId,
            @Param("classId") Long classId);
}
