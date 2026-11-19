package com.agentkb.search.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * RAG 问答请求（接口定义，第四阶段实现）。
 */
public class ChatRequest {

    private Long knowledgeBaseId;

    @NotBlank(message = "question 不能为空")
    private String question;

    private Integer topK;

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }
}
