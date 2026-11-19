package com.agentkb.knowledge.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 知识库授权记录，对应 kb_permission 表。
 * subject_type：USER / ROLE；permission：READ / WRITE / DELETE / ADMIN。
 */
@TableName("kb_permission")
public class KbPermission {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @JsonProperty("subject_type")
    @TableField("subject_type")
    private String subjectType;

    @JsonProperty("subject_id")
    @TableField("subject_id")
    private Long subjectId;

    @JsonProperty("kb_id")
    @TableField("kb_id")
    private Long kbId;

    private String permission;

    private Boolean granted;

    @JsonProperty("created_at")
    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public Long getKbId() {
        return kbId;
    }

    public void setKbId(Long kbId) {
        this.kbId = kbId;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public Boolean getGranted() {
        return granted;
    }

    public void setGranted(Boolean granted) {
        this.granted = granted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
