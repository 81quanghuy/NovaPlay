package vn.iotstar.authservice.util.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[!@#$%^&*()]");

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.length() < 10) return false;
        if (!UPPERCASE.matcher(password).find()) return false;
        if (!LOWERCASE.matcher(password).find()) return false;
        if (!DIGIT.matcher(password).find()) return false;
        return SPECIAL.matcher(password).find();
    }
}
