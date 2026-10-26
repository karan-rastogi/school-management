package com.school.student.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "students")
@Getter
@Setter
@NoArgsConstructor
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "admission_no", nullable = false, length = 50, unique = true)
    private String admissionNo;

    @Column(name = "dob", nullable = false)
    private LocalDate dob;

    @Column(name = "photo_path", length = 512)
    private String photoPath;

    @Generated(event = EventType.INSERT)
    @Column(name = "qr_token", nullable = false, unique = true, updatable = false)
    private UUID qrToken;

    @Column(name = "admission_date", nullable = false)
    private LocalDate admissionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StudentStatus status = StudentStatus.ACTIVE;
}
