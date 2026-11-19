package com.agentkb.search.controller;

import com.agentkb.common.BusinessException;
import com.agentkb.common.R;
import com.agentkb.security.KbAccessService;
import com.agentkb.search.dto.ChatRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG 问答接口：/api/v1/chat、/api/v1/chat/stream
 * 第一阶段仅定义路径，返回清晰的 501（RAG 问答属于第四阶段）。
 */
@Tag(name = "RAG 问答接口（待实现）")
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final KbAccessService accessService;

    public ChatController(KbAccessService accessService) {
        this.accessService = accessService;
    }

    @Operation(summary = "RAG 问答（第四阶段实现）")
    @PostMapping
    public R<Void> chat(@Valid @RequestBody ChatRequest request) {
        if (request.getKnowledgeBaseId() != null) {
            accessService.require(request.getKnowledgeBaseId(), KbAccessService.PERM_READ);
        }
        throw BusinessException.notImplemented("RAG 问答将在第四阶段（LLM 接入 + 来源引用）实现");
    }

    @Operation(summary = "流式问答（第四阶段实现）")
    @PostMapping("/stream")
    public R<Void> stream(@Valid @RequestBody ChatRequest request) {
        if (request.getKnowledgeBaseId() != null) {
            accessService.require(request.getKnowledgeBaseId(), KbAccessService.PERM_READ);
        }
        throw BusinessException.notImplemented("流式问答将在第四阶段实现");
    }
}
