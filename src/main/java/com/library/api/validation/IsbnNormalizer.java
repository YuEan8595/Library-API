package com.library.api.validation;

/**
 * Normalises and checks user-supplied ISBNs.
 *
 * <p>Normalisation is what makes "978-0-13-235088-4", "978 0 13 235088 4" and
 * "9780132350884" resolve to one and the same edition, which matters because the ISBN
 * is the primary key of {@code book_edition}: without it the same book could be
 * registered twice under two spellings of its identifier.
 */
public final class IsbnNormalizer {

    private IsbnNormalizer() {
    }

    /** Strips hyphens/spaces and upper-cases the ISBN-10 check character. */
    public static String normalize(String rawIsbn) {
        return rawIsbn.replaceAll("[\\s-]", "").toUpperCase();
    }

    /**
     * True for a well-formed ISBN-10 or ISBN-13, check digit included.
     *
     * <p>A length-and-digits regex alone would happily accept an 11-digit string or a
     * transposed digit pair; verifying the check digit rejects the overwhelming majority
     * of typos at the edge of the system rather than persisting them.
     */
    public static boolean isValid(String rawIsbn) {
        if (rawIsbn == null) {
            return false;
        }
        String normalized = normalize(rawIsbn);
        return isValidIsbn13(normalized) || isValidIsbn10(normalized);
    }

    /** Weights alternate 1,3,1,3...; the weighted sum must be divisible by 10. */
    private static boolean isValidIsbn13(String isbn) {
        if (isbn.length() != 13) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 13; i++) {
            char c = isbn.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
            sum += (c - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return sum % 10 == 0;
    }

    /** Weights run 10..1, 'X' is a check value of 10, and the sum must be divisible by 11. */
    private static boolean isValidIsbn10(String isbn) {
        if (isbn.length() != 10) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            char c = isbn.charAt(i);
            int value;
            if (c >= '0' && c <= '9') {
                value = c - '0';
            } else if (c == 'X' && i == 9) {
                value = 10;
            } else {
                return false;
            }
            sum += value * (10 - i);
        }
        return sum % 11 == 0;
    }
}
