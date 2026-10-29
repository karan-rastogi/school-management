package com.school.auth.otp;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

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

    @Test
    void doesNotWriteTheVerificationCodeToTheLogByDefault() {
        List<ILoggingEvent> events = captureLogs(() ->
                new ConsoleOtpSender(false).send("+919876543210", "483920", Duration.ofMinutes(5)));

        assertThat(events).allSatisfy(event ->
                assertThat(event.getFormattedMessage()).doesNotContain("483920"));
        assertThat(events).anySatisfy(event ->
                assertThat(event.getFormattedMessage()).contains("****3210"));
    }

    @Test
    void writesTheCodeOnlyWhenExplicitlyEnabledForDevelopment() {
        List<ILoggingEvent> events = captureLogs(() ->
                new ConsoleOtpSender(true).send("+919876543210", "483920", Duration.ofMinutes(5)));

        assertThat(events).anySatisfy(event ->
                assertThat(event.getFormattedMessage()).contains("483920"));
    }

    private static List<ILoggingEvent> captureLogs(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(ConsoleOtpSender.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
            return List.copyOf(appender.list);
        } finally {
            logger.detachAppender(appender);
        }
    }
}
