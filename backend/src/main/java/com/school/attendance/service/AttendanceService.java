package com.school.attendance.service;

import com.school.academic.domain.SchoolClass;
import com.school.academic.repository.SchoolClassRepository;
import com.school.attendance.domain.Attendance;
import com.school.attendance.domain.AttendanceAuditLog;
import com.school.attendance.domain.AttendanceSource;
import com.school.attendance.domain.AttendanceStatus;
import com.school.attendance.repository.AttendanceAuditLogRepository;
import com.school.attendance.repository.AttendanceRepository;
import com.school.attendance.web.dto.AttendanceCounts;
import com.school.attendance.web.dto.AttendanceRosterResponse;
import com.school.attendance.web.dto.AttendanceRosterRow;
import com.school.attendance.web.dto.AttendanceScanRequest;
import com.school.attendance.web.dto.AttendanceScanResponse;
import com.school.attendance.web.dto.BulkAttendanceEntry;
import com.school.attendance.web.dto.BulkAttendanceRequest;
import com.school.attendance.web.dto.MarkableClassResponse;
import com.school.auth.security.AuthenticatedUser;
import com.school.calendar.service.SchoolCalendarService;
import com.school.common.error.ApiException;
import com.school.common.error.ErrorCode;
import com.school.student.domain.Student;
import com.school.student.domain.StudentEnrollment;
import com.school.student.repository.StudentEnrollmentRepository;
import com.school.student.repository.StudentRepository;
import com.school.teacher.domain.SubstitutePermission;
import com.school.teacher.domain.TeacherAssignment;
import com.school.teacher.repository.SubstitutePermissionRepository;
import com.school.teacher.repository.TeacherAssignmentRepository;
import com.school.user.domain.Role;
import com.school.user.domain.User;
import com.school.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AttendanceService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final AttendanceRepository attendanceRepository;
    private final AttendanceAuditLogRepository attendanceAuditLogRepository;
    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final TeacherAssignmentRepository teacherAssignmentRepository;
    private final SubstitutePermissionRepository substitutePermissionRepository;
    private final UserRepository userRepository;
    private final SchoolCalendarService schoolCalendarService;
    private final Clock clock;

    public AttendanceService(
            AttendanceRepository attendanceRepository,
            AttendanceAuditLogRepository attendanceAuditLogRepository,
            StudentRepository studentRepository,
            StudentEnrollmentRepository studentEnrollmentRepository,
            SchoolClassRepository schoolClassRepository,
            TeacherAssignmentRepository teacherAssignmentRepository,
            SubstitutePermissionRepository substitutePermissionRepository,
            UserRepository userRepository,
            SchoolCalendarService schoolCalendarService,
            Clock clock
    ) {
        this.attendanceRepository = attendanceRepository;
        this.attendanceAuditLogRepository = attendanceAuditLogRepository;
        this.studentRepository = studentRepository;
        this.studentEnrollmentRepository = studentEnrollmentRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.teacherAssignmentRepository = teacherAssignmentRepository;
        this.substitutePermissionRepository = substitutePermissionRepository;
        this.userRepository = userRepository;
        this.schoolCalendarService = schoolCalendarService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AttendanceRosterResponse roster(Long classId, LocalDate date, AuthenticatedUser actor) {
        SchoolClass schoolClass = findClass(classId);
        LocalDate onDate = date == null ? LocalDate.now(clock) : date;
        requireCanMark(actor, classId, onDate);
        return buildRoster(schoolClass, onDate);
    }

    @Transactional
    public AttendanceScanResponse scan(AttendanceScanRequest request, AuthenticatedUser actor) {
        SchoolClass schoolClass = findClass(request.classId());
        LocalDate date = request.date();
        requireCanMark(actor, schoolClass.getId(), date);
        requireWorkingDay(date, false, isAdmin(actor));

        Student student = studentRepository.findByQrToken(request.qrToken())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND,
                        "No student matches that QR code."));

        if (!studentEnrollmentRepository.existsEnrollmentInClass(student.getId(), schoolClass.getId(), date)) {
            return new AttendanceScanResponse(
                    student.getId(), student.getFullName(), student.getPhotoPath(),
                    null, null, false, false,
                    student.getFullName() + " is not enrolled in " + label(schoolClass) + " on " + date
                            + " — not marked.",
                    "Warning: wrong class.");
        }

        Optional<Attendance> existing = attendanceRepository.findByStudent_IdAndDate(student.getId(), date);
        if (existing.isPresent()) {
            Attendance attendance = existing.get();
            String time = TIME.format(attendance.getMarkedAt().atZoneSameInstant(clock.getZone()));
            return new AttendanceScanResponse(
                    student.getId(), student.getFullName(), student.getPhotoPath(),
                    attendance.getStatus(), attendance.getMarkedAt(), false, true, null,
                    "Already marked at " + time + ".");
        }

        User marker = userRepository.getReferenceById(actor.id());
        Attendance attendance = new Attendance();
        attendance.setStudent(student);
        attendance.setDate(date);
        attendance.setStatus(AttendanceStatus.PRESENT);
        attendance.setSource(AttendanceSource.QR);
        attendance.setMarkedBy(marker);
        Attendance saved = attendanceRepository.saveAndFlush(attendance);
        audit(saved, null, AttendanceStatus.PRESENT, marker);

        return new AttendanceScanResponse(
                student.getId(), student.getFullName(), student.getPhotoPath(),
                saved.getStatus(), saved.getMarkedAt(), true, false, null, "Marked present.");
    }

    @Transactional
    public AttendanceRosterResponse markBulk(Long classId, BulkAttendanceRequest request, AuthenticatedUser actor) {
        SchoolClass schoolClass = findClass(classId);
        LocalDate date = request.date();
        LocalDate today = LocalDate.now(clock);
        AttendanceSource source = request.source() == null ? AttendanceSource.MANUAL : request.source();
        boolean admin = isAdmin(actor);

        requireCanMark(actor, classId, date);

        if (AttendanceRules.isFuture(date, today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "Attendance cannot be marked for a future date.");
        }
        if (source == AttendanceSource.PAPER_BACKFILL && AttendanceRules.backfillNeedsAdmin(date, today) && !admin) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                    "Paper backfill older than " + AttendanceRules.CLASS_TEACHER_BACKFILL_DAYS
                            + " days can only be done by an admin.");
        }
        requireWorkingDay(date, Boolean.TRUE.equals(request.overrideHoliday()), admin);

        List<StudentEnrollment> roster = studentEnrollmentRepository.findRosterFor(classId, date);
        Map<Long, StudentEnrollment> byStudent = roster.stream()
                .collect(Collectors.toMap(enrollment -> enrollment.getStudent().getId(), Function.identity()));

        for (BulkAttendanceEntry entry : request.entries()) {
            if (!byStudent.containsKey(entry.studentId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                        "Student " + entry.studentId() + " is not enrolled in " + label(schoolClass)
                                + " on " + date + ".");
            }
        }

        User marker = userRepository.getReferenceById(actor.id());
        for (BulkAttendanceEntry entry : request.entries()) {
            Student student = byStudent.get(entry.studentId()).getStudent();
            Optional<Attendance> existing = attendanceRepository.findByStudent_IdAndDate(student.getId(), date);
            if (existing.isPresent()) {
                Attendance attendance = existing.get();
                if (attendance.getStatus() != entry.status()) {
                    AttendanceStatus old = attendance.getStatus();
                    attendance.setStatus(entry.status());
                    attendance.setSource(source);
                    attendance.setMarkedBy(marker);
                    attendanceRepository.saveAndFlush(attendance);
                    audit(attendance, old, entry.status(), marker);
                }
            } else {
                Attendance attendance = new Attendance();
                attendance.setStudent(student);
                attendance.setDate(date);
                attendance.setStatus(entry.status());
                attendance.setSource(source);
                attendance.setMarkedBy(marker);
                Attendance saved = attendanceRepository.saveAndFlush(attendance);
                audit(saved, null, entry.status(), marker);
            }
        }

        return buildRoster(schoolClass, date);
    }

    @Transactional(readOnly = true)
    public List<MarkableClassResponse> markableClasses(AuthenticatedUser actor, LocalDate date) {
        LocalDate onDate = date == null ? LocalDate.now(clock) : date;
        Map<Long, MarkableClassResponse> byClass = new LinkedHashMap<>();

        for (TeacherAssignment assignment : teacherAssignmentRepository.findActiveClassTeacherAssignments(actor.id())) {
            SchoolClass schoolClass = assignment.getSchoolClass();
            byClass.putIfAbsent(schoolClass.getId(), toMarkable(schoolClass, "CLASS_TEACHER"));
        }
        for (SubstitutePermission permission : substitutePermissionRepository.findActiveForTeacher(actor.id(), onDate)) {
            SchoolClass schoolClass = permission.getSchoolClass();
            byClass.putIfAbsent(schoolClass.getId(), toMarkable(schoolClass, "SUBSTITUTE"));
        }
        if (isAdmin(actor)) {
            for (SchoolClass schoolClass : schoolClassRepository.findAll()) {
                byClass.putIfAbsent(schoolClass.getId(), toMarkable(schoolClass, "ADMIN"));
            }
        }

        return byClass.values().stream()
                .sorted(Comparator.comparing(MarkableClassResponse::className, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(MarkableClassResponse::section, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private AttendanceRosterResponse buildRoster(SchoolClass schoolClass, LocalDate date) {
        List<StudentEnrollment> roster = studentEnrollmentRepository.findRosterFor(schoolClass.getId(), date);
        List<Long> studentIds = roster.stream().map(enrollment -> enrollment.getStudent().getId()).toList();

        Map<Long, Attendance> byStudent = studentIds.isEmpty()
                ? Map.of()
                : attendanceRepository.findByDateAndStudent_IdIn(date, studentIds).stream()
                        .collect(Collectors.toMap(attendance -> attendance.getStudent().getId(), Function.identity()));

        List<AttendanceRosterRow> rows = new ArrayList<>(roster.stream()
                .sorted(Comparator
                        .comparing(StudentEnrollment::getRollNo, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(enrollment -> enrollment.getStudent().getFullName(), String.CASE_INSENSITIVE_ORDER))
                .map(enrollment -> {
                    Student student = enrollment.getStudent();
                    Attendance attendance = byStudent.get(student.getId());
                    return new AttendanceRosterRow(
                            student.getId(),
                            student.getFullName(),
                            student.getAdmissionNo(),
                            student.getPhotoPath(),
                            enrollment.getRollNo(),
                            attendance == null ? null : attendance.getStatus(),
                            attendance == null ? null : attendance.getMarkedAt(),
                            attendance == null ? null : attendance.getSource());
                })
                .toList());

        return new AttendanceRosterResponse(
                schoolClass.getId(),
                schoolClass.getName(),
                schoolClass.getSection(),
                schoolClass.getAcademicYear().getName(),
                date,
                schoolCalendarService.isWorkingDay(date),
                counts(rows),
                rows);
    }

    private static AttendanceCounts counts(List<AttendanceRosterRow> rows) {
        int present = 0;
        int absent = 0;
        int late = 0;
        int halfDay = 0;
        int excused = 0;
        int unmarked = 0;

        for (AttendanceRosterRow row : rows) {
            AttendanceStatus status = row.status();
            if (status == null) {
                unmarked++;
                continue;
            }
            switch (status) {
                case PRESENT -> present++;
                case ABSENT -> absent++;
                case LATE -> late++;
                case HALF_DAY -> halfDay++;
                case EXCUSED -> excused++;
            }
        }

        return new AttendanceCounts(rows.size(), present, absent, late, halfDay, excused, unmarked);
    }

    private void audit(Attendance attendance, AttendanceStatus oldStatus, AttendanceStatus newStatus, User changedBy) {
        AttendanceAuditLog log = new AttendanceAuditLog();
        log.setAttendance(attendance);
        log.setChangedBy(changedBy);
        log.setOldStatus(oldStatus);
        log.setNewStatus(newStatus);
        attendanceAuditLogRepository.saveAndFlush(log);
    }

    private void requireCanMark(AuthenticatedUser actor, Long classId, LocalDate date) {
        if (isAdmin(actor)) {
            return;
        }
        if (teacherAssignmentRepository.existsActiveClassTeacher(actor.id(), classId)) {
            return;
        }
        if (substitutePermissionRepository.existsActive(actor.id(), classId, date)) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                "Only the class teacher or a teacher with a substitute permission can mark this class.");
    }

    private void requireWorkingDay(LocalDate date, boolean overrideRequested, boolean admin) {
        if (schoolCalendarService.isWorkingDay(date)) {
            return;
        }
        if (admin && overrideRequested) {
            return;
        }
        throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                date + " is not a working day. Only an admin can override this.");
    }

    private static boolean isAdmin(AuthenticatedUser actor) {
        return actor.roles().contains(Role.ADMIN);
    }

    private SchoolClass findClass(Long id) {
        return schoolClassRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Class not found."));
    }

    private static MarkableClassResponse toMarkable(SchoolClass schoolClass, String role) {
        return new MarkableClassResponse(
                schoolClass.getId(),
                schoolClass.getName(),
                schoolClass.getSection(),
                schoolClass.getAcademicYear().getName(),
                role);
    }

    private static String label(SchoolClass schoolClass) {
        return schoolClass.getName() + " " + schoolClass.getSection();
    }
}
