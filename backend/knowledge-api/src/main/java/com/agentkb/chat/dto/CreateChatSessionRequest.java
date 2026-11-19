package com.agentkb.chat.dto;

import jakarta.validation.constraints.NotNull;

/** 创建会话请求。 */
public class CreateChatSessionRequest {

    @NotNull(message = "knowledgeBaseId 不能为空")
    private Long knowledgeBaseId;

    private String title;

    public Long getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(Long knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
