package com.agentkb.security.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限判定所需的底层查询：用户角色、显式授权、可访问知识库 ID。
 */
@Mapper
public interface AccessMapper {

    @Select("SELECT r.role_code FROM sys_role r " +
            "JOIN sys_user_role ur ON ur.role_id = r.id " +
            "WHERE ur.user_id = #{userId} AND ur.deleted = FALSE AND r.deleted = FALSE")
    List<String> selectRoleCodes(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM kb_permission " +
            "WHERE granted = TRUE AND subject_type = 'USER' AND subject_id = #{userId} " +
            "AND permission = #{permission} AND (kb_id = #{kbId} OR kb_id IS NULL)")
    int countUserGrant(@Param("userId") Long userId,
                       @Param("kbId") Long kbId,
                       @Param("permission") String permission);

    @Select("SELECT COUNT(*) FROM kb_permission p " +
            "JOIN sys_role r ON r.id = p.subject_id " +
            "WHERE p.granted = TRUE AND p.subject_type = 'ROLE' AND r.role_code = #{roleCode} " +
            "AND r.deleted = FALSE AND p.permission = #{permission} AND (p.kb_id = #{kbId} OR p.kb_id IS NULL)")
    int countRoleGrant(@Param("roleCode") String roleCode,
                       @Param("kbId") Long kbId,
                       @Param("permission") String permission);

    @Select("SELECT COUNT(*) FROM kb_permission " +
            "WHERE granted = TRUE AND permission = 'READ' AND kb_id IS NULL " +
            "AND ((subject_type = 'USER' AND subject_id = #{userId}) " +
            "OR (subject_type = 'ROLE' AND subject_id IN " +
            "(SELECT role_id FROM sys_user_role WHERE user_id = #{userId} AND deleted = FALSE)))")
    int countGlobalReadGrant(@Param("userId") Long userId);

    @Select("SELECT id FROM kb_knowledge_base WHERE deleted = FALSE AND owner_id = #{userId} " +
            "UNION " +
            "SELECT kb_id FROM kb_permission " +
            "WHERE granted = TRUE AND subject_type = 'USER' AND subject_id = #{userId} " +
            "AND permission = 'READ' AND kb_id IS NOT NULL " +
            "UNION " +
            "SELECT p.kb_id FROM kb_permission p " +
            "JOIN sys_user_role ur ON p.subject_type = 'ROLE' AND p.subject_id = ur.role_id " +
            "WHERE p.granted = TRUE AND ur.user_id = #{userId} AND ur.deleted = FALSE " +
            "AND p.permission = 'READ' AND p.kb_id IS NOT NULL")
    List<Long> selectReadableKbIds(@Param("userId") Long userId);
}
