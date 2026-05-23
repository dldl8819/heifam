package com.balancify.backend.api.admin;

import com.balancify.backend.api.admin.dto.OperationAuditLogResponse;
import com.balancify.backend.service.OperationAuditLogService;
import java.util.List;
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
    public List<OperationAuditLogResponse> getAuditLogs(
        @RequestParam(name = "limit", required = false, defaultValue = "100") int limit
    ) {
        return operationAuditLogService.getRecentLogs(limit);
    }
}
