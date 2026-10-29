package com.school.idcard.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.school.common.error.ApiException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QrCodeGeneratorTest {

    private final QrCodeGenerator generator = new QrCodeGenerator();

    @Test
    void encodesExactlyTheOpaqueQrToken() throws Exception {
        String token = "f47ac10b-58cc-4372-a567-0e02b2c3d479";

        byte[] png = generator.pngBytes(token, 300);

        assertThat(decode(png)).isEqualTo(token);
    }

    @Test
    void theQrPayloadNeverCarriesPersonalData() throws Exception {
        // The generator is only ever handed the opaque qr_token. Prove that what comes back out of
        // the rendered code is that token and nothing else - no name, class or admission number.
        String token = UUID.randomUUID().toString();

        String decoded = decode(generator.pngBytes(token, 300));

        assertThat(decoded).isEqualTo(token);
        assertThat(decoded).doesNotContain("Aarav", "Sharma", "ADM-2025-001", "Grade 7");
    }

    @Test
    void rejectsBlankContent() {
        assertThatThrownBy(() -> generator.pngBytes("   ", 300))
                .isInstanceOf(ApiException.class);
    }

    private static String decode(byte[] png) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        Result result = new MultiFormatReader().decode(bitmap);
        return result.getText();
    }
}
