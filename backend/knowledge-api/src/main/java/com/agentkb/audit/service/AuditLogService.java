package com.agentkb.audit.service;

import com.agentkb.audit.dto.AuditLogDTO;
import com.agentkb.audit.entity.AccessLog;
import com.agentkb.audit.mapper.AccessLogMapper;
import com.agentkb.auth.entity.SysUser;
import com.agentkb.auth.mapper.SysUserMapper;
import com.agentkb.common.PageResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Map;

/** 审计日志写入与查询。 */
@Service
public class AuditLogService {

    private final AccessLogMapper logMapper;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;

    public AuditLogService(AccessLogMapper logMapper,
                           SysUserMapper userMapper,
                           ObjectMapper objectMapper) {
        this.logMapper = logMapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    public void record(AccessLog log) {
        try {
            logMapper.insertLog(log);
        } catch (Exception ignored) {
            // 审计失败不应影响业务请求
        }
    }

    public PageResult<AuditLogDTO> page(String keyword, String resourceType, Boolean success, long current, long size) {
        LambdaQueryWrapper<AccessLog> wrapper = Wrappers.<AccessLog>lambdaQuery();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(AccessLog::getOperation, keyword);
        }
        if (resourceType != null && !resourceType.isBlank()) {
            wrapper.eq(AccessLog::getResourceType, resourceType);
        }
        if (success != null) {
            wrapper.eq(AccessLog::getSuccess, success);
        }
        wrapper.orderByDesc(AccessLog::getCreatedAt);
        Page<AccessLog> page = logMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page, page.getRecords().stream().map(this::toDTO).toList());
    }

    private AuditLogDTO toDTO(AccessLog log) {
        AuditLogDTO dto = new AuditLogDTO();
        dto.setId(String.valueOf(log.getId()));
        dto.setUserId(log.getUserId() == null ? null : String.valueOf(log.getUserId()));
        if (log.getUserId() != null) {
            SysUser user = userMapper.selectById(log.getUserId());
            dto.setUsername(user == null ? null : user.getUsername());
        }
        dto.setOperation(log.getOperation());
        dto.setResourceType(log.getResourceType());
        dto.setResourceId(log.getResourceId());
        dto.setRequestIp(log.getRequestIp());
        dto.setSuccess(log.getSuccess());
        dto.setCreatedAt(log.getCreatedAt());
        if (log.getDetail() != null) {
            try {
                dto.setDetail(objectMapper.readValue(log.getDetail(), new TypeReference<>() {}));
            } catch (Exception ignored) {
                dto.setDetail(Map.of("raw", log.getDetail()));
            }
        }
        return dto;
    }
}
