package com.school.attendance.repository;

import com.school.attendance.domain.AttendanceAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceAuditLogRepository extends JpaRepository<AttendanceAuditLog, Long> {
}
