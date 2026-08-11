package com.gamebuddy.config;

import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns a lost optimistic lock into a conflict rather than a server error.
 *
 * <p>Separate from {@code GlobalExceptionHandler}, which lives in {@code common}. That
 * module deliberately depends only on web, security and validation — it has no persistence
 * on its classpath, and {@link OptimisticLockingFailureException} is a persistence type.
 * Adding spring-tx there to catch one exception would push a storage concern into the
 * shared web layer for every future consumer.
 *
 * <p><b>Why it matters.</b> Every path that spends or earns coins does read-check-write
 * under the {@code @Version} column on {@code Gamer} — buying a cosmetic, claiming a daily
 * reward, boosting, rewinding. That is what makes a double-tap charge once instead of
 * twice, and it works: measured with eight simultaneous claims, exactly one was paid.
 * But the seven losers came back as HTTP 500 "an unexpected error occurred", over a balance
 * that was entirely correct. The lock doing its job should not look like a crash, and it
 * should not fill the error log either — hence INFO, and hence a 409.
 *
 * <p>Ordered ahead of the catch-all so this wins.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class PersistenceExceptionHandler {

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<DefaultMessageResponse> handleConcurrentModification(
            OptimisticLockingFailureException ex) {
        log.info("Concurrent modification rejected: {}", ex.getMessage());

        TransactionCode code = TransactionCode.CONCURRENT_MODIFICATION;
        DefaultMessageResponse response = new DefaultMessageResponse();
        Status status = new Status();
        status.setCode(String.valueOf(code.getId()));
        status.setMessage(code.getMessage());
        status.setSuccess(false);
        response.setStatus(status);
        return new ResponseEntity<>(response, code.getHttpStatus());
    }
}
