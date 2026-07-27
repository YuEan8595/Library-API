package com.library.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a string as an ISBN-10 or ISBN-13, hyphens and spaces permitted.
 * A dedicated constraint rather than an inline Pattern so that the rule lives
 * in one place, reports as a normal field-level 400, and can be unit tested on its own.
 */
@Documented
@Constraint(validatedBy = IsbnValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD,
         ElementType.CONSTRUCTOR, ElementType.ANNOTATION_TYPE, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Isbn {

    String message() default "must be a valid ISBN-10 or ISBN-13";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
