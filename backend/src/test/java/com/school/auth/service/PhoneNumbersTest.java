package com.school.auth.service;

import com.school.common.error.ApiException;
import com.school.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumbersTest {

    @Test
    void stripsFormattingAndKeepsALeadingCountryCode() {
        assertThat(PhoneNumbers.normalize("+91 98765 43210")).isEqualTo("+919876543210");
        assertThat(PhoneNumbers.normalize("(98765) 432-10")).isEqualTo("9876543210");
        assertThat(PhoneNumbers.normalize("  9876543210  ")).isEqualTo("9876543210");
    }

    @Test
    void rejectsNumbersThatCannotBeDialled() {
        assertThatThrownBy(() -> PhoneNumbers.normalize("12345"))
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> assertThat(((ApiException) thrown).getCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));

        assertThatThrownBy(() -> PhoneNumbers.normalize(null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PhoneNumbers.normalize("not-a-number")).isInstanceOf(ApiException.class);
    }
}
