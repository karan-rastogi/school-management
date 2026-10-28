package com.school.attendance.repository;

import com.school.attendance.domain.AttendanceQuery;
import com.school.attendance.domain.AttendanceQueryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface AttendanceQueryRepository extends JpaRepository<AttendanceQuery, Long> {

    List<AttendanceQuery> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    List<AttendanceQuery> findByStudentIdInOrderByCreatedAtDesc(Collection<Long> studentIds);

    List<AttendanceQuery> findByStudentIdInAndStatusOrderByCreatedAtDesc(
            Collection<Long> studentIds, AttendanceQueryStatus status);

    List<AttendanceQuery> findByStatusOrderByCreatedAtDesc(AttendanceQueryStatus status);

    boolean existsByStudentIdAndAttendanceDateAndStatus(
            Long studentId, LocalDate attendanceDate, AttendanceQueryStatus status);
}
