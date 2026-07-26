package com.library.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class IsbnValidator implements ConstraintValidator<Isbn, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Null/blank is @NotBlank's job. Reporting it here too would give the client
        // two violations for one mistake.
        if (value == null || value.isBlank()) {
            return true;
        }
        return IsbnNormalizer.isValid(value);
    }
}
