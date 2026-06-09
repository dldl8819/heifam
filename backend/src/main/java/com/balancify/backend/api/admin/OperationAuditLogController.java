package com.balancify.backend.api.admin;

import com.balancify.backend.api.admin.dto.OperationAuditLogPageResponse;
import com.balancify.backend.service.OperationAuditLogService;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class OperationAuditLogController {

    private final OperationAuditLogService operationAuditLogService;

    public OperationAuditLogController(OperationAuditLogService operationAuditLogService) {
        this.operationAuditLogService = operationAuditLogService;
    }

    @GetMapping
    public OperationAuditLogPageResponse getAuditLogs(
        @RequestParam(name = "page", required = false, defaultValue = "0") int page,
        @RequestParam(name = "size", required = false) Integer size,
        @RequestParam(name = "limit", required = false) Integer limit,
        @RequestParam(name = "fromDate", required = false) String fromDate,
        @RequestParam(name = "toDate", required = false) String toDate,
        @RequestParam(name = "actor", required = false) String actor,
        @RequestParam(name = "action", required = false) String action,
        @RequestParam(name = "content", required = false) String content,
        @RequestParam(name = "target", required = false) String target
    ) {
        int requestedSize = size == null ? limit == null ? 20 : limit : size;
        LocalDate parsedFromDate = parseDate(fromDate, "fromDate");
        LocalDate parsedToDate = parseDate(toDate, "toDate");
        if (parsedFromDate != null && parsedToDate != null && parsedFromDate.isAfter(parsedToDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromDate must be before or equal to toDate");
        }
        if (
            parsedFromDate == null
                && parsedToDate == null
                && isBlank(actor)
                && isBlank(action)
                && isBlank(content)
                && isBlank(target)
        ) {
            return operationAuditLogService.getLogs(page, requestedSize);
        }

        return operationAuditLogService.getLogs(
            page,
            requestedSize,
            new OperationAuditLogService.OperationAuditLogFilter(
                parsedFromDate,
                parsedToDate,
                actor,
                action,
                content,
                target
            )
        );
    }

    private LocalDate parseDate(String value, String parameterName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                parameterName + " must use yyyy-MM-dd format",
                exception
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
