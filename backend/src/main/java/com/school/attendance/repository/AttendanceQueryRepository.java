package com.school.attendance.repository;

import com.school.attendance.domain.AttendanceQuery;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceQueryRepository extends JpaRepository<AttendanceQuery, Long> {
}
