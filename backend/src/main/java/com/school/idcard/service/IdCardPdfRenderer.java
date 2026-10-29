package com.school.idcard.service;

import com.lowagie.text.BadElementException;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import com.school.common.error.ApiException;
import com.school.common.error.ErrorCode;
import com.school.idcard.config.IdCardProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Renders one or more {@link IdCardData} cards onto A4 pages as a printable PDF sheet, filling the
 * configured grid row by row and spilling onto a new page when it is full.
 */
@Component
public class IdCardPdfRenderer {

    private static final Logger log = LoggerFactory.getLogger(IdCardPdfRenderer.class);

    private static final float MM = 72f / 25.4f;
    private static final float BRAND_BAND_MM = 8f;
    private static final float PHOTO_WIDTH_MM = 24f;
    private static final float PHOTO_HEIGHT_MM = 30f;
    private static final float QR_SIZE_MM = 20f;
    private static final int QR_PIXELS = 512;

    private static final Color INK = new Color(30, 41, 59);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color BRAND = new Color(79, 70, 229);
    private static final Color BORDER = new Color(203, 213, 225);
    private static final Color PLACEHOLDER = new Color(226, 232, 240);

    private final IdCardProperties properties;
    private final QrCodeGenerator qrCodeGenerator;

    public IdCardPdfRenderer(IdCardProperties properties, QrCodeGenerator qrCodeGenerator) {
        this.properties = properties;
        this.qrCodeGenerator = qrCodeGenerator;
    }

