package com.school.notice.service;

import com.school.academic.domain.SchoolClass;
import com.school.academic.repository.SchoolClassRepository;
import com.school.audit.service.AuditService;
import com.school.auth.security.AuthenticatedUser;
import com.school.calendar.domain.SchoolCalendarDayType;
import com.school.calendar.service.SchoolCalendarService;
import com.school.calendar.web.dto.CalendarDayResponse;
import com.school.calendar.web.dto.CalendarEntryRequest;
import com.school.common.error.ApiException;
import com.school.common.error.ErrorCode;
import com.school.common.web.PageResponse;
import com.school.notice.domain.Notice;
import com.school.notice.domain.NoticeScope;
import com.school.notice.repository.NoticeRepository;
import com.school.notice.web.dto.AssignableClassResponse;
import com.school.notice.web.dto.HolidayFromNoticeRequest;
import com.school.notice.web.dto.NoticeAttachmentResponse;
import com.school.notice.web.dto.NoticeRequest;
import com.school.notice.web.dto.NoticeResponse;
import com.school.notice.web.dto.NoticeUpdateRequest;
import com.school.student.domain.ParentStudentLink;
import com.school.student.domain.Student;
import com.school.student.domain.StudentEnrollment;
import com.school.student.repository.ParentStudentLinkRepository;
import com.school.student.repository.StudentEnrollmentRepository;
import com.school.student.repository.StudentRepository;
import com.school.teacher.domain.TeacherAssignment;
import com.school.teacher.repository.TeacherAssignmentRepository;
import com.school.user.domain.Role;
import com.school.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NoticeService {

    private static final int SENDER_LABEL_MAX = 100;

    private final NoticeRepository noticeRepository;
    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final ParentStudentLinkRepository parentStudentLinkRepository;
    private final TeacherAssignmentRepository teacherAssignmentRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final SchoolCalendarService schoolCalendarService;
    private final NoticeAttachmentStore attachmentStore;
    private final NoticeAttachmentSigner attachmentSigner;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final Clock clock;

    public NoticeService(
            NoticeRepository noticeRepository,
            StudentRepository studentRepository,
            StudentEnrollmentRepository studentEnrollmentRepository,
            ParentStudentLinkRepository parentStudentLinkRepository,
            TeacherAssignmentRepository teacherAssignmentRepository,
            SchoolClassRepository schoolClassRepository,
            SchoolCalendarService schoolCalendarService,
            NoticeAttachmentStore attachmentStore,
            NoticeAttachmentSigner attachmentSigner,
            UserRepository userRepository,
            AuditService auditService,
            Clock clock
    ) {
        this.noticeRepository = noticeRepository;
        this.studentRepository = studentRepository;
        this.studentEnrollmentRepository = studentEnrollmentRepository;
        this.parentStudentLinkRepository = parentStudentLinkRepository;
        this.teacherAssignmentRepository = teacherAssignmentRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.schoolCalendarService = schoolCalendarService;
        this.attachmentStore = attachmentStore;
        this.attachmentSigner = attachmentSigner;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public NoticeResponse create(AuthenticatedUser actor, NoticeRequest request) {
        NoticeScope scope = request.scope();
        SchoolClass schoolClass = null;
        String senderLabel;

        if (scope == NoticeScope.GLOBAL) {
            requireAdmin(actor);
            senderLabel = labelOrDefault(request.senderLabel(), actor.name());
        } else {
            SchoolClass target = findClass(request.classId());
            schoolClass = target;
            if (isAdmin(actor)) {
                senderLabel = labelOrDefault(request.senderLabel(), actor.name());
            } else {
                senderLabel = resolveTeacherLabel(actor, target.getId());
            }
        }

        Notice notice = new Notice();
        notice.setTitle(request.title().trim());
        notice.setBody(request.body().trim());
        notice.setScope(scope);
        notice.setSchoolClass(schoolClass);
        notice.setSender(userRepository.getReferenceById(actor.id()));
        notice.setSenderLabel(senderLabel);
        notice.setExpiresAt(futureExpiryOrNull(request.expiresAt()));

        Notice saved = noticeRepository.saveAndFlush(notice);
        auditService.record("NOTICE_CREATED", "NOTICE", saved.getId(), "scope=" + saved.getScope());
        return toResponse(saved, null);
    }

    @Transactional
    public NoticeResponse update(AuthenticatedUser actor, Long id, NoticeUpdateRequest request) {
        Notice notice = findActive(id);
        requireCanManage(actor, notice);

        notice.setTitle(request.title().trim());
        notice.setBody(request.body().trim());
        if (Boolean.TRUE.equals(request.clearExpiry())) {
            notice.setExpiresAt(null);
        } else if (request.expiresAt() != null) {
            notice.setExpiresAt(futureExpiryOrNull(request.expiresAt()));
        }
        if (Boolean.TRUE.equals(request.removeAttachment())) {
            attachmentStore.delete(notice.getAttachmentKey());
            clearAttachment(notice);
        }
        notice.setEditedAt(OffsetDateTime.now(clock));

        Notice saved = noticeRepository.saveAndFlush(notice);
        auditService.record("NOTICE_UPDATED", "NOTICE", id);
        return toResponse(saved, null);
    }

    @Transactional
    public void softDelete(AuthenticatedUser actor, Long id) {
        Notice notice = findActive(id);
        requireCanManage(actor, notice);
        notice.setDeletedAt(OffsetDateTime.now(clock));
        noticeRepository.saveAndFlush(notice);
        auditService.record("NOTICE_DELETED", "NOTICE", id);
    }

    @Transactional
    public NoticeResponse attach(AuthenticatedUser actor, Long id, MultipartFile file) {
        Notice notice = findActive(id);
        requireCanManage(actor, notice);

        StoredAttachment stored = attachmentStore.store(file);
        attachmentStore.delete(notice.getAttachmentKey());

        notice.setAttachmentKey(stored.key());
        notice.setAttachmentFileName(stored.fileName());
        notice.setAttachmentContentType(stored.contentType());
        notice.setAttachmentSizeBytes(stored.sizeBytes());
        notice.setAttachmentUploadedAt(OffsetDateTime.now(clock));

        Notice saved = noticeRepository.saveAndFlush(notice);
        auditService.record("NOTICE_ATTACHMENT_ADDED", "NOTICE", id);
        return toResponse(saved, null);
    }

    @Transactional
    public void detach(AuthenticatedUser actor, Long id) {
        Notice notice = findActive(id);
        requireCanManage(actor, notice);
        attachmentStore.delete(notice.getAttachmentKey());
        clearAttachment(notice);
        noticeRepository.saveAndFlush(notice);
        auditService.record("NOTICE_ATTACHMENT_REMOVED", "NOTICE", id);
    }

    @Transactional(readOnly = true)
    public PageResponse<NoticeResponse> list(AuthenticatedUser actor, Pageable pageable) {
        Page<Notice> page;
        if (isAdmin(actor)) {
            page = noticeRepository.findByDeletedAtIsNull(pageable);
        } else {
            List<Long> classIds = assignedClassIds(actor.id());
            page = classIds.isEmpty()
                    ? Page.empty(pageable)
                    : noticeRepository.findByScopeAndSchoolClassIdInAndDeletedAtIsNull(NoticeScope.CLASS, classIds, pageable);
        }
        return PageResponse.from(page, notice -> toResponse(notice, null));
    }

    @Transactional(readOnly = true)
    public List<AssignableClassResponse> assignableClasses(AuthenticatedUser actor) {
        if (isAdmin(actor)) {
            return schoolClassRepository.findAll().stream()
                    .map(schoolClass -> new AssignableClassResponse(
                            schoolClass.getId(),
                            schoolClass.getName(),
                            schoolClass.getSection(),
                            schoolClass.getAcademicYear().getName(),
                            labelOrDefault(null, actor.name())))
                    .toList();
        }
        Map<Long, AssignableClassResponse> byClass = new LinkedHashMap<>();
        for (TeacherAssignment assignment : teacherAssignmentRepository.findActiveAssignmentsForTeacher(actor.id())) {
            SchoolClass schoolClass = assignment.getSchoolClass();
            byClass.putIfAbsent(schoolClass.getId(), new AssignableClassResponse(
                    schoolClass.getId(),
                    schoolClass.getName(),
                    schoolClass.getSection(),
                    schoolClass.getAcademicYear().getName(),
                    teacherLabel(assignment, actor.name())));
        }
        return new ArrayList<>(byClass.values()).stream()
                .sorted((a, b) -> (a.className() + a.section()).compareToIgnoreCase(b.className() + b.section()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NoticeResponse> parentFeed(AuthenticatedUser actor) {
        List<ParentStudentLink> links =
                parentStudentLinkRepository.findByParentUserIdOrderedByStudentName(actor.id());
        if (links.isEmpty()) {
            return List.of();
        }

        List<Long> studentIds = links.stream().map(ParentStudentLink::getStudentId).toList();
        Map<Long, Student> students = studentRepository.findAllById(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, student -> student));
        Map<Long, Long> childClassIds = new LinkedHashMap<>();
        for (StudentEnrollment enrollment : studentEnrollmentRepository.findOpenEnrollmentsFor(studentIds)) {
            childClassIds.putIfAbsent(enrollment.getStudent().getId(), enrollment.getSchoolClass().getId());
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Long> classIds = childClassIds.values().stream().filter(java.util.Objects::nonNull).distinct().toList();
        List<Notice> notices = classIds.isEmpty()
                ? noticeRepository.findVisibleGlobal(NoticeScope.GLOBAL, now)
                : noticeRepository.findVisibleForClasses(NoticeScope.GLOBAL, classIds, now);

        List<NoticeResponse> feed = new ArrayList<>();
        for (Notice notice : notices) {
            String childName = notice.getScope() == NoticeScope.CLASS
                    ? childNameFor(notice, childClassIds, students)
                    : null;
            feed.add(toResponse(notice, childName));
        }
        return feed;
    }

    @Transactional
    public CalendarDayResponse createHoliday(AuthenticatedUser actor, Long noticeId, HolidayFromNoticeRequest request) {
        requireAdmin(actor);
        Notice notice = findActive(noticeId);
        if (notice.getScope() != NoticeScope.GLOBAL) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "Only a global notice can create a holiday.");
        }
        String description = request.description() == null || request.description().isBlank()
                ? notice.getTitle()
                : request.description().trim();
        auditService.record("NOTICE_HOLIDAY_CREATED", "NOTICE", noticeId, "date=" + request.date());
        return schoolCalendarService.mark(request.date(), new CalendarEntryRequest(SchoolCalendarDayType.HOLIDAY, description));
    }

    @Transactional(readOnly = true)
    public NoticeAttachmentDownload downloadAttachment(Long noticeId, long expires, String signature) {
        attachmentSigner.verify(noticeId, expires, signature);
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Attachment not found."));
        if (notice.getAttachmentKey() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "This notice has no attachment.");
        }
        return new NoticeAttachmentDownload(
                attachmentStore.load(notice.getAttachmentKey()),
                notice.getAttachmentFileName(),
                notice.getAttachmentContentType(),
                notice.getAttachmentSizeBytes() == null ? 0 : notice.getAttachmentSizeBytes());
    }

    private String childNameFor(Notice notice, Map<Long, Long> childClassIds, Map<Long, Student> students) {
        Long classId = notice.getSchoolClass() == null ? null : notice.getSchoolClass().getId();
        if (classId == null) {
            return null;
        }
        for (Map.Entry<Long, Long> entry : childClassIds.entrySet()) {
            if (classId.equals(entry.getValue())) {
                Student student = students.get(entry.getKey());
                return student == null ? null : student.getFullName();
            }
        }
        return null;
    }

    private String resolveTeacherLabel(AuthenticatedUser actor, Long classId) {
        List<TeacherAssignment> assignments =
                teacherAssignmentRepository.findActiveAssignmentsForClass(actor.id(), classId);
        if (assignments.isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                    "You can only post class notices to classes you are assigned to.");
        }
        return teacherLabel(assignments.get(0), actor.name());
    }

    private List<Long> assignedClassIds(Long teacherUserId) {
        return teacherAssignmentRepository.findActiveAssignmentsForTeacher(teacherUserId).stream()
                .map(assignment -> assignment.getSchoolClass().getId())
                .distinct()
                .toList();
    }

    private void requireCanManage(AuthenticatedUser actor, Notice notice) {
        if (isAdmin(actor)) {
            return;
        }
        boolean isSender = notice.getSender() != null && actor.id().equals(notice.getSender().getId());
        if (notice.getScope() == NoticeScope.CLASS && isSender) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                "Only the sender or an admin can change this notice.");
    }

    private void requireAdmin(AuthenticatedUser actor) {
        if (!isAdmin(actor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                    "Only an admin can post a global notice.");
        }
    }

    private Notice findActive(Long id) {
        return noticeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Notice not found."));
    }

    private SchoolClass findClass(Long classId) {
        if (classId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "A class notice needs a class.");
        }
        return schoolClassRepository.findById(classId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                        "The selected class does not exist."));
    }

    private OffsetDateTime futureExpiryOrNull(OffsetDateTime expiresAt) {
        if (expiresAt == null) {
            return null;
        }
        if (!expiresAt.isAfter(OffsetDateTime.now(clock))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "The expiry must be in the future.");
        }
        return expiresAt;
    }

    private void clearAttachment(Notice notice) {
        notice.setAttachmentKey(null);
        notice.setAttachmentFileName(null);
        notice.setAttachmentContentType(null);
        notice.setAttachmentSizeBytes(null);
        notice.setAttachmentUploadedAt(null);
    }

    private NoticeResponse toResponse(Notice notice, String childName) {
        SchoolClass schoolClass = notice.getSchoolClass();
        NoticeAttachmentResponse attachment = notice.getAttachmentKey() == null ? null : new NoticeAttachmentResponse(
                notice.getAttachmentFileName(),
                notice.getAttachmentContentType(),
                notice.getAttachmentSizeBytes() == null ? 0 : notice.getAttachmentSizeBytes(),
                notice.getAttachmentUploadedAt(),
                attachmentSigner.signedUrl(notice.getId()));

        boolean expired = notice.getExpiresAt() != null
                && !notice.getExpiresAt().isAfter(OffsetDateTime.now(clock));

        return new NoticeResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getBody(),
                notice.getScope(),
                schoolClass == null ? null : schoolClass.getId(),
                schoolClass == null ? null : schoolClass.getName(),
                schoolClass == null ? null : schoolClass.getSection(),
                schoolClass == null ? null : schoolClass.getName() + " " + schoolClass.getSection(),
                notice.getSender() == null ? null : notice.getSender().getId(),
                notice.getSender() == null ? null : notice.getSender().getName(),
                notice.getSenderLabel(),
                notice.getCreatedAt(),
                notice.getEditedAt(),
                notice.getEditedAt() != null,
                notice.getExpiresAt(),
                expired,
                childName,
                attachment);
    }

    static String teacherLabel(TeacherAssignment assignment, String teacherName) {
        String label = assignment.getSubjectName() + " Teacher - " + teacherName;
        return label.length() > SENDER_LABEL_MAX ? label.substring(0, SENDER_LABEL_MAX) : label;
    }

    private static String labelOrDefault(String label, String fallback) {
        String value = label == null || label.isBlank() ? fallback : label.trim();
        return value.length() > SENDER_LABEL_MAX ? value.substring(0, SENDER_LABEL_MAX) : value;
    }

    private static boolean isAdmin(AuthenticatedUser actor) {
        return actor.roles().contains(Role.ADMIN);
    }
}
