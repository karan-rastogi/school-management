package com.school.calendar.repository;

import com.school.calendar.domain.SchoolCalendarEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SchoolCalendarRepository extends JpaRepository<SchoolCalendarEntry, LocalDate> {

    List<SchoolCalendarEntry> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);
}
