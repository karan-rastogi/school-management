package com.school.calendar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "school_calendar")
@Getter
@Setter
@NoArgsConstructor
public class SchoolCalendarEntry {

    @Id
    @Column(name = "date", nullable = false, updatable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private SchoolCalendarDayType type;

    @Column(name = "description", length = 500)
    private String description;
}
