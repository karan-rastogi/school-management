package com.school.attendance.repository;

import com.school.attendance.domain.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findByStudent_IdAndDate(Long studentId, LocalDate date);

    List<Attendance> findByDateAndStudent_IdIn(LocalDate date, Collection<Long> studentIds);

    List<Attendance> findByStudent_IdAndDateBetweenOrderByDateAsc(Long studentId, LocalDate from, LocalDate to);
}
