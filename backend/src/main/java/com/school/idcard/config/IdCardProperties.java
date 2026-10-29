package com.school.idcard.config;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Layout and branding settings for the printable student ID cards, all under {@code app.id-cards}.
 * The card defaults to the ISO/IEC 7810 ID-1 size (85.6 &#215; 54 mm) laid out in a 2 &#215; 4 grid on A4.
 */
@Validated
@ConfigurationProperties(prefix = "app.id-cards")
public record IdCardProperties(
        @DefaultValue("School") String schoolName,
        @DefaultValue("./uploads/photos") String photoDirectory,
        @Positive @DefaultValue("85.6") float cardWidthMm,
        @Positive @DefaultValue("54") float cardHeightMm,
        @Positive @DefaultValue("2") int columns,
        @Positive @DefaultValue("4") int rows,
        @PositiveOrZero @DefaultValue("12") float marginMm,
        @PositiveOrZero @DefaultValue("8") float gutterMm
) {
}
