package com.school.idcard.web;

import com.school.idcard.service.GeneratedPdf;
import com.school.idcard.service.IdCardService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Admin-only endpoints that stream printable student ID card PDFs: one card for a single student,
 * or a full sheet for every student currently enrolled in a class.
 */
@RestController
@RequestMapping("/admin/id-cards")
@PreAuthorize("hasRole('ADMIN')")
public class IdCardController {

    private final IdCardService idCardService;

    public IdCardController(IdCardService idCardService) {
        this.idCardService = idCardService;
    }

    @GetMapping("/students/{studentId}")
    public ResponseEntity<byte[]> studentCard(@PathVariable Long studentId) {
        return pdf(idCardService.renderStudentCard(studentId));
    }

    @GetMapping("/classes/{classId}")
    public ResponseEntity<byte[]> classSheet(@PathVariable Long classId) {
        return pdf(idCardService.renderClassSheet(classId));
    }

    private static ResponseEntity<byte[]> pdf(GeneratedPdf pdf) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(pdf.fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(pdf.content());
    }
}
