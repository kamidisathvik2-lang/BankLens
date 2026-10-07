package com.banklens.exception;

import com.banklens.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, "Validation failed", message);
    }

    @ExceptionHandler(BankLensExceptions.EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailExists(BankLensExceptions.EmailAlreadyExistsException ex) {
        return error(HttpStatus.CONFLICT, "Email already registered", ex.getMessage());
    }

    @ExceptionHandler({BankLensExceptions.InvalidCredentialsException.class, BadCredentialsException.class})
    public ResponseEntity<ErrorResponse> handleBadCredentials(RuntimeException ex) {
        return error(HttpStatus.UNAUTHORIZED, "Authentication failed", "Invalid email or password");
    }

    @ExceptionHandler(BankLensExceptions.RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(BankLensExceptions.RateLimitExceededException ex) {
        return error(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded", ex.getMessage());
    }

    @ExceptionHandler(BankLensExceptions.InvalidPdfException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPdf(BankLensExceptions.InvalidPdfException ex) {
        return error(HttpStatus.BAD_REQUEST, "Invalid PDF", ex.getMessage());
    }

    @ExceptionHandler(BankLensExceptions.AnalysisNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(BankLensExceptions.AnalysisNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
    }

    @ExceptionHandler(BankLensExceptions.AnthropicApiException.class)
    public ResponseEntity<ErrorResponse> handleAnthropicError(BankLensExceptions.AnthropicApiException ex) {
        log.error("Anthropic API error: {}", ex.getMessage());
        return error(HttpStatus.SERVICE_UNAVAILABLE, "AI service unavailable", "Please try again later");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleFileTooLarge(MaxUploadSizeExceededException ex) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "File too large", "Maximum file size is 10MB");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "An unexpected error occurred");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(
                ErrorResponse.builder()
                        .error(error)
                        .message(message)
                        .status(status.value())
                        .timestamp(Instant.now())
                        .build()
        );
    }
}
