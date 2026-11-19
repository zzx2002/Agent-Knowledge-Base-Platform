package com.agentkb.chat.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 历史消息。 */
public class ChatMessageDTO {

    private String id;
    private String role;
    private String content;
    private List<ChatSourceDTO> sources;
    private LocalDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public List<ChatSourceDTO> getSources() { return sources; }
    public void setSources(List<ChatSourceDTO> sources) { this.sources = sources; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
