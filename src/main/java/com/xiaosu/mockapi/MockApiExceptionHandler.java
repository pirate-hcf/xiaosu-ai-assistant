package com.xiaosu.mockapi;

import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = MockApiController.class)
public class MockApiExceptionHandler {

    @ExceptionHandler(EmployeeNotFoundException.class)
    public ResponseEntity<ApiError> employeeNotFound(
            EmployeeNotFoundException exception,
            HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "EMPLOYEE_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidDateRangeException.class)
    public ResponseEntity<ApiError> invalidDateRange(
            InvalidDateRangeException exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", exception.getMessage(), request);
    }

    private static ResponseEntity<ApiError> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new ApiError(code, message, request.getRequestURI(), Instant.now()));
    }

    public record ApiError(String code, String message, String path, Instant timestamp) {
    }
}
