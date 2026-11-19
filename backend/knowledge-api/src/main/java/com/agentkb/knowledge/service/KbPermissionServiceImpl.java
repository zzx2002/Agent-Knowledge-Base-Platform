package com.agentkb.knowledge.service;

import com.agentkb.common.BusinessException;
import com.agentkb.knowledge.dto.KbPermissionRequest;
import com.agentkb.knowledge.entity.KbPermission;
import com.agentkb.knowledge.mapper.KbPermissionMapper;
import com.agentkb.security.KbAccessService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 知识库授权管理实现。仅知识库 owner 或全局管理员可管理授权。
 */
@Service
public class KbPermissionServiceImpl implements KbPermissionService {

    private static final Set<String> SUBJECT_TYPES = Set.of("USER", "ROLE");
    private static final Set<String> PERMISSIONS = Set.of(
            KbAccessService.PERM_READ, KbAccessService.PERM_WRITE,
            KbAccessService.PERM_DELETE, KbAccessService.PERM_ADMIN);

    private final KbPermissionMapper permissionMapper;
    private final KbAccessService accessService;

    public KbPermissionServiceImpl(KbPermissionMapper permissionMapper, KbAccessService accessService) {
        this.permissionMapper = permissionMapper;
        this.accessService = accessService;
    }

    @Override
    public List<KbPermission> list(Long kbId) {
        accessService.require(kbId, KbAccessService.PERM_ADMIN);
        return permissionMapper.selectByKbId(kbId);
    }

    @Override
    public KbPermission grant(Long kbId, KbPermissionRequest request) {
        accessService.require(kbId, KbAccessService.PERM_ADMIN);
        validate(request.getSubjectType(), request.getPermission());

        KbPermission entity = new KbPermission();
        entity.setId(IdWorker.getId());
        entity.setKbId(kbId);
        entity.setSubjectType(request.getSubjectType().toUpperCase());
        entity.setSubjectId(request.getSubjectId());
        entity.setPermission(request.getPermission().toUpperCase());
        entity.setGranted(request.getGranted() == null || request.getGranted());
        permissionMapper.upsert(entity);
        return entity;
    }

    @Override
    public void revoke(Long kbId, String subjectType, Long subjectId, String permission) {
        accessService.require(kbId, KbAccessService.PERM_ADMIN);
        validate(subjectType, permission);
        permissionMapper.revoke(kbId, subjectType.toUpperCase(), subjectId, permission.toUpperCase());
    }

    private void validate(String subjectType, String permission) {
        if (subjectType == null || !SUBJECT_TYPES.contains(subjectType.toUpperCase())) {
            throw BusinessException.badRequest("subjectType 仅支持 USER 或 ROLE");
        }
        if (permission == null || !PERMISSIONS.contains(permission.toUpperCase())) {
            throw BusinessException.badRequest("permission 仅支持 READ / WRITE / DELETE / ADMIN");
        }
    }
}
