package com.agentkb.search.controller;

import com.agentkb.chat.dto.ChatResponseDTO;
import com.agentkb.chat.service.ChatService;
import com.agentkb.common.R;
import com.agentkb.search.dto.ChatRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * RAG 问答接口：/api/v1/chat、/api/v1/chat/stream。
 */
@Tag(name = "RAG 问答接口")
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
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
}
