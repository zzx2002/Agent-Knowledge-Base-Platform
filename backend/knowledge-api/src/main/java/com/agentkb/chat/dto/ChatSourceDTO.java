package com.agentkb.chat.dto;

/**
 * 问答来源引用。
 */
public class ChatSourceDTO {

    private String chunkId;
    private String documentId;
    private String fileName;
    private Integer page;
    private String content;
    private double score;

    public String getChunkId() { return chunkId; }
    public void setChunkId(String chunkId) { this.chunkId = chunkId; }
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
}
