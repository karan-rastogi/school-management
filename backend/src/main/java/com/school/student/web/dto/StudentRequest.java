package com.school.student.web.dto;

import com.school.student.domain.StudentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record StudentRequest(
        @NotBlank @Size(max = 255) String fullName,
        @NotBlank @Size(max = 50) String admissionNo,
        @NotNull @Past LocalDate dob,
        @Size(max = 512) String photoPath,
        @NotNull @PastOrPresent LocalDate admissionDate,
        StudentStatus status
) {
}
