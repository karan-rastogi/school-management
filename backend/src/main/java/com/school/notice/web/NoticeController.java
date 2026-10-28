package com.school.notice.web;

import com.school.auth.security.AuthenticatedUser;
import com.school.calendar.web.dto.CalendarDayResponse;
import com.school.common.web.PageRequests;
import com.school.common.web.PageResponse;
import com.school.notice.service.NoticeService;
import com.school.notice.web.dto.AssignableClassResponse;
import com.school.notice.web.dto.HolidayFromNoticeRequest;
import com.school.notice.web.dto.NoticeRequest;
import com.school.notice.web.dto.NoticeResponse;
import com.school.notice.web.dto.NoticeUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Staff surface for notices: admins post GLOBAL notices, teachers post CLASS notices to the classes
 * they are assigned to. What can be listed, edited and deleted is enforced here and in the service.
 */
@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping("/notices")
    public PageResponse<NoticeResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return noticeService.list(user, PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/notices/assignable-classes")
    public List<AssignableClassResponse> assignableClasses(@AuthenticationPrincipal AuthenticatedUser user) {
        return noticeService.assignableClasses(user);
    }

    @PostMapping("/notices")
    @ResponseStatus(HttpStatus.CREATED)
    public NoticeResponse create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody NoticeRequest request
    ) {
        return noticeService.create(user, request);
    }

    @PutMapping("/notices/{id}")
    public NoticeResponse update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody NoticeUpdateRequest request
    ) {
        return noticeService.update(user, id, request);
    }

    @DeleteMapping("/notices/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        noticeService.softDelete(user, id);
    }

    @PostMapping("/notices/{id}/attachment")
    public NoticeResponse attach(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file
    ) {
        return noticeService.attach(user, id, file);
    }

    @DeleteMapping("/notices/{id}/attachment")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void detach(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        noticeService.detach(user, id);
    }

    @PostMapping("/notices/{id}/holiday")
    @PreAuthorize("hasRole('ADMIN')")
    public CalendarDayResponse createHoliday(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody HolidayFromNoticeRequest request
    ) {
        return noticeService.createHoliday(user, id, request);
    }
}
