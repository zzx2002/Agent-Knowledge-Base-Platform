package com.agentkb.common.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 级联逻辑删除：删除文档/知识库时，同步标记其关联数据。
 * 例外：权限授权记录与 MinIO 对象按约定允许物理删除。
 */
@Mapper
public interface LogicalDeleteMapper {

    @Update("UPDATE kb_parse_task SET deleted = TRUE WHERE document_id = #{documentId} AND deleted = FALSE")
    int deleteTasksByDocumentId(@Param("documentId") Long documentId);

    @Update("UPDATE kb_document_version SET deleted = TRUE WHERE document_id = #{documentId} AND deleted = FALSE")
    int deleteVersionsByDocumentId(@Param("documentId") Long documentId);

    @Update("UPDATE kb_document_element SET deleted = TRUE WHERE document_id = #{documentId} AND deleted = FALSE")
    int deleteElementsByDocumentId(@Param("documentId") Long documentId);

    @Update("UPDATE kb_chunk SET deleted = TRUE WHERE document_id = #{documentId} AND deleted = FALSE")
    int deleteChunksByDocumentId(@Param("documentId") Long documentId);

    @Update("UPDATE kb_parse_task SET deleted = TRUE WHERE kb_id = #{kbId} AND deleted = FALSE")
    int deleteTasksByKbId(@Param("kbId") Long kbId);

    @Update("UPDATE kb_chunk SET deleted = TRUE WHERE kb_id = #{kbId} AND deleted = FALSE")
    int deleteChunksByKbId(@Param("kbId") Long kbId);

    @Update("UPDATE kb_document_element SET deleted = TRUE " +
            "WHERE document_id IN (SELECT id FROM kb_document WHERE kb_id = #{kbId}) AND deleted = FALSE")
    int deleteElementsByKbId(@Param("kbId") Long kbId);

    @Update("UPDATE kb_document_version SET deleted = TRUE " +
            "WHERE document_id IN (SELECT id FROM kb_document WHERE kb_id = #{kbId}) AND deleted = FALSE")
    int deleteVersionsByKbId(@Param("kbId") Long kbId);

    @Update("UPDATE kb_document SET deleted = TRUE WHERE kb_id = #{kbId} AND deleted = FALSE")
    int deleteDocumentsByKbId(@Param("kbId") Long kbId);

    /** 权限记录允许物理删除。 */
    @Delete("DELETE FROM kb_permission WHERE kb_id = #{kbId}")
    int deletePermissionsByKbId(@Param("kbId") Long kbId);
}