    public byte[] render(List<IdCardData> cards) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 0, 0, 0, 0);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            BaseFont regular = createFont(BaseFont.HELVETICA);
            BaseFont bold = createFont(BaseFont.HELVETICA_BOLD);

            PdfContentByte canvas = writer.getDirectContent();
            float cardWidth = properties.cardWidthMm() * MM;
            float cardHeight = properties.cardHeightMm() * MM;
            float margin = properties.marginMm() * MM;
            float gutter = properties.gutterMm() * MM;
            int columns = properties.columns();
            int rows = properties.rows();
            int perPage = Math.max(1, columns * rows);
            float pageHeight = PageSize.A4.getHeight();

            for (int index = 0; index < cards.size(); index++) {
                if (index > 0 && index % perPage == 0) {
                    document.newPage();
                    canvas = writer.getDirectContent();
                }
                int slot = index % perPage;
                float x = margin + (slot % columns) * (cardWidth + gutter);
                float y = pageHeight - margin - cardHeight - (slot / columns) * (cardHeight + gutter);
                drawCard(canvas, cards.get(index), x, y, cardWidth, cardHeight, regular, bold);
            }

            document.close();
        } catch (DocumentException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                    "The ID card sheet could not be generated.");
        }
        return out.toByteArray();
    }

    private void drawCard(
            PdfContentByte canvas,
            IdCardData card,
            float x,
            float y,
            float width,
            float height,
            BaseFont regular,
            BaseFont bold
    ) {
        float radius = 2.5f * MM;

        canvas.saveState();
        canvas.setLineWidth(0.6f);
        canvas.setColorStroke(BORDER);
        canvas.setColorFill(Color.WHITE);
        canvas.roundRectangle(x, y, width, height, radius);
        canvas.fillStroke();
        canvas.restoreState();

        float bandHeight = BRAND_BAND_MM * MM;
        float bandY = y + height - bandHeight;
        canvas.saveState();
        canvas.setColorFill(BRAND);
        canvas.roundRectangle(x, bandY, width, bandHeight, radius);
        canvas.fill();
        // Square off the bottom of the band so it reads as a header strip.
        canvas.rectangle(x, bandY, width, bandHeight / 2f);
        canvas.fill();
        canvas.restoreState();

        String schoolName = fit(properties.schoolName(), bold, 7.5f, width - 6f * MM);
        canvas.setFontAndSize(bold, 7.5f);
        canvas.setColorFill(Color.WHITE);
        canvas.showTextAligned(Element.ALIGN_LEFT, schoolName, x + 3f * MM, bandY + 2.6f * MM, 0);

        float contentTop = bandY - 3f * MM;
        float photoX = x + 3f * MM;
        float photoWidth = PHOTO_WIDTH_MM * MM;
        float photoHeight = PHOTO_HEIGHT_MM * MM;
        float photoY = contentTop - photoHeight;
        drawPhoto(canvas, card, regular, photoX, photoY, photoWidth, photoHeight);

        float textX = photoX + photoWidth + 3f * MM;
        float textWidth = (x + width - 3f * MM) - textX;

        canvas.setFontAndSize(bold, 10f);
        canvas.setColorFill(INK);
        canvas.showTextAligned(Element.ALIGN_LEFT, fit(card.fullName(), bold, 10f, textWidth),
                textX, contentTop - 5f * MM, 0);

        canvas.setFontAndSize(regular, 8f);
        canvas.setColorFill(INK);
        canvas.showTextAligned(Element.ALIGN_LEFT, fit(classLabel(card), regular, 8f, textWidth),
                textX, contentTop - 10.5f * MM, 0);

        if (card.rollNo() != null) {
            canvas.setColorFill(MUTED);
            canvas.showTextAligned(Element.ALIGN_LEFT, "Roll " + card.rollNo(),
                    textX, contentTop - 15.5f * MM, 0);
        }

        drawQr(canvas, card, x + width - 3f * MM - QR_SIZE_MM * MM, y + 3f * MM);
    }

    private void drawPhoto(
            PdfContentByte canvas,
            IdCardData card,
            BaseFont regular,
            float x,
            float y,
            float width,
            float height
    ) {
        byte[] photo = card.photo();
        if (photo != null && photo.length > 0) {
            try {
                Image image = Image.getInstance(photo);
                image.scaleToFit(width, height);
                image.setAbsolutePosition(
                        x + (width - image.getScaledWidth()) / 2f,
                        y + (height - image.getScaledHeight()) / 2f);
                canvas.addImage(image);
                return;
            } catch (BadElementException | IOException invalid) {
                log.debug("Falling back to the initials placeholder for a student ID card photo.");
            }
        }

        canvas.saveState();
        canvas.setColorFill(PLACEHOLDER);
        canvas.rectangle(x, y, width, height);
        canvas.fill();
        canvas.restoreState();

        canvas.setFontAndSize(regular, 14f);
        canvas.setColorFill(MUTED);
        canvas.showTextAligned(Element.ALIGN_CENTER, initials(card.fullName()),
                x + width / 2f, y + height / 2f - 5f, 0);
    }

    private void drawQr(PdfContentByte canvas, IdCardData card, float x, float y) {
        if (card.qrToken() == null || card.qrToken().isBlank()) {
            return;
        }
        float size = QR_SIZE_MM * MM;
        try {
            Image qr = Image.getInstance(qrCodeGenerator.pngBytes(card.qrToken(), QR_PIXELS));
            qr.scaleAbsolute(size, size);
            qr.setAbsolutePosition(x, y);
            canvas.addImage(qr);
        } catch (BadElementException | IOException invalid) {
            log.warn("Could not embed the QR code on a student ID card.");
        }
    }

    private static BaseFont createFont(String name) {
        try {
            return BaseFont.createFont(name, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        } catch (DocumentException | IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                    "The ID card fonts could not be initialised.");
        }
    }

    private static String classLabel(IdCardData card) {
        String name = card.className() == null ? "" : card.className().trim();
        String section = card.section() == null ? "" : card.section().trim();
        if (name.isEmpty()) {
            return section.isEmpty() ? "-" : section;
        }
        return section.isEmpty() ? name : name + " " + section;
    }

    private static String initials(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "?";
        }
        StringBuilder builder = new StringBuilder();
        for (String part : fullName.trim().split("\\s+")) {
            if (builder.length() == 2) {
                break;
            }
            if (!part.isEmpty()) {
                builder.append(Character.toUpperCase(part.charAt(0)));
            }
        }
        return builder.length() == 0 ? "?" : builder.toString();
    }

    /** Truncates to the widest string that fits, appending an ellipsis when it has to shorten. */
    private static String fit(String text, BaseFont font, float size, float maxWidth) {
        String value = sanitise(text == null ? "" : text.trim());
        if (value.isEmpty() || font.getWidthPoint(value, size) <= maxWidth) {
            return value;
        }
        int end = value.length();
        while (end > 1 && font.getWidthPoint(value.substring(0, end) + "...", size) > maxWidth) {
            end--;
        }
        return value.substring(0, end) + "...";
    }

    /** Standard PDF fonts are single-byte; drop anything outside Latin-1 so rendering never fails. */
    private static String sanitise(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            builder.append(character <= 0xFF ? character : '?');
        }
        return builder.toString();
    }
}
