package com.school.idcard.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.school.common.error.ApiException;
import com.school.common.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/**
 * Renders a string as a PNG QR code using ZXing. The only caller passes the student's opaque
 * {@code qr_token}, so a printed card never carries personal data in its QR payload.
 */
@Component
public class QrCodeGenerator {

    /** Encodes {@code content} into a {@code size} &#215; {@code size} PNG QR code. */
    public byte[] pngBytes(String content, int size) {
        if (content == null || content.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                    "The QR code payload is empty.");
        }

        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 1);

        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                    "The QR code could not be generated.");
        }
    }
}
