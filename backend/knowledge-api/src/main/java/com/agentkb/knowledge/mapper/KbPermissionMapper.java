package com.agentkb.knowledge.mapper;

import com.agentkb.knowledge.entity.KbPermission;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 知识库授权 Mapper。
 */
@Mapper
public interface KbPermissionMapper extends BaseMapper<KbPermission> {

    @Select("SELECT * FROM kb_permission WHERE kb_id = #{kbId} ORDER BY created_at DESC")
    List<KbPermission> selectByKbId(@Param("kbId") Long kbId);

    /**
     * PostgreSQL 唯一约束冲突时更新 granted。kb_id 为 NULL 表示全局授权。
     */
    @Insert("INSERT INTO kb_permission (id, subject_type, subject_id, kb_id, permission, granted) " +
            "VALUES (#{id}, #{subjectType}, #{subjectId}, #{kbId}, #{permission}, #{granted}) " +
            "ON CONFLICT (subject_type, subject_id, kb_id, permission) WHERE deleted = FALSE " +
            "DO UPDATE SET granted = EXCLUDED.granted")
    int upsert(KbPermission permission);

    @Delete("DELETE FROM kb_permission WHERE kb_id = #{kbId} AND subject_type = #{subjectType} " +
            "AND subject_id = #{subjectId} AND permission = #{permission}")
    int revoke(@Param("kbId") Long kbId,
               @Param("subjectType") String subjectType,
               @Param("subjectId") Long subjectId,
               @Param("permission") String permission);
}
