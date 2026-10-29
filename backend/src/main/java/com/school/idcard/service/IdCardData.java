package com.school.idcard.service;

/**
 * The data rendered onto a single ID card. {@code qrToken} is the student's opaque UUID and is the
 * only thing the QR code encodes &mdash; names, admission numbers and other personal data are printed
 * on the card but never embedded in the code. {@code photo} may be {@code null}, in which case the
 * renderer draws an initials placeholder.
 */
public record IdCardData(
        String fullName,
        String className,
        String section,
        Integer rollNo,
        String qrToken,
        byte[] photo
) {
}
