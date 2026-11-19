package com.agentkb.knowledge.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 知识库授权请求。
 */
public class KbPermissionRequest {

    @JsonProperty("subject_type")
    @NotBlank(message = "subjectType 不能为空")
    private String subjectType;

    @JsonProperty("subject_id")
    @NotNull(message = "subjectId 不能为空")
    private Long subjectId;

    @NotBlank(message = "permission 不能为空")
    private String permission;

    private Boolean granted = true;

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
}
