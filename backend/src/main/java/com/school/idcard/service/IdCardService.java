package com.school.idcard.service;

import com.school.academic.domain.SchoolClass;
import com.school.academic.repository.SchoolClassRepository;
import com.school.common.error.ApiException;
import com.school.common.error.ErrorCode;
import com.school.student.domain.Student;
import com.school.student.domain.StudentEnrollment;
import com.school.student.repository.StudentEnrollmentRepository;
import com.school.student.repository.StudentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Builds printable ID card PDFs for a single student or a whole class. The class sheet uses the
 * same roster query as attendance so it lists exactly the students enrolled on the current date,
 * ordered by roll number.
 */
@Service
public class IdCardService {

    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final StudentPhotoResolver photoResolver;
    private final IdCardPdfRenderer renderer;
    private final Clock clock;

    public IdCardService(
            StudentRepository studentRepository,
            StudentEnrollmentRepository studentEnrollmentRepository,
            SchoolClassRepository schoolClassRepository,
            StudentPhotoResolver photoResolver,
            IdCardPdfRenderer renderer,
            Clock clock
    ) {
        this.studentRepository = studentRepository;
        this.studentEnrollmentRepository = studentEnrollmentRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.photoResolver = photoResolver;
        this.renderer = renderer;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public GeneratedPdf renderStudentCard(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Student not found."));

        StudentEnrollment enrollment = studentEnrollmentRepository
                .findOpenEnrollmentsFor(List.of(studentId)).stream()
                .findFirst()
                .orElse(null);

        IdCardData card = toCard(student, enrollment);
        String fileName = safeName(student.getAdmissionNo(), "student") + "-id-card.pdf";
        return new GeneratedPdf(fileName, renderer.render(List.of(card)));
    }

    @Transactional(readOnly = true)
    public GeneratedPdf renderClassSheet(Long classId) {
        SchoolClass schoolClass = schoolClassRepository.findById(classId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Class not found."));

        List<StudentEnrollment> roster = studentEnrollmentRepository.findRosterFor(classId, LocalDate.now(clock));
        if (roster.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND,
                    "No students are currently enrolled in this class.");
        }

        List<IdCardData> cards = roster.stream()
                .sorted(Comparator
                        .comparing(StudentEnrollment::getRollNo, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(enrollment -> enrollment.getStudent().getFullName(), String.CASE_INSENSITIVE_ORDER))
                .map(enrollment -> toCard(enrollment.getStudent(), enrollment, schoolClass))
                .toList();

        String fileName = safeName(schoolClass.getName() + "-" + schoolClass.getSection(), "class") + "-id-cards.pdf";
        return new GeneratedPdf(fileName, renderer.render(cards));
    }

    private IdCardData toCard(Student student, StudentEnrollment enrollment) {
        SchoolClass schoolClass = enrollment == null ? null : enrollment.getSchoolClass();
        return toCard(student, enrollment, schoolClass);
    }

    private IdCardData toCard(Student student, StudentEnrollment enrollment, SchoolClass schoolClass) {
        return new IdCardData(
                student.getFullName(),
                schoolClass == null ? null : schoolClass.getName(),
                schoolClass == null ? null : schoolClass.getSection(),
                enrollment == null ? null : enrollment.getRollNo(),
                student.getQrToken() == null ? null : student.getQrToken().toString(),
                photoResolver.resolve(student.getPhotoPath()).orElse(null));
    }

    private static String safeName(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String cleaned = value.trim().replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        return cleaned.isEmpty() ? fallback : cleaned;
    }
}
