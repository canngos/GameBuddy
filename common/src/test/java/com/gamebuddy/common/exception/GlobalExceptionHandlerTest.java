package com.gamebuddy.common.exception;

import static org.junit.jupiter.api.Assertions.*;

import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("a business failure keeps its transaction code and maps to a real HTTP status")
    void testHandleBusiness_whenCalled_UsesTheCodesHttpStatus() {
        ResponseEntity<DefaultMessageResponse> response =
                handler.handleBusiness(new BusinessException(TransactionCode.USER_NOT_FOUND));

        assertEquals(TransactionCode.USER_NOT_FOUND.getHttpStatus(), response.getStatusCode());
        assertEquals(
                String.valueOf(TransactionCode.USER_NOT_FOUND.getId()),
                response.getBody().getStatus().getCode());
        assertFalse(response.getBody().getStatus().isSuccess());
    }

    @Test
    @DisplayName("the detail passed to BusinessException never reaches the caller")
    void testHandleBusiness_whenDetailSupplied_DoesNotLeakIt() {
        BusinessException ex = new BusinessException(
                TransactionCode.DB_ERROR, "duplicate key value violates unique constraint \"gamer_pkey\"");

        ResponseEntity<DefaultMessageResponse> response = handler.handleBusiness(ex);

        String message = response.getBody().getStatus().getMessage();
        assertFalse(message.contains("gamer_pkey"), "internal detail leaked to the client: " + message);
        assertEquals(TransactionCode.DB_ERROR.getMessage(), message);
    }

    @Test
    @DisplayName("an unexpected exception is a generic 500, not the raw message the old handler echoed")
    void testHandleUnexpected_whenCalled_ReturnsGenericMessage() {
        ResponseEntity<DefaultMessageResponse> response =
                handler.handleUnexpected(new IllegalStateException("jdbc:postgresql://db:5432 password=hunter2"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        String message = response.getBody().getStatus().getMessage();
        assertEquals("An unexpected error occurred", message);
        assertFalse(message.contains("hunter2"));
    }

    @Test
    void testHandleAccessDenied_whenCalled_Returns403() {
        ResponseEntity<DefaultMessageResponse> response = handler.handleAccessDenied(new AccessDeniedException("no"));

        assertEquals(TransactionCode.FORBIDDEN.getHttpStatus(), response.getStatusCode());
    }

    @Test
    void testHandleAuthentication_whenCalled_Returns401() {
        ResponseEntity<DefaultMessageResponse> response =
                handler.handleAuthentication(new BadCredentialsException("bad"));

        assertEquals(TransactionCode.TOKEN_INVALID.getHttpStatus(), response.getStatusCode());
    }

    @Test
    void testHandleUnreadableBody_whenCalled_Returns400() {
        ResponseEntity<DefaultMessageResponse> response =
                handler.handleUnreadableBody(new HttpMessageNotReadableException("truncated", null, null));

        assertEquals(TransactionCode.INVALID_REQUEST.getHttpStatus(), response.getStatusCode());
    }

    @Test
    void testHandleBadArgument_whenCalled_Returns400() {
        ResponseEntity<DefaultMessageResponse> response =
                handler.handleBadArgument(new IllegalArgumentException("Invalid UUID string: abc"));

        assertEquals(TransactionCode.INVALID_REQUEST.getHttpStatus(), response.getStatusCode());
        assertFalse(response.getBody().getStatus().getMessage().contains("abc"));
    }
}
