package com.library.api.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates every exception into an RFC 7807 application/problem+json body,
 * so clients get one consistent error shape instead of Spring's default page.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_CODE = "errorCode";
    private static final String TIMESTAMP = "timestamp";

    /** Errors the application raises on purpose. */
    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex, HttpServletRequest request) {
        log.info("Handled API error [{}] on {} {}: {}",
                ex.getErrorCode(), request.getMethod(), request.getRequestURI(), ex.getMessage());
        return problem(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), request);
    }

    /** Bean Validation failures on @RequestBody payloads -> 400 with a per-field breakdown. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Map<String, String>> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage()))
                .toList();

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "One or more fields failed validation", request);
        problem.setProperty("violations", violations);
        return problem;
    }

    /** Validation failures on path variables / request params. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleHandlerValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "One or more request parameters failed validation", request);
    }

    /** Malformed or unparseable JSON. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Request body is missing or is not valid JSON", request);
    }

    /**
     * A sort= (or other Pageable) param naming a field the entity does not have.
     * This is bad client input, so it is a 400, not the 500 the catch-all would otherwise give.
     * Swagger UI's default sort placeholder is the usual trigger.
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail handleUnknownProperty(PropertyReferenceException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_SORT",
                "Unknown sort property '" + ex.getPropertyName() + "'", request);
    }

    /** e.g. /api/v1/books/abc where a numeric id is expected. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Parameter '" + ex.getName() + "' has an invalid value", request);
    }

    /**
     * Last line of defence for the DB constraints (unique email, partial unique index on
     * active loans). If two requests slip past the service-level checks concurrently, the
     * database still rejects one of them and the client sees a clean 409.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Database constraint rejected {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(HttpStatus.CONFLICT, "CONSTRAINT_VIOLATION",
                "The request conflicts with the current state of the library", request);
    }

    /** Unknown URL. Without this the catch-all below would turn a typo into a 500. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "ENDPOINT_NOT_FOUND",
                "No endpoint " + request.getMethod() + " " + request.getRequestURI(), request);
    }

    /** Right URL, wrong verb. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                  HttpServletRequest request) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "Method " + ex.getMethod() + " is not supported for this endpoint", request);
    }

    /** Missing or wrong Content-Type. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Content-Type must be application/json", request);
    }

    /** Anything unexpected: log the detail, return nothing sensitive. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Please contact support if this persists.", request);
    }

    private ProblemDetail problem(HttpStatus status, String errorCode, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("https://library.example.com/problems/" + errorCode.toLowerCase()));
        problem.setInstance(URI.create(request.getRequestURI()));

        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put(ERROR_CODE, errorCode);
        extras.put(TIMESTAMP, Instant.now().toString());
        extras.forEach(problem::setProperty);
        return problem;
    }
}
