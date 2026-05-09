package vn.iotstar.authservice.util.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class StrongPasswordValidatorTest {

    private StrongPasswordValidator validator;

    @BeforeEach
    void setUp() {
        validator = new StrongPasswordValidator();
    }

    @ParameterizedTest(name = "weak: [{0}]")
    @ValueSource(strings = {
            "",
            "short",
            "alllowercase1!",
            "ALLUPPERCASE1!",
            "NoSpecialChar1",
            "NoDigit@Upper",
            "short1A!",
            "password1!"
    })
    void weakPasswords_returnFalse(String password) {
        assertFalse(validator.isValid(password, null), "Expected false for: " + password);
    }

    @ParameterizedTest(name = "strong: [{0}]")
    @ValueSource(strings = {
            "StrongP@ss1",
            "Abcdefg1!2",
            "MyP@ssw0rd!",
            "C0mplexPass!",
            "NovaPlay@2024"
    })
    void strongPasswords_returnTrue(String password) {
        assertTrue(validator.isValid(password, null), "Expected true for: " + password);
    }
}
