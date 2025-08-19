package vn.iotstar.userservice.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Locale;

public class Bcp47LocaleValidator implements ConstraintValidator<BCP47Locale, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext c) {
        if (value == null || value.isBlank()) return true; // để @NotBlank xử lý riêng nếu cần
        try {
            var tag = value.replace('_','-').trim();
            return !Locale.forLanguageTag(tag).toLanguageTag().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
