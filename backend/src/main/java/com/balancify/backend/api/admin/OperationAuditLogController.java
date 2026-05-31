package com.balancify.backend.api.admin;

import com.balancify.backend.api.admin.dto.OperationAuditLogPageResponse;
import com.balancify.backend.service.OperationAuditLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
        @RequestParam(name = "limit", required = false) Integer limit
    ) {
        int requestedSize = size == null ? limit == null ? 20 : limit : size;
        return operationAuditLogService.getLogs(page, requestedSize);
    }
}
