package com.agentkb.task.service;

import com.agentkb.common.BusinessException;
import com.agentkb.document.entity.Document;
import com.agentkb.document.entity.DocumentChunk;
import com.agentkb.document.entity.DocumentElement;
import com.agentkb.document.mapper.DocumentChunkMapper;
import com.agentkb.document.mapper.DocumentElementMapper;
import com.agentkb.document.mapper.DocumentMapper;
import com.agentkb.search.service.IndexingService;
import com.agentkb.storage.MinioStorageService;
import com.agentkb.task.entity.ParseTask;
import com.agentkb.task.mapper.ParseTaskMapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 解析结果回调与落库：从 MinIO 下载结果 JSON，写入元素与分块表。
 */
@Service
public class ParseResultService {

    private static final Logger log = LoggerFactory.getLogger(ParseResultService.class);

    private final ParseTaskMapper taskMapper;
    private final DocumentMapper documentMapper;
    private final DocumentElementMapper elementMapper;
    private final DocumentChunkMapper chunkMapper;
    private final MinioStorageService minioStorageService;
    private final ObjectMapper objectMapper;
    private final IndexingService indexingService;

    public ParseResultService(ParseTaskMapper taskMapper,
                              DocumentMapper documentMapper,
                              DocumentElementMapper elementMapper,
                              DocumentChunkMapper chunkMapper,
                              MinioStorageService minioStorageService,
                              ObjectMapper objectMapper,
                              IndexingService indexingService) {
        this.taskMapper = taskMapper;
        this.documentMapper = documentMapper;
        this.elementMapper = elementMapper;
        this.chunkMapper = chunkMapper;
        this.minioStorageService = minioStorageService;
        this.objectMapper = objectMapper;
        this.indexingService = indexingService;
    }

    /** 处理 Worker 回调；仅 COMPLETED 时下载并落库解析结果。 */
    public void handleCallback(Long taskId, Map<String, Object> payload) {
        ParseTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw BusinessException.notFound("解析任务不存在");
        }

        String status = payload.get("status") == null ? null : String.valueOf(payload.get("status"));
        if (status == null || status.isBlank()) {
            throw BusinessException.badRequest("回调缺少 status");
        }

        Object progress = payload.get("progress");
        if (progress instanceof Number number) {
            task.setProgress(number.intValue());
        }
        Object message = payload.get("message");
        task.setErrorMessage(message == null ? null : String.valueOf(message));
        Object errorCode = payload.get("error_code");
        task.setErrorCode(errorCode == null ? null : String.valueOf(errorCode));

        if ("PROCESSING".equals(status)) {
            task.setStatus(status);
            if (task.getStartedAt() == null) {
                task.setStartedAt(LocalDateTime.now());
            }
            taskMapper.updateById(task);
            return;
        }

        if ("COMPLETED".equals(status)) {
            String resultKey = payload.get("result_key") == null ? null : String.valueOf(payload.get("result_key"));
            if (resultKey == null || resultKey.isBlank()) {
                throw BusinessException.badRequest("完成回调缺少 result_key");
            }
            persistResult(task, resultKey);
            task.setResultKey(resultKey);
            task.setProgress(100);
            task.setErrorMessage(null);
        }

        task.setStatus(status);
        if ("FAILED".equals(status) || "CANCELLED".equals(status) || "COMPLETED".equals(status)) {
            task.setFinishedAt(LocalDateTime.now());
        }

        Document document = documentMapper.selectById(task.getDocumentId());
        if (document != null) {
            document.setStatus(status);
            documentMapper.updateById(document);
        }

