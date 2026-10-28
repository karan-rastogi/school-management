package com.school.attendance.service;

import com.school.attendance.domain.Attendance;
import com.school.attendance.domain.AttendanceQuery;
import com.school.attendance.domain.AttendanceQueryStatus;
import com.school.attendance.domain.AttendanceStatus;
import com.school.attendance.repository.AttendanceQueryRepository;
import com.school.attendance.repository.AttendanceRepository;
import com.school.attendance.web.dto.AttendanceQueryResponse;
import com.school.auth.security.AuthenticatedUser;
import com.school.common.error.ApiException;
import com.school.common.error.ErrorCode;
import com.school.student.domain.Student;
import com.school.student.domain.StudentEnrollment;
import com.school.student.repository.StudentEnrollmentRepository;
import com.school.student.repository.StudentRepository;
import com.school.teacher.domain.TeacherAssignment;
import com.school.teacher.repository.TeacherAssignmentRepository;
import com.school.user.domain.Role;
import com.school.user.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Parents raise a query to dispute one attendance date; the class teacher (or an admin) answers it.
 */
@Service
public class AttendanceQueryService {

    private final AttendanceQueryRepository attendanceQueryRepository;
    private final AttendanceRepository attendanceRepository;
    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final TeacherAssignmentRepository teacherAssignmentRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public AttendanceQueryService(
            AttendanceQueryRepository attendanceQueryRepository,
            AttendanceRepository attendanceRepository,
            StudentRepository studentRepository,
            StudentEnrollmentRepository studentEnrollmentRepository,
            TeacherAssignmentRepository teacherAssignmentRepository,
            UserRepository userRepository,
            Clock clock
    ) {
        this.attendanceQueryRepository = attendanceQueryRepository;
        this.attendanceRepository = attendanceRepository;
        this.studentRepository = studentRepository;
        this.studentEnrollmentRepository = studentEnrollmentRepository;
        this.teacherAssignmentRepository = teacherAssignmentRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public AttendanceQueryResponse raise(Long studentId, Long raisedByUserId, LocalDate date, String message) {
        if (date.isAfter(LocalDate.now(clock))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "You cannot raise a query about a future date.");
        }
        if (attendanceQueryRepository.existsByStudentIdAndAttendanceDateAndStatus(
                studentId, date, AttendanceQueryStatus.OPEN)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                    "There is already an open query for " + date + ".");
        }

        AttendanceQuery query = new AttendanceQuery();
        query.setStudent(studentRepository.getReferenceById(studentId));
        query.setAttendanceDate(date);
        query.setRaisedBy(userRepository.getReferenceById(raisedByUserId));
        query.setStatus(AttendanceQueryStatus.OPEN);
        query.setMessage(message.trim());
        return toResponse(attendanceQueryRepository.saveAndFlush(query));
    }

    @Transactional(readOnly = true)
    public List<AttendanceQueryResponse> listForActor(AuthenticatedUser actor, AttendanceQueryStatus statusFilter) {
        List<AttendanceQuery> queries = isAdmin(actor)
                ? adminQueries(statusFilter)
                : teacherQueries(actor, statusFilter);
        return queries.stream().map(this::toResponse).toList();
    }

    @Transactional
    public AttendanceQueryResponse resolve(Long queryId, AuthenticatedUser actor, String response) {
        AttendanceQuery query = attendanceQueryRepository.findById(queryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Query not found."));
        requireCanResolve(actor, query.getStudent().getId());
        if (query.getStatus() == AttendanceQueryStatus.RESOLVED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "This query is already resolved.");
        }
        query.setStatus(AttendanceQueryStatus.RESOLVED);
        query.setResponse(response.trim());
        query.setRespondedBy(userRepository.getReferenceById(actor.id()));
        query.setRespondedAt(OffsetDateTime.now(clock));
        return toResponse(attendanceQueryRepository.saveAndFlush(query));
    }

    private List<AttendanceQuery> adminQueries(AttendanceQueryStatus statusFilter) {
        if (statusFilter != null) {
            return attendanceQueryRepository.findByStatusOrderByCreatedAtDesc(statusFilter);
        }
        return attendanceQueryRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private List<AttendanceQuery> teacherQueries(AuthenticatedUser actor, AttendanceQueryStatus statusFilter) {
        List<Long> studentIds = teacherStudentIds(actor);
        if (studentIds.isEmpty()) {
            return List.of();
        }
        return statusFilter == null
                ? attendanceQueryRepository.findByStudentIdInOrderByCreatedAtDesc(studentIds)
                : attendanceQueryRepository.findByStudentIdInAndStatusOrderByCreatedAtDesc(studentIds, statusFilter);
    }

    private List<Long> teacherStudentIds(AuthenticatedUser actor) {
        LocalDate today = LocalDate.now(clock);
        List<Long> studentIds = new ArrayList<>();
        for (TeacherAssignment assignment : teacherAssignmentRepository.findActiveClassTeacherAssignments(actor.id())) {
            Long classId = assignment.getSchoolClass().getId();
            studentEnrollmentRepository.findRosterFor(classId, today).stream()
                    .map(enrollment -> enrollment.getStudent().getId())
                    .forEach(studentIds::add);
        }
        return studentIds.stream().distinct().toList();
    }

    private void requireCanResolve(AuthenticatedUser actor, Long studentId) {
        if (isAdmin(actor)) {
            return;
        }
        Long classId = currentClassId(studentId);
        if (classId != null && teacherAssignmentRepository.existsActiveClassTeacher(actor.id(), classId)) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                "Only the class teacher of that student can resolve the query.");
    }

    private Long currentClassId(Long studentId) {
        return studentEnrollmentRepository.findOpenEnrollmentsFor(List.of(studentId)).stream()
                .findFirst()
                .map(enrollment -> enrollment.getSchoolClass().getId())
                .orElse(null);
    }

    private AttendanceQueryResponse toResponse(AttendanceQuery query) {
        Student student = query.getStudent();
        Long studentId = student.getId();
        StudentEnrollment enrollment = studentEnrollmentRepository.findOpenEnrollmentsFor(List.of(studentId)).stream()
                .findFirst()
                .orElse(null);
        AttendanceStatus attendanceStatus = attendanceRepository
                .findByStudent_IdAndDate(studentId, query.getAttendanceDate())
                .map(Attendance::getStatus)
                .orElse(null);

        return new AttendanceQueryResponse(
                query.getId(),
                studentId,
                student.getFullName(),
                student.getAdmissionNo(),
                enrollment == null ? null : enrollment.getSchoolClass().getId(),
                enrollment == null ? null : enrollment.getSchoolClass().getName(),
                enrollment == null ? null : enrollment.getSchoolClass().getSection(),
                query.getAttendanceDate(),
                attendanceStatus,
                query.getStatus(),
                query.getMessage(),
                query.getResponse(),
                query.getCreatedAt(),
                query.getRespondedBy() == null ? null : query.getRespondedBy().getName(),
                query.getRespondedAt());
    }

    private static boolean isAdmin(AuthenticatedUser actor) {
        return actor.roles().contains(Role.ADMIN);
    }
}
