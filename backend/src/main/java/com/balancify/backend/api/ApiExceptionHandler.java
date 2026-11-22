package com.balancify.backend.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    static final String LOCK_CONFLICT_MESSAGE = "다른 저장과 겹쳐 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(
        ResponseStatusException exception,
        HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String message = exception.getReason();
        if (message == null || message.isBlank()) {
            message = status.getReasonPhrase();
        }

        return error(status, message, request);
    }

    /**
     * A deadlock, a lock wait the database gave up on, or a serialization failure. The database has
     * rolled the whole transaction back, so nothing was half saved and trying again is safe: it is
     * answered as a conflict, like the optimistic-lock conflicts the controllers already answer, not
     * as a server error. On 2026-11-22 two results with the same players, saved in the same second,
     * locked those players in opposite orders; the one the database stopped came back as a 500.
     * Logged (not reported to Sentry) so a run of them can still be seen.
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleLockFailure(
        PessimisticLockingFailureException exception,
        HttpServletRequest request
    ) {
        log.warn(
            "Lock conflict on {} {}, answered 409: {}",
            request.getMethod(),
            request.getRequestURI(),
            NestedExceptionUtils.getMostSpecificCause(exception).getMessage()
        );
        return error(HttpStatus.CONFLICT, LOCK_CONFLICT_MESSAGE, request);
    }

    private static ResponseEntity<ApiErrorResponse> error(
        HttpStatus status,
        String message,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
            OffsetDateTime.now(),
            status.value(),
            status.getReasonPhrase(),
            message,
            request.getRequestURI()
        ));
    }
}
