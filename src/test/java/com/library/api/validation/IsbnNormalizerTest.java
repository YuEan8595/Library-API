package com.library.api.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IsbnNormalizer")
class IsbnNormalizerTest {

    @Nested
    @DisplayName("normalize")
    class Normalize {

        @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
        @CsvSource({
                "9780132350884,     9780132350884",
                "978-0-13-235088-4, 9780132350884",
                "978 0 13 235088 4, 9780132350884",
                "0-306-40615-2,     0306406152",
                "155860832x,        155860832X"
        })
        @DisplayName("collapses separators and upper-cases the check digit")
        void normalizes(String raw, String expected) {
            assertThat(IsbnNormalizer.normalize(raw)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("isValid")
    class IsValid {

        @ParameterizedTest(name = "\"{0}\" is valid")
        @ValueSource(strings = {
                "9780132350884",        // ISBN-13, Clean Code
                "978-0-13-235088-4",    // same, hyphenated
                "9780201616224",        // ISBN-13
                "0306406152",           // ISBN-10
                "0-306-40615-2",        // same, hyphenated
                "155860832X",           // ISBN-10 with an X check digit
                "155860832x"            // lower-case x is accepted
        })
        void acceptsWellFormedIsbns(String isbn) {
            assertThat(IsbnNormalizer.isValid(isbn)).isTrue();
        }

        @ParameterizedTest(name = "\"{0}\" is rejected")
        @ValueSource(strings = {
                "abc",                  // not numeric
                "97801323508841",       // 14 digits
                "97801323508",          // 11 digits - the gap a length-range regex leaves open
                "9780132350885",        // ISBN-13 with a bad check digit
                "0306406153",           // ISBN-10 with a bad check digit
                "7980132350884",        // ISBN-13 with the first two digits transposed
                "030640615X",           // ISBN-10 whose X check digit does not check out
                "155860X832"            // X anywhere but last
        })
        void rejectsMalformedIsbns(String isbn) {
            assertThat(IsbnNormalizer.isValid(isbn)).isFalse();
        }

        @Test
        @DisplayName("rejects null")
        void rejectsNull() {
            assertThat(IsbnNormalizer.isValid(null)).isFalse();
        }
    }
}
