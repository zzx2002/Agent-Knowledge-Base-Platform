package com.agentkb.search.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 混合检索请求（接口定义，第一阶段尚未实现检索能力）。
 */
public class SearchRequest {

    private Long knowledgeBaseId;

    @NotBlank(message = "query 不能为空")
    private String query;

    private Integer topK;

    private Boolean rerank;

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public Boolean getRerank() {
        return rerank;
    }

    public void setRerank(Boolean rerank) {
        this.rerank = rerank;
    }
}
