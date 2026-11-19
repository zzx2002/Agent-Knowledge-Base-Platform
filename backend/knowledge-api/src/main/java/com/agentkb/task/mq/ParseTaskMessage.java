package com.agentkb.task.mq;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

/**
 * 解析任务消息体，Java API 投递给 Python Parser Worker。
 */
public class ParseTaskMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("task_id")
    private Long taskId;

    @JsonProperty("document_id")
    private Long documentId;

    @JsonProperty("knowledge_base_id")
    private Long knowledgeBaseId;

    @JsonProperty("storage_key")
    private String storageKey;

    @JsonProperty("file_name")
    private String fileName;

    @JsonProperty("file_type")
    private String fileType;

    public ParseTaskMessage() {
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    @Override
    public String toString() {
        return "ParseTaskMessage{taskId=" + taskId
                + ", documentId=" + documentId
                + ", knowledgeBaseId=" + knowledgeBaseId
                + ", fileName='" + fileName + '\''
                + ", fileType='" + fileType + '\''
                + '}';
    }
}
