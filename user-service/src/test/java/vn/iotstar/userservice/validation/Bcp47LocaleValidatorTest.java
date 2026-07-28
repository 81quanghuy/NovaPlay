package vn.iotstar.userservice.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class Bcp47LocaleValidatorTest {

    private final Bcp47LocaleValidator validator = new Bcp47LocaleValidator();

    @ParameterizedTest(name = "hợp lệ: {0}")
    @ValueSource(strings = {"vi-VN", "en-US", "vi_VN", "fr", "zh-Hant-TW"})
    void acceptsValidTags(String tag) {
        assertThat(validator.isValid(tag, null)).isTrue();
    }

    @ParameterizedTest(name = "không hợp lệ: {0}")
    @DisplayName("tag rác bị từ chối, kể cả khi Java trả về \"und\"")
    @ValueSource(strings = {"!!!", "@@@", "123456789012345678"})
    void rejectsInvalidTags(String tag) {
        assertThat(validator.isValid(tag, null)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("null/rỗng để cho @NotBlank quyết định")
    void treatsNullAndBlankAsValid(String tag) {
        assertThat(validator.isValid(tag, null)).isTrue();
    }
}
