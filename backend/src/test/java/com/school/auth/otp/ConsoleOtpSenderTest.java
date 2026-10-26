package com.school.auth.otp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleOtpSenderTest {

    @Test
    void masksAllButTheLastFourDigitsSoLogsCarryNoContactDetails() {
        assertThat(ConsoleOtpSender.mask("+919876543210")).isEqualTo("****3210");
        assertThat(ConsoleOtpSender.mask("9876543210")).isEqualTo("****3210");
    }

    @Test
    void masksShortOrMissingNumbersEntirely() {
        assertThat(ConsoleOtpSender.mask("123")).isEqualTo("****");
        assertThat(ConsoleOtpSender.mask(null)).isEqualTo("****");
    }
}
