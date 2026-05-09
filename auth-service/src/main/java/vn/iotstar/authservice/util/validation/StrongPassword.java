package vn.iotstar.authservice.util.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {
    String message() default "Password must be at least 10 characters and contain at least 1 uppercase, 1 lowercase, 1 digit, and 1 special character (!@#$%^&*())";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
