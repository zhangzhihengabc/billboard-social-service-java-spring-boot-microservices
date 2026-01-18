package com.billboard.social.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.HashMap;
import java.util.Map;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ValidationException extends RuntimeException {
    
    private final Map<String, String> errors;

    public ValidationException(String message) {
        super(message);
        this.errors = new HashMap<>();
    }

    public ValidationException(String message, Map<String, String> errors) {
        super(message);
        this.errors = errors != null ? errors : new HashMap<>();
    }

    public Map<String, String> getErrors() {
        return errors;
    }

    public void addError(String field, String message) {
        this.errors.put(field, message);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
