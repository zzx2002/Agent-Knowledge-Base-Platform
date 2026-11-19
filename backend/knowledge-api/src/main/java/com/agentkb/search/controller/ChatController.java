package com.agentkb.search.controller;

import com.agentkb.chat.dto.ChatMessageDTO;
import com.agentkb.chat.dto.ChatResponseDTO;
import com.agentkb.chat.dto.ChatSessionDTO;
import com.agentkb.chat.dto.CreateChatSessionRequest;
import com.agentkb.chat.service.ChatHistoryService;
import com.agentkb.chat.service.ChatService;
import com.agentkb.common.R;
import com.agentkb.search.dto.ChatRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * RAG 问答接口与会话历史。
 */
@Tag(name = "RAG 问答接口")
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;
    private final ChatHistoryService historyService;

    public ChatController(ChatService chatService, ChatHistoryService historyService) {
        this.chatService = chatService;
        this.historyService = historyService;
    }

    @Operation(summary = "RAG 问答")
    @PostMapping
    public R<ChatResponseDTO> chat(@Valid @RequestBody ChatRequest request) {
        return R.ok(chatService.chat(request));
    }

    @Operation(summary = "流式问答（SSE）")
    @PostMapping("/stream")
    public ResponseEntity<SseEmitter> stream(@Valid @RequestBody ChatRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Accel-Buffering", "no");
        headers.setCacheControl("no-cache");
        return ResponseEntity.ok().headers(headers).body(chatService.stream(request));
    }

    @Operation(summary = "创建会话")
    @PostMapping("/sessions")
    public R<ChatSessionDTO> createSession(@Valid @RequestBody CreateChatSessionRequest request) {
        return R.ok(historyService.create(request));
    }

    @Operation(summary = "会话列表")
    @GetMapping("/sessions")
    public R<List<ChatSessionDTO>> listSessions(@RequestParam(required = false) Long knowledgeBaseId) {
        return R.ok(historyService.list(knowledgeBaseId));
    }

    @Operation(summary = "会话消息")
    @GetMapping("/sessions/{id}/messages")
    public R<List<ChatMessageDTO>> messages(@PathVariable Long id) {
        return R.ok(historyService.messages(id));
    }

    @Operation(summary = "删除会话")
    @DeleteMapping("/sessions/{id}")
    public R<Void> deleteSession(@PathVariable Long id) {
        historyService.delete(id);
        return R.ok();
    }
}