        Object summary = payload.get("result_summary");
        if (summary != null) {
            try {
                task.setResultSummary(objectMapper.writeValueAsString(summary));
            } catch (Exception e) {
                log.warn("序列化任务摘要失败: taskId={}, error={}", taskId, e.getMessage());
            }
        }
        taskMapper.updateById(task);
    }

    private void persistResult(ParseTask task, String resultKey) {
        JsonNode root;
        try {
            byte[] bytes = minioStorageService.download(resultKey);
            root = objectMapper.readTree(bytes);
        } catch (Exception e) {
            throw new BusinessException(500, "下载或解析解析结果失败: " + e.getMessage());
        }

        JsonNode documentNode = root.hasNonNull("document") ? root.get("document") : root;
        JsonNode docIdNode = documentNode.hasNonNull("document_id")
                ? documentNode.get("document_id") : root.get("document_id");
        if (docIdNode == null || !docIdNode.asText().equals(String.valueOf(task.getDocumentId()))) {
            throw BusinessException.badRequest("解析结果与任务 document_id 不一致");
        }

        Document document = documentMapper.selectById(task.getDocumentId());
        if (document == null) {
            throw BusinessException.notFound("文档不存在");
        }
        long version = document.getVersion() == null ? 1L : document.getVersion().longValue();

        elementMapper.update(null, Wrappers.<DocumentElement>lambdaUpdate()
                .eq(DocumentElement::getDocumentId, task.getDocumentId())
                .eq(DocumentElement::getVersionId, version)
                .set(DocumentElement::getDeleted, true));
        chunkMapper.update(null, Wrappers.<DocumentChunk>lambdaUpdate()
                .eq(DocumentChunk::getDocumentId, task.getDocumentId())
                .eq(DocumentChunk::getVersionId, version)
                .set(DocumentChunk::getDeleted, true));

        int elementCount = 0;
        JsonNode elements = documentNode.path("elements");
        if (elements.isArray()) {
            for (JsonNode node : elements) {
                elementMapper.insert(toElement(node, document, task, version));
                elementCount++;
            }
        }

        int chunkCount = 0;
        List<DocumentChunk> indexedChunks = new ArrayList<>();
        JsonNode chunkNodes = documentNode.path("chunks");
        if (chunkNodes.isArray()) {
            for (JsonNode node : chunkNodes) {
                DocumentChunk chunk = toChunk(node, document, task, version);
                chunkMapper.insert(chunk);
                indexedChunks.add(chunk);
                chunkCount++;
            }
        }
        log.info("解析结果已落库: taskId={}, documentId={}, elements={}, chunks={}",
                task.getId(), task.getDocumentId(), elementCount, chunkCount);
        try {
            indexingService.index(indexedChunks);
        } catch (Exception e) {
            log.warn("索引阶段失败（不阻断解析任务）: taskId={}, error={}", task.getId(), e.getMessage());
        }
    }

    private DocumentElement toElement(JsonNode node, Document document, ParseTask task, long version) {
        DocumentElement element = new DocumentElement();
        element.setId(IdWorker.getId());
        element.setDocumentId(document.getId());
        element.setVersionId(version);
        element.setParseTaskId(task.getId());
        element.setElementId(text(node, "element_id", IdWorker.getIdStr()));
        element.setType(text(node, "type", "text"));
        element.setContent(text(node, "content", ""));
        element.setLevel(intOrNull(node.get("level")));
        element.setPage(intOrNull(node.get("page")));
        element.setPageStart(intOrNull(firstNotNull(node.get("page_start"), node.get("page"))));
        element.setPageEnd(intOrNull(firstNotNull(node.get("page_end"), node.get("page"))));
        element.setHeadingPath(headingPath(node.get("heading_path")));
        element.setTableData(jsonOrNull(node.get("table_data")));
        element.setBoundingBox(jsonOrNull(node.get("bounding_box")));
        element.setMetadata(jsonOrNull(node.get("metadata")));
        element.setSourceFileId(textOrNull(node, "source_file_id"));
        element.setCreatedAt(LocalDateTime.now());
        element.setDeleted(false);
        return element;
    }

    private DocumentChunk toChunk(JsonNode node, Document document, ParseTask task, long version) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(IdWorker.getId());
        chunk.setDocumentId(document.getId());
        chunk.setKbId(task.getKbId());
        chunk.setVersionId(version);
        chunk.setParseTaskId(task.getId());
        chunk.setParentChunkId(textOrNull(node, "parent_chunk_id"));
        String firstElement = null;
        JsonNode ids = node.get("element_ids");
        if (ids != null && ids.isArray() && ids.size() > 0) {
            firstElement = ids.get(0).asText();
        }
        chunk.setElementId(firstElement == null ? textOrNull(node, "element_id") : firstElement);
        chunk.setElementIds(ids == null ? "[]" : ids.toString());
        chunk.setChunkIndex(intOrNull(node.get("chunk_index")));
        chunk.setChunkType(text(node, "chunk_type", "child"));
        chunk.setContent(text(node, "content", ""));
        chunk.setTokenCount(intOrNull(node.get("token_count")));
        chunk.setHeadingPath(headingPath(node.get("heading_path")));
        chunk.setPageStart(intOrNull(firstNotNull(node.get("page_start"), node.get("page"))));
        chunk.setPageEnd(intOrNull(firstNotNull(node.get("page_end"), node.get("page"))));
        chunk.setMetadata(jsonOrNull(node.get("metadata")));
        chunk.setEmbedding(doubleListOrNull(node.get("embedding")));
        chunk.setIndexed(false);
        chunk.setCreatedAt(LocalDateTime.now());
        chunk.setUpdatedAt(LocalDateTime.now());
        chunk.setDeleted(false);
        return chunk;
    }

    private String text(JsonNode node, String field, String defaultVal) {
        if (node == null || !node.hasNonNull(field)) {
            return defaultVal;
        }
        return node.get(field).asText(defaultVal);
    }

    private String textOrNull(JsonNode node, String field) {
        return text(node, field, null);
    }

    private Integer intOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asInt();
    }

    private List<Double> doubleListOrNull(JsonNode node) {
        if (node == null || !node.isArray()) {
            return null;
        }
        List<Double> values = new ArrayList<>();
        for (JsonNode item : node) {
            values.add(item.asDouble());
        }
        return values;
    }

    private JsonNode firstNotNull(JsonNode a, JsonNode b) {
        return a != null && !a.isNull() ? a : b;
    }

    private String jsonOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.toString();
    }

    private String headingPath(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : node) {
                if (sb.length() > 0) {
                    sb.append(" / ");
                }
                sb.append(item.asText(""));
            }
            return sb.toString();
        }
        return node.asText("");
    }
}
