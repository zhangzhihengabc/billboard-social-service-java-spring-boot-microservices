package com.billboard.social.common.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class CustomErrorController implements ErrorController {

    @RequestMapping(value = "/error", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);

        int statusCode = (status != null) ? Integer.parseInt(status.toString()) : 500;
        String errorMessage = (message != null && !message.toString().isEmpty())
                ? message.toString()
                : getDefaultMessage(statusCode);

        HttpStatus httpStatus = HttpStatus.resolve(statusCode);
        if (httpStatus == null) {
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        Map<String, Object> errorResponse = Map.of(
                "timestamp", Instant.now().toString(),
                "status", statusCode,
                "error", httpStatus.getReasonPhrase(),
                "message", errorMessage,
                "validationErrors", Map.of()
        );

        return ResponseEntity.status(httpStatus).body(errorResponse);
    }

    private String getDefaultMessage(int statusCode) {
        return switch (statusCode) {
            case 400 -> "Invalid request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not found";
            case 405 -> "Method not allowed";
            case 500 -> "Internal server error";
            default -> "An error occurred";
        };
    }
}