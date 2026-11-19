package com.agentkb.chat.service;

import com.agentkb.chat.dto.ChatMessageDTO;
import com.agentkb.chat.dto.ChatSessionDTO;
import com.agentkb.chat.dto.ChatSourceDTO;
import com.agentkb.chat.dto.CreateChatSessionRequest;
import com.agentkb.chat.entity.ChatMessage;
import com.agentkb.chat.entity.ChatSession;
import com.agentkb.chat.mapper.ChatMessageMapper;
import com.agentkb.chat.mapper.ChatSessionMapper;
import com.agentkb.common.BusinessException;
import com.agentkb.security.KbAccessService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/** 问答会话与历史消息。 */
@Service
public class ChatHistoryService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final KbAccessService accessService;
    private final ObjectMapper objectMapper;

    public ChatHistoryService(ChatSessionMapper sessionMapper,
                              ChatMessageMapper messageMapper,
                              KbAccessService accessService,
                              ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.accessService = accessService;
        this.objectMapper = objectMapper;
    }

    public ChatSessionDTO create(CreateChatSessionRequest request) {
        accessService.require(request.getKnowledgeBaseId(), KbAccessService.PERM_READ);
        ChatSession session = new ChatSession();
        session.setUserId(accessService.currentUserId());
        session.setKbId(request.getKnowledgeBaseId());
        String title = request.getTitle();
        session.setTitle(title == null || title.isBlank() ? "新对话" : title.substring(0, Math.min(60, title.length())));
        session.setDeleted(false);
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(session.getCreatedAt());
        sessionMapper.insert(session);
        return toSessionDTO(session);
    }

    public List<ChatSessionDTO> list(Long kbId) {
        Long userId = accessService.currentUserId();
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .eq(kbId != null, ChatSession::getKbId, kbId)
                .orderByDesc(ChatSession::getUpdatedAt);
        return sessionMapper.selectList(wrapper).stream().map(this::toSessionDTO).collect(Collectors.toList());
    }

    public List<ChatMessageDTO> messages(Long sessionId) {
        requireSessionOwner(sessionId);
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getCreatedAt);
        return messageMapper.selectList(wrapper).stream().map(this::toMessageDTO).collect(Collectors.toList());
    }

    public void delete(Long sessionId) {
        requireSessionOwner(sessionId);
        sessionMapper.deleteById(sessionId);
        messageMapper.delete(new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getSessionId, sessionId));
    }

    public void append(Long sessionId, String role, String content, List<ChatSourceDTO> sources) {
        if (sessionId == null) {
            return;
        }
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setDeleted(false);
        message.setCreatedAt(LocalDateTime.now());
        try {
            message.setSources(sources == null ? null : objectMapper.writeValueAsString(sources));
        } catch (Exception ignored) {
            message.setSources(null);
        }
        messageMapper.insert(message);

        ChatSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
            session.setUpdatedAt(LocalDateTime.now());
            sessionMapper.updateById(session);
        }
    }

    private void requireSessionOwner(Long sessionId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(accessService.currentUserId())) {
            throw new BusinessException(403, "无权访问该会话");
        }
    }

    private ChatSessionDTO toSessionDTO(ChatSession session) {
        ChatSessionDTO dto = new ChatSessionDTO();
        dto.setId(String.valueOf(session.getId()));
        dto.setKnowledgeBaseId(String.valueOf(session.getKbId()));
        dto.setTitle(session.getTitle());
        dto.setCreatedAt(session.getCreatedAt());
        dto.setUpdatedAt(session.getUpdatedAt());
        return dto;
    }

    private ChatMessageDTO toMessageDTO(ChatMessage message) {
        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setId(String.valueOf(message.getId()));
        dto.setRole(message.getRole());
        dto.setContent(message.getContent());
        dto.setCreatedAt(message.getCreatedAt());
        if (message.getSources() != null) {
            try {
                dto.setSources(objectMapper.readValue(message.getSources(), new TypeReference<>() {}));
            } catch (Exception ignored) {
                dto.setSources(List.of());
            }
        }
        return dto;
    }
}
