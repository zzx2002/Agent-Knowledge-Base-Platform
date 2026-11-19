package com.agentkb.admin.controller;

import com.agentkb.admin.service.AdminStatsService;
import com.agentkb.common.BusinessException;
import com.agentkb.common.R;
import com.agentkb.security.KbAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 数据面板统计接口。 */
@Tag(name = "数据面板")
@RestController
@RequestMapping("/api/v1/admin/stats")
public class AdminStatsController {

    private final AdminStatsService statsService;
    private final KbAccessService accessService;

    public AdminStatsController(AdminStatsService statsService, KbAccessService accessService) {
        this.statsService = statsService;
        this.accessService = accessService;
    }

    @Operation(summary = "平台运行概览（仅 ADMIN）")
    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        if (!accessService.isAdmin()) {
            throw BusinessException.forbidden("仅系统管理员可查看数据面板");
        }
        return R.ok(statsService.overview());
    }
}
