package com.example.Release_Assistant_Backend.validation;


import java.util.List;

/** Business-rule failures (duplicate version, unreviewed statements). Field checks use Bean Validation. */
public class ValidationException extends RuntimeException {
    private final List<String> errors;

    public ValidationException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}
