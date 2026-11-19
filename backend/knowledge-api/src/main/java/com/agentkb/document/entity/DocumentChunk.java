package com.agentkb.document.entity;

import com.agentkb.common.JsonbStringTypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 文档分块，对应 kb_chunk 表。
 */
@TableName("kb_chunk")
public class DocumentChunk {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("document_id")
    private Long documentId;

    @TableField("kb_id")
    private Long kbId;

    @TableField("version_id")
    private Long versionId;

    @TableField("parse_task_id")
    private Long parseTaskId;

    @TableField("parent_chunk_id")
    private String parentChunkId;

    @TableField("element_id")
    private String elementId;

    @TableField(value = "element_ids", typeHandler = JsonbStringTypeHandler.class)
    private String elementIds;

    @TableField("chunk_index")
    private Integer chunkIndex;

    @TableField("chunk_type")
    private String chunkType;

    private String content;

    @TableField("token_count")
    private Integer tokenCount;

    @TableField("heading_path")
    private String headingPath;

    @TableField("page_start")
    private Integer pageStart;

    @TableField("page_end")
    private Integer pageEnd;

    @TableField(value = "metadata", typeHandler = JsonbStringTypeHandler.class)
    private String metadata;

    @TableField("embedding_model")
    private String embeddingModel;

    private Boolean indexed;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableLogic
    private Boolean deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Long getKbId() { return kbId; }
    public void setKbId(Long kbId) { this.kbId = kbId; }
    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public Long getParseTaskId() { return parseTaskId; }
    public void setParseTaskId(Long parseTaskId) { this.parseTaskId = parseTaskId; }
    public String getParentChunkId() { return parentChunkId; }
    public void setParentChunkId(String parentChunkId) { this.parentChunkId = parentChunkId; }
    public String getElementId() { return elementId; }
    public void setElementId(String elementId) { this.elementId = elementId; }
    public String getElementIds() { return elementIds; }
    public void setElementIds(String elementIds) { this.elementIds = elementIds; }
    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getChunkType() { return chunkType; }
    public void setChunkType(String chunkType) { this.chunkType = chunkType; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getTokenCount() { return tokenCount; }
    public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }
    public String getHeadingPath() { return headingPath; }
    public void setHeadingPath(String headingPath) { this.headingPath = headingPath; }
    public Integer getPageStart() { return pageStart; }
    public void setPageStart(Integer pageStart) { this.pageStart = pageStart; }
    public Integer getPageEnd() { return pageEnd; }
    public void setPageEnd(Integer pageEnd) { this.pageEnd = pageEnd; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
    public Boolean getIndexed() { return indexed; }
    public void setIndexed(Boolean indexed) { this.indexed = indexed; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
}
