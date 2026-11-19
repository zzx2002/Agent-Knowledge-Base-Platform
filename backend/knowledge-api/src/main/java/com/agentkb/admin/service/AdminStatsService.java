package com.agentkb.admin.service;

import com.agentkb.admin.mapper.AdminStatsMapper;
import com.agentkb.document.entity.Document;
import com.agentkb.document.mapper.DocumentMapper;
import com.agentkb.knowledge.entity.KnowledgeBase;
import com.agentkb.knowledge.mapper.KnowledgeBaseMapper;
import com.agentkb.task.entity.ParseTask;
import com.agentkb.task.mapper.ParseTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 数据面板真实统计。 */
@Service
public class AdminStatsService {

    private final AdminStatsMapper statsMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final DocumentMapper documentMapper;
    private final ParseTaskMapper taskMapper;

    public AdminStatsService(AdminStatsMapper statsMapper,
                             KnowledgeBaseMapper kbMapper,
                             DocumentMapper documentMapper,
                             ParseTaskMapper taskMapper) {
        this.statsMapper = statsMapper;
        this.kbMapper = kbMapper;
        this.documentMapper = documentMapper;
        this.taskMapper = taskMapper;
    }

    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();

        long kbCount = count(kbMapper.selectCount(Wrappers.<KnowledgeBase>lambdaQuery()));
        long documentCount = count(documentMapper.selectCount(Wrappers.<Document>lambdaQuery()));
        long taskCount = count(taskMapper.selectCount(Wrappers.<ParseTask>lambdaQuery()));
        long completed = count(taskMapper.selectCount(Wrappers.<ParseTask>lambdaQuery()
                .eq(ParseTask::getStatus, "COMPLETED")));
        long failed = count(taskMapper.selectCount(Wrappers.<ParseTask>lambdaQuery()
                .eq(ParseTask::getStatus, "FAILED")));
        double successRate = completed + failed == 0 ? 0 : completed * 100.0 / (completed + failed);

        Map<String, Object> calls = statsMapper.callStats();
        Map<String, Object> storage = statsMapper.storageStats();

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("kbCount", kbCount);
        overview.put("documentCount", documentCount);
        overview.put("storageBytes", num(storage.get("total_size")));
        overview.put("taskCount", taskCount);
        overview.put("successRate", Math.round(successRate * 10) / 10.0);
        overview.put("searchCalls", num(calls.get("search_calls")));
        overview.put("chatCalls", num(calls.get("chat_calls")));
        result.put("overview", overview);

        result.put("trend", buildTrend());
        result.put("documentTypes", statsMapper.documentTypeDistribution());
        result.put("recentTasks", recentTasks());
        result.put("recentKnowledgeBases", recentKnowledgeBases());
        return result;
    }

    private List<Map<String, Object>> buildTrend() {
        Map<String, Map<String, Object>> actual = new LinkedHashMap<>();
        for (Map<String, Object> row : statsMapper.dailyTrend()) {
            actual.put(String.valueOf(row.get("day")), row);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        for (int i = 6; i >= 0; i--) {
            String day = LocalDate.now().minusDays(i).format(formatter);
            Map<String, Object> row = actual.get(day);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("day", day);
            item.put("search", row == null ? 0 : num(row.get("search_count")));
            item.put("chat", row == null ? 0 : num(row.get("chat_count")));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> recentTasks() {
        Page<ParseTask> page = taskMapper.selectPage(new Page<>(1, 5),
                Wrappers.<ParseTask>lambdaQuery().orderByDesc(ParseTask::getCreatedAt));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ParseTask task : page.getRecords()) {
            Document doc = task.getDocumentId() == null ? null : documentMapper.selectById(task.getDocumentId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(task.getId()));
            item.put("fileName", doc == null ? "已删除文档" : doc.getFileName());
            item.put("status", task.getStatus());
            item.put("progress", task.getProgress());
            item.put("errorCode", task.getErrorCode());
            item.put("createdAt", task.getCreatedAt());
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> recentKnowledgeBases() {
        Page<KnowledgeBase> page = kbMapper.selectPage(new Page<>(1, 5),
                Wrappers.<KnowledgeBase>lambdaQuery().orderByDesc(KnowledgeBase::getUpdatedAt));
        List<Map<String, Object>> result = new ArrayList<>();
        for (KnowledgeBase kb : page.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(kb.getId()));
            item.put("name", kb.getName());
            item.put("embeddingModel", kb.getEmbeddingModel());
            item.put("status", kb.getStatus());
            item.put("updatedAt", kb.getUpdatedAt());
            result.add(item);
        }
        return result;
    }

    private long count(Long value) {
        return value == null ? 0L : value;
    }

    private long num(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
