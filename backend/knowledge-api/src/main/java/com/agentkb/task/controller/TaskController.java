package com.agentkb.task.controller;

import com.agentkb.common.BusinessException;
import com.agentkb.common.PageResult;
import com.agentkb.common.R;
import com.agentkb.security.KbAccessService;
import com.agentkb.task.entity.ParseTask;
import com.agentkb.task.service.ParseResultService;
import com.agentkb.task.service.ParseTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 解析任务接口：/api/v1/tasks/*
 */
@Tag(name = "解析任务管理")
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final ParseTaskService parseTaskService;
    private final ParseResultService parseResultService;
    private final KbAccessService accessService;

    @Value("${kb.callback.token:}")
    private String callbackToken;

    public TaskController(ParseTaskService parseTaskService,
                          ParseResultService parseResultService,
                          KbAccessService accessService) {
        this.parseTaskService = parseTaskService;
        this.parseResultService = parseResultService;
        this.accessService = accessService;
    }

    @Operation(summary = "任务列表（分页）")
    @GetMapping
    public R<PageResult<ParseTask>> list(
            @RequestParam(required = false) Long kbId,
            @RequestParam(value = "knowledge_base_id", required = false) Long knowledgeBaseId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long page_size) {
        Long targetKbId = kbId != null ? kbId : knowledgeBaseId;
        long finalCurrent = page != null ? page : current;
        long finalSize = page_size != null ? page_size : size;
        return R.ok(parseTaskService.page(targetKbId, status, finalCurrent, finalSize));
    }

    @Operation(summary = "任务详情")
    @GetMapping("/{id}")
    public R<ParseTask> detail(@PathVariable Long id) {
        ParseTask task = parseTaskService.getById(id);
        if (task == null) {
            throw BusinessException.notFound("解析任务不存在");
        }
        accessService.require(task.getKbId(), KbAccessService.PERM_READ);
        return R.ok(task);
    }

    @Operation(summary = "Worker 内部任务状态查询")
    @GetMapping("/{id}/status")
    public R<Map<String, Object>> status(@PathVariable Long id,
                                         @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireInternalToken(authorization);
        ParseTask task = parseTaskService.getById(id);
        if (task == null) {
            throw BusinessException.notFound("解析任务不存在");
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("task_id", task.getId());
        result.put("document_id", task.getDocumentId());
        result.put("status", task.getStatus());
        result.put("progress", task.getProgress());
        return R.ok(result);
    }

    @Operation(summary = "重试任务")
    @PostMapping("/{id}/retry")
    public R<ParseTask> retry(@PathVariable Long id) {
        return R.ok(parseTaskService.retry(id));
    }

    @Operation(summary = "取消任务")
    @PostMapping("/{id}/cancel")
    public R<ParseTask> cancel(@PathVariable Long id) {
        return R.ok(parseTaskService.cancel(id));
    }

    /**
     * Parser Worker 状态回调。生产环境必须配置 kb.callback.token，
     * Worker 以 Authorization: Bearer <token> 形式携带。
     */
    @Operation(summary = "解析任务状态回调（内部接口）")
    @PostMapping("/{id}/callback")
    public R<Void> callback(@PathVariable Long id,
                            @RequestHeader(value = "Authorization", required = false) String authorization,
                            @RequestBody Map<String, Object> payload) {
        requireInternalToken(authorization);
        parseResultService.handleCallback(id, payload);
        return R.ok();
    }

    private void requireInternalToken(String authorization) {
        if (callbackToken != null && !callbackToken.isBlank()
                && !("Bearer " + callbackToken).equals(authorization)) {
            throw BusinessException.unauthorized("回调令牌无效");
        }
    }
}
