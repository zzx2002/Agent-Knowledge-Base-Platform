package com.agentkb.search.service;

import com.agentkb.document.entity.DocumentChunk;
import com.agentkb.document.mapper.DocumentChunkMapper;
import com.agentkb.search.config.SearchProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分块索引服务：把结构化分块写入 Elasticsearch 关键词索引与 Qdrant 向量集合。
 */
@Service
public class IndexingService {

    private static final Logger log = LoggerFactory.getLogger(IndexingService.class);

    private final SearchProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final DocumentChunkMapper chunkMapper;

    public IndexingService(SearchProperties properties,
                           @Qualifier("searchRestTemplate") RestTemplate restTemplate,
                           ObjectMapper objectMapper,
                           DocumentChunkMapper chunkMapper) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.chunkMapper = chunkMapper;
    }

    /** 索引一批分块。失败只记录日志，不影响解析回调。 */
    public void index(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        try {
            List<List<Double>> vectors = embed(chunks);
            ensureEsIndex();
            ensureQdrantCollection();
            indexToEs(chunks);
            if (!vectors.isEmpty()) {
                indexToQdrant(chunks, vectors);
            }
            markIndexed(chunks);
            log.info("分块索引完成: {} 条, 向量 {} 条", chunks.size(), vectors.size());
        } catch (Exception e) {
            log.error("分块索引失败: {}", e.getMessage(), e);
            markFailed(chunks, e.getMessage());
        }
    }

    /** 按知识库全量重建索引，用于切换 Embedding 模型后刷新存量向量。 */
    public int reindex(Long kbId) throws Exception {
        List<DocumentChunk> chunks = chunkMapper.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers
                .<DocumentChunk>lambdaQuery()
                .eq(DocumentChunk::getKbId, kbId)
                .eq(DocumentChunk::getDeleted, false));
        if (chunks.isEmpty()) {
            return 0;
        }
        List<List<Double>> vectors = embed(chunks, true);
        if (vectors.size() != chunks.size()) {
            throw new IllegalStateException("向量化数量与分块数量不一致");
        }
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).setEmbedding(vectors.get(i));
        }
        ensureEsIndex();
        ensureQdrantCollection();
        indexToEs(chunks);
        indexToQdrant(chunks, vectors);
        markIndexed(chunks);
        log.info("知识库 {} 重建索引完成: {} 条", kbId, chunks.size());
        return chunks.size();
    }

    private List<List<Double>> embed(List<DocumentChunk> chunks) throws Exception {
        return embed(chunks, false);
    }

    private List<List<Double>> embed(List<DocumentChunk> chunks, boolean force) throws Exception {
        boolean allEmbedded = !force && chunks.stream()
                .allMatch(c -> c.getEmbedding() != null && !c.getEmbedding().isEmpty());
        if (allEmbedded) {
            List<List<Double>> cached = new ArrayList<>();
            for (DocumentChunk chunk : chunks) {
                cached.add(chunk.getEmbedding());
            }
            return cached;
        }

        List<List<Double>> result = new ArrayList<>();
        int batchSize = 32;
        for (int start = 0; start < chunks.size(); start += batchSize) {
            int end = Math.min(start + batchSize, chunks.size());
            List<String> texts = new ArrayList<>();
            for (int i = start; i < end; i++) {
                texts.add(chunks.get(i).getContent() == null ? "" : chunks.get(i).getContent());
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("texts", texts);
            body.put("model", properties.getEmbedding().getModel());
            ResponseEntity<Map> response = restTemplate.exchange(
                    properties.getEmbedding().getUrl().replaceAll("/$", "") + "/embed",
                    HttpMethod.POST, new HttpEntity<>(body, jsonHeaders()), Map.class);
            Map responseBody = response.getBody();
            if (responseBody == null || !(responseBody.get("embeddings") instanceof List)) {
                return List.of();
            }
            for (Object item : (List<?>) responseBody.get("embeddings")) {
                if (item instanceof List) {
                    result.add((List<Double>) item);
                }
            }
        }
        if (result.size() != chunks.size()) {
            log.warn("向量数量 {} 与分块数量 {} 不一致，跳过向量写入", result.size(), chunks.size());
            return List.of();
        }
        return result;
    }

    private void ensureEsIndex() {
        String url = properties.getEs().getUrl().replaceAll("/$", "")
                + "/" + properties.getEs().getIndex();
        HttpHeaders headers = esHeaders();
        try {
            restTemplate.exchange(url, HttpMethod.HEAD, new HttpEntity<>(headers), Void.class);
            return;
        } catch (HttpClientErrorException.NotFound ignored) {
            // 索引不存在，下面创建
        }
        Map<String, Object> settings = new LinkedHashMap<>();
        Map<String, Object> propertiesMap = new LinkedHashMap<>();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("chunkId", Map.of("type", "keyword"));
        fields.put("documentId", Map.of("type", "keyword"));
        fields.put("kbId", Map.of("type", "keyword"));
        fields.put("content", Map.of("type", "text"));
        fields.put("headingPath", Map.of("type", "text"));
        fields.put("pageStart", Map.of("type", "integer"));
        propertiesMap.put("properties", fields);
        settings.put("mappings", propertiesMap);
        restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(settings, headers), Map.class);
        log.info("已创建 ES 索引: {}", properties.getEs().getIndex());
    }

    private void indexToEs(List<DocumentChunk> chunks) throws Exception {
        StringBuilder ndjson = new StringBuilder();
        for (DocumentChunk chunk : chunks) {
            ndjson.append(objectMapper.writeValueAsString(Map.of(
                    "index", Map.of("_index", properties.getEs().getIndex(), "_id", String.valueOf(chunk.getId())))))
                    .append('\n');
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("chunkId", String.valueOf(chunk.getId()));
            doc.put("documentId", String.valueOf(chunk.getDocumentId()));
            doc.put("kbId", String.valueOf(chunk.getKbId()));
            doc.put("content", chunk.getContent() == null ? "" : chunk.getContent());
            doc.put("headingPath", chunk.getHeadingPath() == null ? "" : chunk.getHeadingPath());
            doc.put("pageStart", chunk.getPageStart());
            doc.put("versionId", chunk.getVersionId());
            ndjson.append(objectMapper.writeValueAsString(doc)).append('\n');
        }
        HttpHeaders headers = esHeaders();
        headers.setContentType(new MediaType("application", "x-ndjson"));
        ResponseEntity<Map> response = restTemplate.exchange(
                properties.getEs().getUrl().replaceAll("/$", "") + "/_bulk?refresh=true",
                HttpMethod.POST, new HttpEntity<>(ndjson.toString().getBytes(StandardCharsets.UTF_8), headers),
                Map.class);
        Map body = response.getBody();
        if (body != null && Boolean.TRUE.equals(body.get("errors"))) {
            log.warn("ES 批量写入存在部分失败，请检查索引日志");
        }
    }

    private void ensureQdrantCollection() {
        String url = properties.getQdrant().getUrl().replaceAll("/$", "")
                + "/collections/" + properties.getQdrant().getCollection();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vectors", Map.of(
                "size", properties.getQdrant().getDimension(),
                "distance", "Cosine"));
        try {
            restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body, qdrantHeaders()), Map.class);
        } catch (Exception e) {
            log.warn("创建 Qdrant 集合失败（可能已存在）: {}", e.getMessage());
        }
    }

    private void indexToQdrant(List<DocumentChunk> chunks, List<List<Double>> vectors) {
        List<Map<String, Object>> points = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("chunk_id", String.valueOf(chunk.getId()));
            payload.put("document_id", String.valueOf(chunk.getDocumentId()));
            payload.put("kb_id", String.valueOf(chunk.getKbId()));
            payload.put("content", chunk.getContent() == null ? "" : chunk.getContent());
            payload.put("heading_path", chunk.getHeadingPath() == null ? "" : chunk.getHeadingPath());
            payload.put("page_start", chunk.getPageStart());

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("id", chunk.getId());
            point.put("vector", vectors.get(i));
            point.put("payload", payload);
            points.add(point);
        }
        Map<String, Object> body = Map.of("points", points);
        String url = properties.getQdrant().getUrl().replaceAll("/$", "")
                + "/collections/" + properties.getQdrant().getCollection() + "/points?wait=true";
        restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body, qdrantHeaders()), Map.class);
    }

    private void markIndexed(List<DocumentChunk> chunks) {
        for (DocumentChunk chunk : chunks) {
            chunk.setIndexed(true);
            chunk.setIndexedAt(LocalDateTime.now());
            chunk.setIndexError(null);
            chunk.setEmbeddingModel(properties.getEmbedding().getModel());
            chunk.setEsId(String.valueOf(chunk.getId()));
            chunk.setQdrantPointId(String.valueOf(chunk.getId()));
            chunkMapper.updateById(chunk);
        }
    }

    private void markFailed(List<DocumentChunk> chunks, String error) {
        String message = error == null ? "索引失败" : error;
        if (message.length() > 1000) {
            message = message.substring(0, 1000);
        }
        for (DocumentChunk chunk : chunks) {
            chunk.setIndexed(false);
            chunk.setIndexError(message);
            chunkMapper.updateById(chunk);
        }
    }

    private HttpHeaders esHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(properties.getEs().getUsername(), properties.getEs().getPassword());
        return headers;
    }

    private HttpHeaders qdrantHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (properties.getQdrant().getApiKey() != null && !properties.getQdrant().getApiKey().isBlank()) {
            headers.set("api-key", properties.getQdrant().getApiKey());
        }
        return headers;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
