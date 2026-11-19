package com.agentkb.search.dto;

import java.util.Map;

/**
 * 检索命中结果。
 */
public class SearchHitDTO {

    private String chunkId;
    private String documentId;
    private String kbId;
    private String fileName;
    private String content;
    private String headingPath;
    private Integer page;
    private double score;
    private Double keywordScore;
    private Double vectorScore;
    private Double rerankScore;
    private Map<String, Object> metadata;

    public String getChunkId() { return chunkId; }
    public void setChunkId(String chunkId) { this.chunkId = chunkId; }
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getKbId() { return kbId; }
    public void setKbId(String kbId) { this.kbId = kbId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getHeadingPath() { return headingPath; }
    public void setHeadingPath(String headingPath) { this.headingPath = headingPath; }
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    public Double getKeywordScore() { return keywordScore; }
    public void setKeywordScore(Double keywordScore) { this.keywordScore = keywordScore; }
    public Double getVectorScore() { return vectorScore; }
    public void setVectorScore(Double vectorScore) { this.vectorScore = vectorScore; }
    public Double getRerankScore() { return rerankScore; }
    public void setRerankScore(Double rerankScore) { this.rerankScore = rerankScore; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
