package com.school.teacher.repository;

import com.school.teacher.domain.TeacherAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherAssignmentRepository extends JpaRepository<TeacherAssignment, Long> {
}
