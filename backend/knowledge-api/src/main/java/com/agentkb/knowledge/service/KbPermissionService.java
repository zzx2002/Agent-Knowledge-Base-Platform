package com.agentkb.knowledge.service;

import com.agentkb.knowledge.dto.KbPermissionRequest;
import com.agentkb.knowledge.entity.KbPermission;

import java.util.List;

/**
 * 知识库显式授权管理。
 */
public interface KbPermissionService {

    List<KbPermission> list(Long kbId);

    KbPermission grant(Long kbId, KbPermissionRequest request);

    void revoke(Long kbId, String subjectType, Long subjectId, String permission);
}
