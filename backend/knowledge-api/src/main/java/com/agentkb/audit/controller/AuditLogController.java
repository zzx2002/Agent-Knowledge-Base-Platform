package com.agentkb.audit.controller;

import com.agentkb.audit.dto.AuditLogDTO;
import com.agentkb.audit.service.AuditLogService;
import com.agentkb.common.BusinessException;
import com.agentkb.common.PageResult;
import com.agentkb.common.R;
import com.agentkb.security.KbAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 操作审计查询。 */
@Tag(name = "操作审计")
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final KbAccessService accessService;

    public AuditLogController(AuditLogService auditLogService, KbAccessService accessService) {
        this.auditLogService = auditLogService;
        this.accessService = accessService;
    }

    @Operation(summary = "审计日志分页列表（仅 ADMIN）")
    @GetMapping
    public R<PageResult<AuditLogDTO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) Boolean success,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        if (!accessService.isAdmin()) {
            throw BusinessException.forbidden("仅系统管理员可查看审计日志");
        }
        return R.ok(auditLogService.page(keyword, resourceType, success, page, pageSize));
    }
}
