package vn.iotstar.userservice.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = Bcp47LocaleValidator.class)
public @interface BCP47Locale {
    String message() default "locale phải theo BCP47, ví dụ: vi-VN";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
