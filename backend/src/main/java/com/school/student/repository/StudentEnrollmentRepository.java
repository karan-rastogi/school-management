package com.school.student.repository;

import com.school.student.domain.StudentEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentEnrollmentRepository extends JpaRepository<StudentEnrollment, Long> {
}
