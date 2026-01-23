package com.billboard.social.common.security;

import com.billboard.social.common.exception.ValidationException;

import java.util.regex.Pattern;

/**
 * Utility class for validating and sanitizing user input.
 * Prevents garbage/malformed data from being stored in the database.
 */
public class InputValidator {

    private InputValidator() {
        // Utility class
    }

    // Pattern for valid name: letters (including international), numbers, spaces, and basic punctuation
    // Allows: a-z, A-Z, 0-9, spaces, hyphens, apostrophes, ampersand, parentheses, commas, periods
    // Also allows international letters (Latin Extended, Cyrillic, Greek, etc.)
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile(
            "^[\\p{L}\\p{N}][\\p{L}\\p{N}\\s'&(),./-]*$"
    );

    // Pattern to detect problematic Unicode: surrogate pairs, private use, mathematical symbols
    private static final Pattern INVALID_UNICODE_PATTERN = Pattern.compile(
            "[\\uD800-\\uDFFF]" +           // Surrogate pairs (when unpaired or mathematical scripts)
                    "|[\\uE000-\\uF8FF]" +           // Private Use Area
                    "|[\\uFFF0-\\uFFFF]" +           // Specials
                    "|[\\u2000-\\u200F]" +           // General punctuation (zero-width, etc.)
                    "|[\\u2028-\\u202F]" +           // Line/paragraph separators
                    "|[\\u205F-\\u206F]" +           // General punctuation
                    "|[\\uFE00-\\uFE0F]" +           // Variation selectors
                    "|[\\uFEFF]" +                   // BOM
                    "|[\\u0000-\\u001F]" +           // Control characters
                    "|[\\u007F-\\u009F]"             // More control characters
    );

    // Pattern for valid slug
    private static final Pattern VALID_SLUG_PATTERN = Pattern.compile(
            "^[a-z0-9]+(?:-[a-z0-9]+)*$"
    );

    // Pattern for valid description: same as name but allows more punctuation and newlines
    private static final Pattern VALID_TEXT_PATTERN = Pattern.compile(
            "^[\\p{L}\\p{N}\\p{P}\\p{S}\\s]*$"
    );

    /**
     * Validates a name field (e.g., category name, group name).
     * Must start with a letter or number, can contain letters, numbers, spaces, and basic punctuation.
     *
     * @param name      The name to validate
     * @param fieldName The field name for error messages
     * @return Sanitized name (trimmed, null bytes removed)
     * @throws ValidationException if name is invalid
     */
    public static String validateName(String name, String fieldName) {
        if (name == null || name.isBlank()) {
            throw new ValidationException(fieldName + " is required");
        }

        // Remove null bytes and trim
        String sanitized = name.replace("\u0000", "").trim();

        if (sanitized.isEmpty()) {
            throw new ValidationException(fieldName + " is required");
        }

        // Check for invalid Unicode characters
        if (INVALID_UNICODE_PATTERN.matcher(sanitized).find()) {
            throw new ValidationException(fieldName + " contains invalid characters");
        }

        // Check minimum printable content (at least one letter or number)
        if (!sanitized.matches(".*[\\p{L}\\p{N}].*")) {
            throw new ValidationException(fieldName + " must contain at least one letter or number");
        }

        // Check length after sanitization
        if (sanitized.length() < 1) {
            throw new ValidationException(fieldName + " is too short");
        }

        if (sanitized.length() > 100) {
            throw new ValidationException(fieldName + " must be at most 100 characters");
        }

        return sanitized;
    }

    /**
     * Validates and sanitizes a text field (e.g., description).
     * More permissive than name validation.
     *
     * @param text      The text to validate
     * @param fieldName The field name for error messages
     * @param maxLength Maximum allowed length
     * @return Sanitized text or null if input was null
     * @throws ValidationException if text is invalid
     */
    public static String validateText(String text, String fieldName, int maxLength) {
        if (text == null) {
            return null;
        }

        // Remove null bytes
        String sanitized = text.replace("\u0000", "");

        // Check for invalid Unicode characters
        if (INVALID_UNICODE_PATTERN.matcher(sanitized).find()) {
            throw new ValidationException(fieldName + " contains invalid characters");
        }

        if (sanitized.length() > maxLength) {
            throw new ValidationException(fieldName + " must be at most " + maxLength + " characters");
        }

        return sanitized;
    }

    /**
     * Validates an icon field (emoji or short text).
     *
     * @param icon The icon to validate
     * @return Sanitized icon or null if input was null
     * @throws ValidationException if icon is invalid
     */
    public static String validateIcon(String icon) {
        if (icon == null) {
            return null;
        }

        // Remove null bytes
        String sanitized = icon.replace("\u0000", "");

        if (sanitized.length() > 50) {
            throw new ValidationException("Icon must be at most 50 characters");
        }

        return sanitized;
    }

    /**
     * Validates a slug.
     *
     * @param slug The slug to validate
     * @return The slug if valid
     * @throws ValidationException if slug is invalid
     */
    public static String validateSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new ValidationException("Slug is required");
        }

        String sanitized = slug.replace("\u0000", "").trim().toLowerCase();

        if (!VALID_SLUG_PATTERN.matcher(sanitized).matches()) {
            throw new ValidationException("Slug must contain only lowercase letters, numbers, and hyphens");
        }

        if (sanitized.length() > 120) {
            throw new ValidationException("Slug must be at most 120 characters");
        }

        return sanitized;
    }

    /**
     * Sanitizes a search query by removing problematic characters.
     *
     * @param query The search query
     * @return Sanitized query (may be empty)
     */
    public static String sanitizeSearchQuery(String query) {
        if (query == null) {
            return "";
        }

        return query
                .replace("\u0000", "")           // Remove null bytes
                .replaceAll("[\\p{Cntrl}]", "")  // Remove control characters
                .replaceAll("[\\uD800-\\uDFFF]", "") // Remove surrogate pairs
                .trim();
    }

    /**
     * Checks if a string contains only valid printable characters.
     *
     * @param text The text to check
     * @return true if valid, false otherwise
     */
    public static boolean isValidPrintable(String text) {
        if (text == null) {
            return true;
        }
        return !INVALID_UNICODE_PATTERN.matcher(text).find();
    }
}