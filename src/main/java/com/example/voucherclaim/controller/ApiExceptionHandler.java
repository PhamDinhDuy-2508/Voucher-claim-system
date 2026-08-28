package com.example.voucherclaim.controller;

import com.example.voucherclaim.model.response.ErrorResponse;
import com.example.voucherclaim.exception.ServiceException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Instant;
import java.util.stream.Collectors;

/** Converts controller and dependency failures into the stable public error contract. */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Maps expected business failures and adds retry guidance when the error is transient. */
    @ExceptionHandler(ServiceException.class)
    ResponseEntity<ErrorResponse> handleServiceException(ServiceException exception) {
        log.debug("Business request rejected code={} status={} retryable={}",
                exception.code(), exception.status(), exception.retryable());
        HttpHeaders headers = new HttpHeaders();
        if (exception.retryable()) {
            headers.set("Retry-After", "1");
        }
        return new ResponseEntity<>(
                new ErrorResponse(exception.code(), exception.getMessage(), exception.retryable(), Instant.now()),
                headers,
                exception.status()
        );
    }

    /** Aggregates bean-validation messages into one client-safe INVALID_REQUEST response. */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    ResponseEntity<ErrorResponse> handleValidation(Exception exception) {
        var errors = exception instanceof MethodArgumentNotValidException methodArgument
                ? methodArgument.getBindingResult().getAllErrors()
                : ((BindException) exception).getBindingResult().getAllErrors();
        String message = errors.stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.debug("Request body validation failed errorCount={}", errors.size());
        return ResponseEntity.badRequest().body(
                new ErrorResponse("INVALID_REQUEST", message, false, Instant.now()));
    }

    /** Reports a required HTTP header that was not supplied by the caller. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException exception) {
        log.debug("Required request header is missing header={}", exception.getHeaderName());
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "MISSING_HEADER", exception.getMessage(), false, Instant.now()));
    }

    /** Maps method-level validation failures without logging the original request payload. */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));
        log.debug("Request parameter validation failed violationCount={}",
                exception.getConstraintViolations().size());
        return ResponseEntity.badRequest().body(
                new ErrorResponse("INVALID_REQUEST", message, false, Instant.now()));
    }

    /** Hides parser details while returning a consistent malformed-request response. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorResponse> handleMalformedRequest(Exception exception) {
        log.debug("Malformed API request type={}", exception.getClass().getSimpleName());
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "INVALID_REQUEST", "Request contains malformed JSON or an invalid parameter", false, Instant.now()));
    }

    /** Converts temporary MySQL or Redis connectivity failures into a retryable response. */
    @ExceptionHandler({DataAccessResourceFailureException.class, RedisConnectionFailureException.class})
    ResponseEntity<ErrorResponse> handleDependencyFailure(RuntimeException exception) {
        log.warn("Dependency unavailable", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "1")
                .body(new ErrorResponse(
                        "DEPENDENCY_UNAVAILABLE", "A required dependency is unavailable", true, Instant.now()));
    }

    /** Logs unexpected failures with their stack trace and returns no internal details to clients. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unexpected API error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "Unexpected server error", false, Instant.now()));
    }
}
