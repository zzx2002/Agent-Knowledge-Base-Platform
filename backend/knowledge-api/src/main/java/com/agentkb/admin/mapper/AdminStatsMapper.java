package com.agentkb.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** 数据面板聚合查询。 */
@Mapper
public interface AdminStatsMapper {

    @Select("SELECT COUNT(*) AS total, " +
            "COUNT(*) FILTER (WHERE operation LIKE '%/search%') AS search_calls, " +
            "COUNT(*) FILTER (WHERE operation LIKE '%/chat%') AS chat_calls " +
            "FROM kb_access_log WHERE created_at >= CURRENT_DATE - INTERVAL '30 days'")
    Map<String, Object> callStats();

    @Select("SELECT to_char(date_trunc('day', created_at), 'MM-DD') AS day, " +
            "COUNT(*) FILTER (WHERE operation LIKE '%/search%') AS search_count, " +
            "COUNT(*) FILTER (WHERE operation LIKE '%/chat%') AS chat_count " +
            "FROM kb_access_log WHERE created_at >= CURRENT_DATE - INTERVAL '6 days' " +
            "GROUP BY date_trunc('day', created_at) ORDER BY date_trunc('day', created_at)")
    List<Map<String, Object>> dailyTrend();

    @Select("SELECT COALESCE(UPPER(file_type), 'OTHER') AS name, COUNT(*) AS value " +
            "FROM kb_document WHERE deleted = false GROUP BY COALESCE(UPPER(file_type), 'OTHER') " +
            "ORDER BY value DESC")
    List<Map<String, Object>> documentTypeDistribution();

    @Select("SELECT COALESCE(SUM(file_size), 0) AS total_size FROM kb_document WHERE deleted = false")
    Map<String, Object> storageStats();
}
