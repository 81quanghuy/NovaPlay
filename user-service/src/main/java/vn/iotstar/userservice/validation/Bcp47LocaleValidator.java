package vn.iotstar.userservice.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Locale;

public class Bcp47LocaleValidator implements ConstraintValidator<BCP47Locale, String> {

    /** Tag mà Java trả về khi không nhận dạng được ngôn ngữ ("undetermined"). */
    private static final String UNDETERMINED = "und";

    @Override
    public boolean isValid(String value, ConstraintValidatorContext c) {
        if (value == null || value.isBlank()) return true; // để @NotBlank xử lý riêng nếu cần

        // Locale.forLanguageTag không ném exception với đầu vào rác: nó trả về tag rỗng, hoặc
        // "und" khi không nhận dạng được. Chỉ kiểm tra rỗng là chưa đủ để loại giá trị sai.
        String tag = Locale.forLanguageTag(value.replace('_', '-').trim()).toLanguageTag();
        return !tag.isEmpty() && !UNDETERMINED.equals(tag);
    }
}
