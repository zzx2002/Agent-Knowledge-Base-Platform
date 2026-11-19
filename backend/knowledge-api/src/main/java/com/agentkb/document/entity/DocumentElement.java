package com.agentkb.document.entity;

import com.agentkb.common.JsonbStringTypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 文档结构化元素，对应 kb_document_element 表。
 */
@TableName("kb_document_element")
public class DocumentElement {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("document_id")
    private Long documentId;

    @TableField("version_id")
    private Long versionId;

    @TableField("parse_task_id")
    private Long parseTaskId;

    @TableField("element_id")
    private String elementId;

    private String type;

    private String content;

    private Integer level;

    private Integer page;

    @TableField("page_start")
    private Integer pageStart;

    @TableField("page_end")
    private Integer pageEnd;

    @TableField("heading_path")
    private String headingPath;

    @TableField(value = "table_data", typeHandler = JsonbStringTypeHandler.class)
    private String tableData;

    @TableField(value = "bounding_box", typeHandler = JsonbStringTypeHandler.class)
    private String boundingBox;

    @TableField(value = "metadata", typeHandler = JsonbStringTypeHandler.class)
    private String metadata;

    @TableField("source_file_id")
    private String sourceFileId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableLogic
    private Boolean deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public Long getParseTaskId() { return parseTaskId; }
    public void setParseTaskId(Long parseTaskId) { this.parseTaskId = parseTaskId; }
    public String getElementId() { return elementId; }
    public void setElementId(String elementId) { this.elementId = elementId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getPageStart() { return pageStart; }
    public void setPageStart(Integer pageStart) { this.pageStart = pageStart; }
    public Integer getPageEnd() { return pageEnd; }
    public void setPageEnd(Integer pageEnd) { this.pageEnd = pageEnd; }
    public String getHeadingPath() { return headingPath; }
    public void setHeadingPath(String headingPath) { this.headingPath = headingPath; }
    public String getTableData() { return tableData; }
    public void setTableData(String tableData) { this.tableData = tableData; }
    public String getBoundingBox() { return boundingBox; }
    public void setBoundingBox(String boundingBox) { this.boundingBox = boundingBox; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public String getSourceFileId() { return sourceFileId; }
    public void setSourceFileId(String sourceFileId) { this.sourceFileId = sourceFileId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
}
