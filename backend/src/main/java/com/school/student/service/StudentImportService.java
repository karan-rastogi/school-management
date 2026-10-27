package com.school.student.service;

import com.school.academic.domain.SchoolClass;
import com.school.academic.repository.SchoolClassRepository;
import com.school.auth.service.PhoneNumbers;
import com.school.common.error.ApiException;
import com.school.common.error.ErrorCode;
import com.school.student.domain.ParentStudentLink;
import com.school.student.domain.Student;
import com.school.student.domain.StudentEnrollment;
import com.school.student.domain.StudentStatus;
import com.school.student.repository.ParentStudentLinkRepository;
import com.school.student.repository.StudentEnrollmentRepository;
import com.school.student.repository.StudentRepository;
import com.school.student.web.dto.StudentImportOutcome;
import com.school.student.web.dto.StudentImportReport;
import com.school.student.web.dto.StudentImportRow;
import com.school.user.domain.Role;
import com.school.user.domain.User;
import com.school.user.domain.UserRole;
import com.school.user.repository.UserRepository;
import com.school.user.repository.UserRoleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class StudentImportService {

    static final List<String> REQUIRED_COLUMNS =
            List.of("admission_no", "full_name", "dob", "admission_date");

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"));

    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final ParentStudentLinkRepository parentStudentLinkRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final SchoolClassRepository schoolClassRepository;

    public StudentImportService(
            StudentRepository studentRepository,
            StudentEnrollmentRepository studentEnrollmentRepository,
            ParentStudentLinkRepository parentStudentLinkRepository,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            SchoolClassRepository schoolClassRepository
    ) {
        this.studentRepository = studentRepository;
        this.studentEnrollmentRepository = studentEnrollmentRepository;
        this.parentStudentLinkRepository = parentStudentLinkRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.schoolClassRepository = schoolClassRepository;
    }

    @Transactional(readOnly = true)
    public StudentImportReport preview(String csv) {
        return run(csv, true);
    }

    @Transactional
    public StudentImportReport commit(String csv) {
        return run(csv, false);
    }

    private StudentImportReport run(String csv, boolean dryRun) {
        Analysis analysis = analyse(csv);

        if (dryRun || !analysis.canCommit()) {
            return analysis.toReport(dryRun, false);
        }

        persist(analysis);
        return analysis.toReport(false, true);
    }

    private Analysis analyse(String csv) {
        List<List<String>> records = CsvParser.parse(csv);
        if (records.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "The file has no rows.");
        }

        Map<String, Integer> header = header(records.get(0));
        List<String> missing = REQUIRED_COLUMNS.stream().filter(column -> !header.containsKey(column)).toList();
        if (!missing.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "The file is missing these columns: " + String.join(", ", missing) + ".");
        }

        Analysis analysis = new Analysis();

        for (int index = 1; index < records.size(); index++) {
            processRow(analysis, header, index + 1, records.get(index));
        }

        return analysis;
    }

    private void processRow(Analysis analysis, Map<String, Integer> header, int rowNumber, List<String> record) {
        List<String> messages = new ArrayList<>();

        String admissionNo = value(record, header, "admission_no");
        String fullName = value(record, header, "full_name");
        String dobText = value(record, header, "dob");
        String admissionDateText = value(record, header, "admission_date");
        String statusText = value(record, header, "status");
        String className = value(record, header, "class_name");
        String classSection = value(record, header, "class_section");
        String rollNoText = value(record, header, "roll_no");
        String parentName = value(record, header, "parent_name");
        String parentPhoneText = value(record, header, "parent_phone");
        String parentEmail = value(record, header, "parent_email");
        String relationship = value(record, header, "relationship");

        String studentKey = admissionNo.toLowerCase(Locale.ROOT);
        if (studentKey.isBlank()) {
            messages.add("admission_no is required");
        }

        StudentPlan studentPlan = studentKey.isBlank() ? null : analysis.students.get(studentKey);
        if (studentPlan == null && !studentKey.isBlank()) {
            studentPlan = planStudent(analysis, studentKey, rowNumber, admissionNo, fullName, dobText,
                    admissionDateText, statusText, messages);
        } else if (studentPlan != null
                && !fullName.isBlank()
                && !fullName.equalsIgnoreCase(studentPlan.fullName)) {
            messages.add("the name differs from row " + studentPlan.firstRowNumber
                    + ", where admission_no " + admissionNo + " was defined");
        }

        if (studentPlan != null && studentPlan.classId == null
                && (!className.isBlank() || !classSection.isBlank())) {
            resolveClass(studentPlan, className, classSection, messages);
        }
        if (studentPlan != null && studentPlan.rollNo == null && !rollNoText.isBlank()) {
            studentPlan.rollNo = parseRollNo(rollNoText, messages);
        }

        String parentKey = null;
        boolean hasParentData = !parentPhoneText.isBlank() || !parentName.isBlank() || !parentEmail.isBlank();
        if (hasParentData) {
            if (parentPhoneText.isBlank()) {
                messages.add("parent_phone is required when parent details are given");
            } else {
                try {
                    parentKey = PhoneNumbers.normalize(parentPhoneText);
                } catch (ApiException exception) {
                    messages.add("parent_phone (" + parentPhoneText + ") is not a valid phone number");
                }
            }
        }

        if (!messages.isEmpty() || studentPlan == null || studentKey.isBlank()) {
            if (messages.isEmpty()) {
                messages.add("admission_no is required");
            }
            analysis.rows.add(new StudentImportRow(rowNumber, StudentImportOutcome.ERROR, admissionNo,
                    studentPlan == null ? fullName : studentPlan.fullName, parentName, parentKey, List.copyOf(messages)));
            return;
        }

        String outcome = studentPlan.existing == null
                ? StudentImportOutcome.CREATE_STUDENT.name()
                : StudentImportOutcome.DUPLICATE.name();

        if (parentKey != null) {
            ParentPlan parentPlan = planParent(analysis, parentKey, parentName, parentEmail, studentPlan, messages);
            if (!messages.isEmpty()) {
                analysis.rows.add(new StudentImportRow(rowNumber, StudentImportOutcome.ERROR, admissionNo,
                        studentPlan.fullName, parentName, parentKey, List.copyOf(messages)));
                return;
            }

            String linkKey = studentKey + "|" + parentKey;
            boolean alreadyLinked = analysis.linksToCreate.contains(linkKey)
                    || (studentPlan.existing != null && parentPlan.existingUserId != null
                        && parentStudentLinkRepository.existsByParentUserIdAndStudentId(
                                parentPlan.existingUserId, studentPlan.existing.getId()));

            if (alreadyLinked) {
                outcome = StudentImportOutcome.DUPLICATE.name();
            } else {
                analysis.linksToCreate.add(linkKey);
                analysis.linkRelationships.put(linkKey, relationship.isBlank() ? null : relationship);
                if (studentPlan.existing != null) {
                    outcome = StudentImportOutcome.LINK_PARENT.name();
                }
            }
        }

        analysis.committableStudentKeys.add(studentKey);
        if (parentKey != null) {
            analysis.committableParentKeys.add(parentKey);
        }
        analysis.rows.add(new StudentImportRow(rowNumber, StudentImportOutcome.valueOf(outcome), admissionNo,
                studentPlan.fullName, parentName, parentKey, List.of()));
    }

    private StudentPlan planStudent(
            Analysis analysis,
            String studentKey,
            int rowNumber,
            String admissionNo,
            String fullName,
            String dobText,
            String admissionDateText,
            String statusText,
            List<String> messages
    ) {
        StudentPlan plan = new StudentPlan();
        plan.key = studentKey;
        plan.admissionNo = admissionNo;
        plan.firstRowNumber = rowNumber;

        Optional<Student> existing = studentRepository.findByAdmissionNoIgnoreCase(admissionNo);
        if (existing.isPresent()) {
            Student student = existing.get();
            plan.existing = student;
            plan.fullName = student.getFullName();
            plan.dob = student.getDob();
            plan.admissionDate = student.getAdmissionDate();
            plan.status = student.getStatus();
            plan.hasOpenEnrollment = !studentEnrollmentRepository
                    .findOpenEnrollmentsFor(List.of(student.getId())).isEmpty();

            if (!fullName.isBlank() && !fullName.equalsIgnoreCase(student.getFullName())) {
                messages.add("admission_no " + admissionNo + " already belongs to \"" + student.getFullName() + "\"");
            }
            LocalDate providedDob = parseDate(dobText, "dob", messages);
            if (providedDob != null && !providedDob.equals(student.getDob())) {
                messages.add("dob does not match the student already on file for " + admissionNo);
            }
        } else {
            if (fullName.isBlank()) {
                messages.add("full_name is required");
            }
            if (dobText.isBlank()) {
                messages.add("dob is required");
            }
            if (admissionDateText.isBlank()) {
                messages.add("admission_date is required");
            }
            plan.fullName = fullName;
            plan.dob = parseDate(dobText, "dob", messages);
            plan.admissionDate = parseDate(admissionDateText, "admission_date", messages);
            plan.status = parseStatus(statusText, messages);
            if (plan.dob != null && plan.admissionDate != null && plan.dob.isAfter(plan.admissionDate)) {
                messages.add("dob cannot be after admission_date");
            }
        }

        analysis.students.put(studentKey, plan);
        return plan;
    }

    private ParentPlan planParent(
            Analysis analysis,
            String parentKey,
            String parentName,
            String parentEmail,
            StudentPlan studentPlan,
            List<String> messages
    ) {
        ParentPlan knownPlan = analysis.parents.get(parentKey);
        if (knownPlan != null) {
            return knownPlan;
        }

        String email = parentEmail.isBlank() ? null : parentEmail;
        Optional<User> match = userRepository.findByPhone(parentKey);
        if (match.isEmpty() && email != null) {
            match = userRepository.findByEmailIgnoreCase(email);
        }

        ParentPlan plan = new ParentPlan();
        plan.key = parentKey;
        plan.phone = parentKey;
        plan.email = email;
        plan.name = parentName.isBlank() ? "Parent of " + studentPlan.fullName : parentName;

        match.ifPresent(user -> {
            if (!user.isActive()) {
                messages.add("the account matching parent_phone " + parentKey + " is deactivated");
            }
            plan.existingUserId = user.getId();
            if (parentName.isBlank()) {
                plan.name = user.getName();
            }
        });

        analysis.parents.put(parentKey, plan);
        return plan;
    }

    private void resolveClass(StudentPlan plan, String className, String classSection, List<String> messages) {
        if (className.isBlank() || classSection.isBlank()) {
            messages.add("class_name and class_section must both be provided");
            return;
        }
        Optional<SchoolClass> schoolClass = schoolClassRepository
                .findFirstByNameIgnoreCaseAndSectionIgnoreCaseAndAcademicYearCurrentIsTrue(className, classSection);
        if (schoolClass.isEmpty()) {
            messages.add("no class " + className + " " + classSection + " exists in the current academic year");
            return;
        }
        plan.classId = schoolClass.get().getId();
    }

    private Integer parseRollNo(String text, List<String> messages) {
        try {
            int rollNo = Integer.parseInt(text);
            if (rollNo < 1) {
                messages.add("roll_no must be a positive number");
                return null;
            }
            return rollNo;
        } catch (NumberFormatException exception) {
            messages.add("roll_no (" + text + ") is not a number");
            return null;
        }
    }

    private LocalDate parseDate(String text, String column, List<String> messages) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(text, format);
            } catch (DateTimeParseException ignored) {
                // try the next supported layout
            }
        }
        messages.add(column + " (" + text + ") is not a date such as 2015-06-01");
        return null;
    }

    private StudentStatus parseStatus(String text, List<String> messages) {
        if (text == null || text.isBlank()) {
            return StudentStatus.ACTIVE;
        }
        try {
            return StudentStatus.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            messages.add("status (" + text + ") must be ACTIVE or LEFT");
            return null;
        }
    }

    private void persist(Analysis analysis) {
        analysis.students.values().forEach(plan -> {
            if (plan.existing != null) {
                plan.studentId = plan.existing.getId();
                plan.effectiveAdmissionDate = plan.existing.getAdmissionDate();
                return;
            }
            Student student = new Student();
            student.setFullName(plan.fullName);
            student.setAdmissionNo(plan.admissionNo);
            student.setDob(plan.dob);
            student.setAdmissionDate(plan.admissionDate);
            student.setStatus(plan.status == null ? StudentStatus.ACTIVE : plan.status);
            plan.studentId = studentRepository.saveAndFlush(student).getId();
            plan.effectiveAdmissionDate = plan.admissionDate;
        });

        analysis.parents.values().forEach(plan -> {
            if (plan.existingUserId != null) {
                plan.userId = plan.existingUserId;
                if (userRoleRepository.findByUserId(plan.userId).stream()
                        .noneMatch(userRole -> userRole.getRole() == Role.PARENT)) {
                    addParentRole(plan.userId);
                }
                return;
            }
            User parent = new User();
            parent.setName(plan.name);
            parent.setPhone(plan.phone);
            parent.setEmail(plan.email);
            parent.setActive(true);
            plan.userId = userRepository.saveAndFlush(parent).getId();
            addParentRole(plan.userId);
        });

        for (String linkKey : analysis.linksToCreate) {
            String[] parts = linkKey.split("\\|", 2);
            Long studentId = analysis.students.get(parts[0]).studentId;
            Long parentUserId = analysis.parents.get(parts[1]).userId;

            if (parentStudentLinkRepository.existsByParentUserIdAndStudentId(parentUserId, studentId)) {
                continue;
            }
            ParentStudentLink link = new ParentStudentLink();
            link.setParentUserId(parentUserId);
            link.setStudentId(studentId);
            link.setRelationship(analysis.linkRelationships.get(linkKey));
            parentStudentLinkRepository.saveAndFlush(link);
        }

        analysis.students.values().forEach(plan -> {
            if (plan.classId == null || plan.hasOpenEnrollment) {
                return;
            }
            StudentEnrollment enrollment = new StudentEnrollment();
            enrollment.setStudent(studentRepository.getReferenceById(plan.studentId));
            enrollment.setSchoolClass(schoolClassRepository.getReferenceById(plan.classId));
            enrollment.setRollNo(plan.rollNo);
            enrollment.setFromDate(plan.effectiveAdmissionDate);
            studentEnrollmentRepository.saveAndFlush(enrollment);
        });
    }

    private void addParentRole(Long userId) {
        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRole(Role.PARENT);
        userRoleRepository.saveAndFlush(userRole);
    }

    private static Map<String, Integer> header(List<String> firstRecord) {
        Map<String, Integer> header = new LinkedHashMap<>();
        for (int index = 0; index < firstRecord.size(); index++) {
            header.putIfAbsent(firstRecord.get(index).trim().toLowerCase(Locale.ROOT), index);
        }
        return header;
    }

    private static String value(List<String> record, Map<String, Integer> header, String column) {
        Integer position = header.get(column);
        if (position == null || position >= record.size()) {
            return "";
        }
        String raw = record.get(position);
        return raw == null ? "" : raw.trim();
    }

    private static final class StudentPlan {

        private String key;
        private String admissionNo;
        private int firstRowNumber;
        private Student existing;
        private String fullName;
        private LocalDate dob;
        private LocalDate admissionDate;
        private LocalDate effectiveAdmissionDate;
        private StudentStatus status;
        private Long classId;
        private Integer rollNo;
        private boolean hasOpenEnrollment;
        private Long studentId;
    }

    private static final class ParentPlan {

        private String key;
        private String phone;
        private String name;
        private String email;
        private Long existingUserId;
        private Long userId;
    }

    private static final class Analysis {

        private final List<StudentImportRow> rows = new ArrayList<>();
        private final Map<String, StudentPlan> students = new LinkedHashMap<>();
        private final Map<String, ParentPlan> parents = new LinkedHashMap<>();
        private final Set<String> linksToCreate = new LinkedHashSet<>();
        private final Map<String, String> linkRelationships = new LinkedHashMap<>();
        private final Set<String> committableStudentKeys = new LinkedHashSet<>();
        private final Set<String> committableParentKeys = new LinkedHashSet<>();

        private long errorCount() {
            return rows.stream().filter(row -> row.outcome() == StudentImportOutcome.ERROR).count();
        }

        private long duplicateCount() {
            return rows.stream().filter(row -> row.outcome() == StudentImportOutcome.DUPLICATE).count();
        }

        private boolean canCommit() {
            return !rows.isEmpty() && errorCount() == 0;
        }

        private StudentImportReport toReport(boolean dryRun, boolean committed) {
            return new StudentImportReport(
                    dryRun,
                    committed,
                    rows.size(),
                    (int) students.values().stream()
                            .filter(plan -> plan.existing == null && committableStudentKeys.contains(plan.key))
                            .count(),
                    (int) parents.values().stream()
                            .filter(plan -> plan.existingUserId == null && committableParentKeys.contains(plan.key))
                            .count(),
                    linksToCreate.size(),
                    (int) duplicateCount(),
                    (int) errorCount(),
                    canCommit(),
                    List.copyOf(rows));
        }
    }
}
