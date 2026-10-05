package com.example.Release_Assistant_Backend.controller;


import com.example.Release_Assistant_Backend.validation.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, List<String> errors) {
        return ResponseEntity.status(status).body(Map.of("errors", errors));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
        List<String> errors = e.getBindingResult().getAllErrors().stream()
                .map(ObjectError::getDefaultMessage).distinct().toList();
        return body(HttpStatus.BAD_REQUEST, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
        return body(HttpStatus.BAD_REQUEST, List.of("The request body is not valid JSON."));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> validation(ValidationException e) {
        return body(HttpStatus.BAD_REQUEST, e.getErrors());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> notFound(NoSuchElementException e) {
        return body(HttpStatus.NOT_FOUND, List.of(e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> conflict(IllegalStateException e) {
        return body(HttpStatus.CONFLICT, List.of(e.getMessage()));
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, Object>> aiFailure(RestClientException e) {
        return body(HttpStatus.BAD_GATEWAY, List.of("The AI service request failed: " + e.getMessage()));
    }
}
