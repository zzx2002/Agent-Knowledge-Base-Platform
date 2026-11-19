package com.agentkb.audit.mapper;

import com.agentkb.audit.entity.AccessLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccessLogMapper extends BaseMapper<AccessLog> {

    @Insert("INSERT INTO kb_access_log (id, user_id, agent_client_id, operation, resource_type, " +
            "resource_id, request_ip, user_agent, success, detail, created_at) " +
            "VALUES (#{log.id}, #{log.userId}, #{log.agentClientId}, #{log.operation}, #{log.resourceType}, " +
            "#{log.resourceId}, #{log.requestIp}, #{log.userAgent}, #{log.success}, " +
            "CAST(#{log.detail} AS jsonb), CURRENT_TIMESTAMP)")
    int insertLog(@Param("log") AccessLog log);
}
