package com.school.student.service;

import com.school.academic.domain.SchoolClass;
import com.school.academic.repository.SchoolClassRepository;
import com.school.common.error.ApiException;
import com.school.common.error.ErrorCode;
import com.school.common.web.PageResponse;
import com.school.student.domain.ParentStudentLink;
import com.school.student.domain.Student;
import com.school.student.domain.StudentEnrollment;
import com.school.student.domain.StudentStatus;
import com.school.student.repository.ParentStudentLinkRepository;
import com.school.student.repository.StudentEnrollmentRepository;
import com.school.student.repository.StudentRepository;
import com.school.student.web.dto.EnrollmentRequest;
import com.school.student.web.dto.EnrollmentResponse;
import com.school.student.web.dto.QrTokenResponse;
import com.school.student.web.dto.StudentDetailResponse;
import com.school.student.web.dto.StudentParentResponse;
import com.school.student.web.dto.StudentRequest;
import com.school.student.web.dto.StudentResponse;
import com.school.user.domain.User;
import com.school.user.repository.UserRepository;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StudentService {

    private static final Logger log = LoggerFactory.getLogger(StudentService.class);

    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final ParentStudentLinkRepository parentStudentLinkRepository;
    private final UserRepository userRepository;
    private final SchoolClassRepository schoolClassRepository;

    public StudentService(
            StudentRepository studentRepository,
            StudentEnrollmentRepository studentEnrollmentRepository,
            ParentStudentLinkRepository parentStudentLinkRepository,
            UserRepository userRepository,
            SchoolClassRepository schoolClassRepository
    ) {
        this.studentRepository = studentRepository;
        this.studentEnrollmentRepository = studentEnrollmentRepository;
        this.parentStudentLinkRepository = parentStudentLinkRepository;
        this.userRepository = userRepository;
        this.schoolClassRepository = schoolClassRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentResponse> list(
            String query,
            StudentStatus status,
            Long classId,
            Pageable pageable
    ) {
        Page<Student> page = studentRepository.findAll(search(query, status, classId), pageable);
        Map<Long, StudentEnrollment> openEnrollments = openEnrollments(page.getContent());
        return PageResponse.from(page, student -> toResponse(student, openEnrollments.get(student.getId())));
    }

    @Transactional(readOnly = true)
    public StudentDetailResponse get(Long id) {
        return toDetail(find(id));
    }

    @Transactional
    public StudentResponse create(StudentRequest request) {
        String admissionNo = request.admissionNo().trim();
        if (studentRepository.existsByAdmissionNoIgnoreCase(admissionNo)) {
            throw duplicateAdmissionNo(admissionNo);
        }
        requireDatesConsistent(request.dob(), request.admissionDate());

        Student student = new Student();
        apply(student, request, admissionNo);

        Student saved = studentRepository.saveAndFlush(student);
        return toResponse(saved, null);
    }

    @Transactional
    public StudentResponse update(Long id, StudentRequest request) {
        Student student = find(id);
        String admissionNo = request.admissionNo().trim();
        if (studentRepository.existsByAdmissionNoIgnoreCaseAndIdNot(admissionNo, id)) {
            throw duplicateAdmissionNo(admissionNo);
        }
        requireDatesConsistent(request.dob(), request.admissionDate());

        apply(student, request, admissionNo);

        Student saved = studentRepository.saveAndFlush(student);
        return toResponse(saved, openEnrollments(List.of(saved)).get(saved.getId()));
    }

    @Transactional
    public QrTokenResponse regenerateQrToken(Long id) {
        find(id);
        studentRepository.regenerateQrToken(id);
        Student refreshed = find(id);
        log.info("Regenerated the QR token for student id {}", id);
        return new QrTokenResponse(refreshed.getQrToken());
    }

    @Transactional
    public EnrollmentResponse enroll(Long studentId, EnrollmentRequest request) {
        Student student = find(studentId);
        SchoolClass schoolClass = schoolClassRepository.findById(request.classId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                        "The selected class does not exist."));

        LocalDate from = request.fromDate();
        LocalDate to = request.toDate();
        if (to != null && to.isBefore(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "The end date cannot be before the start date.");
        }
        if (studentEnrollmentRepository.existsByStudentIdAndFromDate(studentId, from)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE,
                    "This student already has an enrollment starting on " + from + ".");
        }

        for (StudentEnrollment open : studentEnrollmentRepository.findByStudentIdOrderByFromDateDesc(studentId)) {
            if (open.getToDate() != null) {
                continue;
            }
            if (!open.getFromDate().isBefore(from)) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                        "Close the enrollment that starts on " + open.getFromDate() + " first.");
            }
            open.setToDate(from.minusDays(1));
            studentEnrollmentRepository.saveAndFlush(open);
        }

        StudentEnrollment enrollment = new StudentEnrollment();
        enrollment.setStudent(student);
        enrollment.setSchoolClass(schoolClass);
        enrollment.setRollNo(request.rollNo());
        enrollment.setFromDate(from);
        enrollment.setToDate(to);

        return toEnrollment(studentEnrollmentRepository.saveAndFlush(enrollment));
    }

    @Transactional
    public void removeEnrollment(Long studentId, Long enrollmentId) {
        StudentEnrollment enrollment = studentEnrollmentRepository.findById(enrollmentId)
                .filter(candidate -> candidate.getStudent().getId().equals(studentId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND,
                        "Enrollment not found for this student."));
        studentEnrollmentRepository.delete(enrollment);
    }

    private void apply(Student student, StudentRequest request, String admissionNo) {
        student.setFullName(request.fullName().trim());
        student.setAdmissionNo(admissionNo);
        student.setDob(request.dob());
        student.setPhotoPath(blankToNull(request.photoPath()));
        student.setAdmissionDate(request.admissionDate());
        student.setStatus(request.status() == null ? StudentStatus.ACTIVE : request.status());
    }

    private void requireDatesConsistent(LocalDate dob, LocalDate admissionDate) {
        if (dob.isAfter(admissionDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "The date of birth cannot be after the admission date.");
        }
    }

    private Map<Long, StudentEnrollment> openEnrollments(Collection<Student> students) {
        if (students.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = students.stream().map(Student::getId).toList();
        Map<Long, StudentEnrollment> byStudent = new LinkedHashMap<>();
        for (StudentEnrollment enrollment : studentEnrollmentRepository.findOpenEnrollmentsFor(ids)) {
            byStudent.put(enrollment.getStudent().getId(), enrollment);
        }
        return byStudent;
    }

    private StudentDetailResponse toDetail(Student student) {
        List<EnrollmentResponse> enrollments = studentEnrollmentRepository
                .findByStudentIdOrderByFromDateDesc(student.getId()).stream()
                .map(StudentService::toEnrollment)
                .toList();

        List<ParentStudentLink> links = parentStudentLinkRepository.findByStudentId(student.getId());
        Map<Long, User> parentsById = userRepository
                .findAllById(links.stream().map(ParentStudentLink::getParentUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<StudentParentResponse> parents = links.stream()
                .map(link -> {
                    User parent = parentsById.get(link.getParentUserId());
                    return new StudentParentResponse(
                            link.getParentUserId(),
                            parent == null ? null : parent.getName(),
                            parent == null ? null : parent.getPhone(),
                            parent == null ? null : parent.getEmail(),
                            link.getRelationship());
                })
                .toList();

        return new StudentDetailResponse(
                student.getId(),
                student.getFullName(),
                student.getAdmissionNo(),
                student.getDob(),
                student.getPhotoPath(),
                student.getQrToken() == null ? null : student.getQrToken().toString(),
                student.getAdmissionDate(),
                student.getStatus(),
                enrollments,
                parents);
    }

    private Student find(Long id) {
        return studentRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Student not found."));
    }

    private ApiException duplicateAdmissionNo(String admissionNo) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE,
                "Admission number " + admissionNo + " is already taken.");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Specification<Student> search(String query, StudentStatus status, Long classId) {
        List<Specification<Student>> specifications = new ArrayList<>();

        if (query != null && !query.isBlank()) {
            String pattern = "%" + query.trim().toLowerCase() + "%";
            specifications.add((root, criteriaQuery, builder) -> builder.or(
                    builder.like(builder.lower(root.get("fullName")), pattern),
                    builder.like(builder.lower(root.get("admissionNo")), pattern)));
        }
        if (status != null) {
            specifications.add((root, criteriaQuery, builder) -> builder.equal(root.get("status"), status));
        }
        if (classId != null) {
            specifications.add(enrolledIn(classId));
        }

        return specifications.isEmpty() ? null : Specification.allOf(specifications);
    }

    private static Specification<Student> enrolledIn(Long classId) {
        return (root, criteriaQuery, builder) -> {
            Subquery<Long> enrolled = criteriaQuery.subquery(Long.class);
            Root<StudentEnrollment> enrollment = enrolled.from(StudentEnrollment.class);
            Path<Student> studentPath = enrollment.get("student");
            enrolled.select(studentPath.<Long>get("id"))
                    .where(builder.equal(enrollment.get("schoolClass").get("id"), classId));
            return root.get("id").in(enrolled);
        };
    }

    private static StudentResponse toResponse(Student student, StudentEnrollment openEnrollment) {
        return new StudentResponse(
                student.getId(),
                student.getFullName(),
                student.getAdmissionNo(),
                student.getDob(),
                student.getPhotoPath(),
                student.getAdmissionDate(),
                student.getStatus(),
                openEnrollment == null ? null : openEnrollment.getSchoolClass().getId(),
                openEnrollment == null ? null : openEnrollment.getSchoolClass().getName(),
                openEnrollment == null ? null : openEnrollment.getSchoolClass().getSection(),
                openEnrollment == null ? null : openEnrollment.getRollNo());
    }

    static EnrollmentResponse toEnrollment(StudentEnrollment enrollment) {
        SchoolClass schoolClass = enrollment.getSchoolClass();
        return new EnrollmentResponse(
                enrollment.getId(),
                schoolClass.getId(),
                schoolClass.getName(),
                schoolClass.getSection(),
                schoolClass.getAcademicYear().getName(),
                enrollment.getRollNo(),
                enrollment.getFromDate(),
                enrollment.getToDate());
    }
}
