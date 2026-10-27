package com.school.teacher.repository;

import com.school.teacher.domain.TeacherAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TeacherAssignmentRepository
        extends JpaRepository<TeacherAssignment, Long>, JpaSpecificationExecutor<TeacherAssignment> {

    boolean existsByTeacherIdAndSchoolClassIdAndSubjectNameIgnoreCase(
            Long teacherId, Long schoolClassId, String subjectName);

    boolean existsByTeacherIdAndSchoolClassIdAndSubjectNameIgnoreCaseAndIdNot(
            Long teacherId, Long schoolClassId, String subjectName, Long id);

    boolean existsBySchoolClassIdAndClassTeacherIsTrueAndActiveIsTrueAndIdNot(Long schoolClassId, Long id);

    boolean existsBySchoolClassIdAndClassTeacherIsTrueAndActiveIsTrue(Long schoolClassId);
}
