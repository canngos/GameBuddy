package com.gamebuddy.common.exception;

import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * One exception handler for every service, replacing five copies.
 *
 * <p>The previous version answered {@code Exception} with
 * {@code status.setMessage(ex.getMessage())}, echoing raw SQL, Hibernate and NPE
 * text back to the caller, and labelled every failure HTTP 500. Now unexpected
 * exceptions are logged server-side and the client gets a generic message, while
 * known failures map to the {@link TransactionCode}'s real HTTP status.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<DefaultMessageResponse> handleBusiness(BusinessException ex) {
        TransactionCode code = ex.getTransactionCode();
        log.debug("Business rule rejected request: {} ({})", code.name(), code.getId());
        return build(code, code.getMessage());
    }

    /** Bean-validation failures on {@code @RequestBody} arguments. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<DefaultMessageResponse> handleBodyValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getAllErrors().stream()
                .map(e -> e.getDefaultMessage() == null ? "invalid value" : e.getDefaultMessage())
                .toList();
        return build(TransactionCode.INVALID_REQUEST, errors.toString());
    }

    /** Bean-validation failures on path variables, headers and request params. */
    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class})
    public ResponseEntity<DefaultMessageResponse> handleParameterValidation(Exception ex) {
        return build(TransactionCode.INVALID_REQUEST, "One or more request parameters are invalid");
    }

    /**
     * An unparseable UUID path variable used to surface as
     * {@code IllegalArgumentException} from {@code UUID.fromString} and become a 500.
     */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    public ResponseEntity<DefaultMessageResponse> handleBadArgument(Exception ex) {
        log.debug("Malformed request argument: {}", ex.getMessage());
        return build(TransactionCode.INVALID_REQUEST, TransactionCode.INVALID_REQUEST.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<DefaultMessageResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return build(TransactionCode.INVALID_REQUEST, "Request body is missing or malformed");
    }

    /**
     * An unmatched path is a 404.
     *
     * <p>Without this, Spring's {@code NoResourceFoundException} fell through to the
     * catch-all below and every request to a URL that does not exist was reported as an
     * HTTP 500 — indistinguishable, to a client, from the server being broken.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<DefaultMessageResponse> handleNoHandler(Exception ex) {
        DefaultMessageResponse response = new DefaultMessageResponse();
        Status status = new Status();
        status.setCode(String.valueOf(HttpStatus.NOT_FOUND.value()));
        status.setMessage("No such endpoint");
        status.setSuccess(false);
        response.setStatus(status);
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<DefaultMessageResponse> handleAccessDenied(AccessDeniedException ex) {
        return build(TransactionCode.FORBIDDEN, TransactionCode.FORBIDDEN.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<DefaultMessageResponse> handleAuthentication(AuthenticationException ex) {
        return build(TransactionCode.TOKEN_INVALID, TransactionCode.TOKEN_INVALID.getMessage());
    }

    /** Catch-all. The detail stays in the log; the caller gets nothing exploitable. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<DefaultMessageResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        DefaultMessageResponse response = new DefaultMessageResponse();
        Status status = new Status();
        status.setCode(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()));
        status.setMessage("An unexpected error occurred");
        status.setSuccess(false);
        response.setStatus(status);
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<DefaultMessageResponse> build(TransactionCode code, String message) {
        DefaultMessageResponse response = new DefaultMessageResponse();
        Status status = new Status();
        status.setCode(String.valueOf(code.getId()));
        status.setMessage(message);
        status.setSuccess(false);
        response.setStatus(status);
        return new ResponseEntity<>(response, code.getHttpStatus());
    }
}
