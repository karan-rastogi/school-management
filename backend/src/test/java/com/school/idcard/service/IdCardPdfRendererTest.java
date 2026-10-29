package com.school.idcard.service;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.school.idcard.config.IdCardProperties;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdCardPdfRendererTest {

    private static final IdCardProperties PROPERTIES =
            new IdCardProperties("Springfield High", "./uploads/photos", 85.6f, 54f, 2, 4, 12f, 8f);

    @Test
    void rendersAValidSinglePagePdfWithTheStudentsDetails() throws Exception {
        QrCodeGenerator generator = mock(QrCodeGenerator.class);
        when(generator.pngBytes(anyString(), anyInt())).thenReturn(png(20, 20));
        IdCardPdfRenderer renderer = new IdCardPdfRenderer(PROPERTIES, generator);

        String token = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
        byte[] pdf = renderer.render(List.of(new IdCardData(
                "Aarav Sharma", "Grade 7", "A", 3, token, png(40, 40))));

        assertThat(isPdf(pdf)).isTrue();

        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text).contains("Springfield").contains("High");
            assertThat(text).contains("Aarav").contains("Sharma");
            assertThat(text).contains("Grade").contains("Roll 3");
        } finally {
            reader.close();
        }

        // The QR code is built from the opaque token alone.
        verify(generator).pngBytes(eq(token), anyInt());
    }

    @Test
    void fallsBackToAPlaceholderWhenNoPhotoOrQrIsPresent() throws Exception {
        QrCodeGenerator generator = mock(QrCodeGenerator.class);
        IdCardPdfRenderer renderer = new IdCardPdfRenderer(PROPERTIES, generator);

        byte[] pdf = renderer.render(List.of(new IdCardData("No Photo", null, null, null, null, null)));

        assertThat(isPdf(pdf)).isTrue();
        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
            assertThat(new PdfTextExtractor(reader).getTextFromPage(1)).contains("No");
        } finally {
            reader.close();
        }
    }

    @Test
    void spillsOntoASecondPageWhenTheGridIsFull() throws Exception {
        QrCodeGenerator generator = mock(QrCodeGenerator.class);
        when(generator.pngBytes(anyString(), anyInt())).thenReturn(png(20, 20));
        IdCardPdfRenderer renderer = new IdCardPdfRenderer(PROPERTIES, generator);

        List<IdCardData> cards = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            cards.add(new IdCardData("Student " + i, "Grade 7", "A", i + 1, "token-" + i, null));
        }

        byte[] pdf = renderer.render(cards);

        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(reader.getNumberOfPages()).isEqualTo(2);
        } finally {
            reader.close();
        }
    }

    private static boolean isPdf(byte[] bytes) {
        return bytes.length > 5 && "%PDF-".equals(new String(bytes, 0, 5, StandardCharsets.ISO_8859_1));
    }

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", out);
        return out.toByteArray();
    }
}
