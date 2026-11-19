package com.agentkb.search.service;

import com.agentkb.document.entity.Document;
import com.agentkb.document.mapper.DocumentMapper;
import com.agentkb.search.config.SearchProperties;
import com.agentkb.search.dto.SearchHitDTO;
import com.agentkb.search.dto.SearchRequest;
import com.agentkb.search.dto.SearchResponseDTO;
import com.agentkb.security.KbAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 混合检索：ES 关键词召回 + Qdrant 向量召回 + RRF 融合 + Reranker 重排。
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    private static final int RRF_CONSTANT = 60;

    private final SearchProperties properties;
    private final RestTemplate restTemplate;
    private final KbAccessService accessService;
    private final DocumentMapper documentMapper;

    public SearchService(SearchProperties properties,
                         @Qualifier("searchRestTemplate") RestTemplate restTemplate,
                         KbAccessService accessService,
                         DocumentMapper documentMapper) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.accessService = accessService;
        this.documentMapper = documentMapper;
    }

    /** 执行混合检索。 */
    @SuppressWarnings("unchecked")
    public SearchResponseDTO search(SearchRequest request, boolean debug) {
        int topK = request.getTopK() == null || request.getTopK() <= 0 ? 10 : request.getTopK();
        int candidateSize = Math.max(topK * 3, 10);
        List<Long> readableIds = resolveReadableKbIds(request.getKnowledgeBaseId());

        if (readableIds != null && readableIds.isEmpty()) {
            SearchResponseDTO empty = new SearchResponseDTO();
            empty.setQuery(request.getQuery());
            empty.setHits(List.of());
            empty.setDebug(Map.of("keyword", List.of(), "vector", List.of(), "fused", List.of()));
            return empty;
        }

        List<SearchHitDTO> keywordHits = keywordSearch(request.getQuery(), readableIds, candidateSize);
        List<SearchHitDTO> vectorHits = vectorSearch(request.getQuery(), readableIds, candidateSize);
        List<SearchHitDTO> fused = fuse(keywordHits, vectorHits, candidateSize);

        if (request.getRerank() == null || request.getRerank()) {
            fused = rerank(request.getQuery(), fused, topK);
        }
        List<SearchHitDTO> hits = fused.stream().limit(topK).collect(Collectors.toList());
        fillFileNames(hits);

        SearchResponseDTO response = new SearchResponseDTO();
        response.setQuery(request.getQuery());
        response.setHits(hits);
        if (debug) {
            Map<String, Object> debugInfo = new LinkedHashMap<>();
            debugInfo.put("keyword", simplify(keywordHits));
            debugInfo.put("vector", simplify(vectorHits));
            debugInfo.put("fused", simplify(fused));
            response.setDebug(debugInfo);
        }
        return response;
    }

    private List<Long> resolveReadableKbIds(Long requestedKbId) {
        if (requestedKbId != null) {
            accessService.require(requestedKbId, KbAccessService.PERM_READ);
            return List.of(requestedKbId);
        }
        return accessService.readableKbIds();
    }

    @SuppressWarnings("unchecked")
    private List<SearchHitDTO> keywordSearch(String query, List<Long> readableIds, int size) {
        Map<String, Object> bool = new LinkedHashMap<>();
        Map<String, Object> multiMatch = new LinkedHashMap<>();
        multiMatch.put("query", query);
        multiMatch.put("fields", List.of("content^2", "headingPath"));
        bool.put("must", List.of(Map.of("multi_match", multiMatch)));

        List<Map<String, Object>> filters = new ArrayList<>();
        Long single = singleKb(readableIds);
        if (single != null) {
            filters.add(Map.of("term", Map.of("kbId", String.valueOf(single))));
        } else if (readableIds != null && !readableIds.isEmpty()) {
            filters.add(Map.of("terms", Map.of("kbId", readableIds.stream().map(String::valueOf).collect(Collectors.toList()))));
        }
        if (!filters.isEmpty()) {
            bool.put("filter", filters);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", Map.of("bool", bool));
        body.put("size", size);

        String url = properties.getEs().getUrl().replaceAll("/$", "")
                + "/" + properties.getEs().getIndex() + "/_search";
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(body, esHeaders()), Map.class);
            Map responseBody = response.getBody();
            if (responseBody == null) {
                return List.of();
            }
            Map hitsRoot = (Map) responseBody.get("hits");
            List<Map> rawHits = hitsRoot == null ? List.of() : (List<Map>) hitsRoot.get("hits");
            List<SearchHitDTO> result = new ArrayList<>();
            for (Map raw : rawHits) {
                Map source = (Map) raw.get("_source");
                if (source == null) {
                    continue;
                }
                SearchHitDTO hit = new SearchHitDTO();
                hit.setChunkId(str(source.get("chunkId")));
                hit.setDocumentId(str(source.get("documentId")));
                hit.setKbId(str(source.get("kbId")));
                hit.setContent(str(source.get("content")));
                hit.setHeadingPath(str(source.get("headingPath")));
                hit.setPage(intOrNull(source.get("pageStart")));
                hit.setKeywordScore(num(raw.get("_score")));
                hit.setScore(hit.getKeywordScore());
                result.add(hit);
            }
            return result;
        } catch (Exception e) {
            log.warn("ES 关键词检索失败: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<SearchHitDTO> vectorSearch(String query, List<Long> readableIds, int size) {
        List<Double> vector = embedQuery(query);
        if (vector == null || vector.isEmpty()) {
            return List.of();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vector", vector);
        body.put("limit", size);
        body.put("with_payload", true);

        Long single = singleKb(readableIds);
        if (single != null) {
            body.put("query_filter", Map.of("must", List.of(
                    Map.of("key", "kb_id", "match", Map.of("value", String.valueOf(single))))));
        } else if (readableIds != null && !readableIds.isEmpty()) {
            body.put("query_filter", Map.of("must", List.of(
                    Map.of("key", "kb_id", "match", Map.of("any", readableIds.stream().map(String::valueOf).collect(Collectors.toList()))))));
        }

        String url = properties.getQdrant().getUrl().replaceAll("/$", "")
                + "/collections/" + properties.getQdrant().getCollection() + "/points/search";
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(body, qdrantHeaders()), Map.class);
            Map responseBody = response.getBody();
            if (responseBody == null || !(responseBody.get("result") instanceof List)) {
                return List.of();
            }
            List<SearchHitDTO> result = new ArrayList<>();
            for (Object item : (List<?>) responseBody.get("result")) {
                Map point = (Map) item;
                Map payload = (Map) point.get("payload");
                if (payload == null) {
                    continue;
                }
                SearchHitDTO hit = new SearchHitDTO();
                hit.setChunkId(str(payload.get("chunk_id")));
                hit.setDocumentId(str(payload.get("document_id")));
                hit.setKbId(str(payload.get("kb_id")));
                hit.setContent(str(payload.get("content")));
                hit.setHeadingPath(str(payload.get("heading_path")));
                hit.setPage(intOrNull(payload.get("page_start")));
                hit.setVectorScore(num(point.get("score")));
                hit.setScore(hit.getVectorScore());
                result.add(hit);
            }
            return result;
        } catch (Exception e) {
            log.warn("Qdrant 向量检索失败: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Double> embedQuery(String query) {
        Map<String, Object> body = Map.of(
                "texts", List.of(query),
                "model", properties.getEmbedding().getModel());
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    properties.getEmbedding().getUrl().replaceAll("/$", "") + "/embed",
                    HttpMethod.POST, new HttpEntity<>(body, jsonHeaders()), Map.class);
            Map responseBody = response.getBody();
            if (responseBody == null || !(responseBody.get("embeddings") instanceof List)) {
                return null;
            }
            List<?> embeddings = (List<?>) responseBody.get("embeddings");
            if (embeddings.isEmpty() || !(embeddings.get(0) instanceof List)) {
                return null;
            }
            return (List<Double>) embeddings.get(0);
        } catch (Exception e) {
            log.warn("查询向量化失败: {}", e.getMessage());
            return null;
        }
    }

    private List<SearchHitDTO> fuse(List<SearchHitDTO> keywordHits,
                                    List<SearchHitDTO> vectorHits,
                                    int limit) {
        Map<String, SearchHitDTO> merged = new LinkedHashMap<>();
        addRrf(merged, keywordHits, "keyword");
        addRrf(merged, vectorHits, "vector");
        return merged.values().stream()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private void addRrf(Map<String, SearchHitDTO> merged, List<SearchHitDTO> hits, String source) {
        for (int i = 0; i < hits.size(); i++) {
            SearchHitDTO hit = hits.get(i);
            if (hit.getChunkId() == null) {
                continue;
            }
            double contribution = 1.0 / (RRF_CONSTANT + i + 1);
            SearchHitDTO existing = merged.get(hit.getChunkId());
            if (existing == null) {
                hit.setScore(contribution);
                merged.put(hit.getChunkId(), hit);
            } else {
                existing.setScore(existing.getScore() + contribution);
                if (existing.getKeywordScore() == null && "keyword".equals(source)) {
                    existing.setKeywordScore(hit.getKeywordScore());
                }
                if (existing.getVectorScore() == null && "vector".equals(source)) {
                    existing.setVectorScore(hit.getVectorScore());
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<SearchHitDTO> rerank(String query, List<SearchHitDTO> candidates, int topK) {
        if (!properties.getRerank().isEnabled() || candidates.isEmpty()) {
            return candidates;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("documents", candidates.stream().map(SearchHitDTO::getContent).collect(Collectors.toList()));
        body.put("top_k", Math.min(topK, candidates.size()));
        body.put("model", properties.getRerank().getModel());
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    properties.getRerank().getUrl().replaceAll("/$", "") + "/rerank",
                    HttpMethod.POST, new HttpEntity<>(body, jsonHeaders()), Map.class);
            Map responseBody = response.getBody();
            if (responseBody == null || !(responseBody.get("results") instanceof List)) {
                return candidates;
            }
            List<SearchHitDTO> reranked = new ArrayList<>();
            for (Object item : (List<?>) responseBody.get("results")) {
                Map result = (Map) item;
                int index = ((Number) result.get("index")).intValue();
                if (index < 0 || index >= candidates.size()) {
                    continue;
                }
                SearchHitDTO hit = candidates.get(index);
                hit.setRerankScore(num(result.get("score")));
                hit.setScore(hit.getRerankScore());
                reranked.add(hit);
            }
            return reranked.isEmpty() ? candidates : reranked;
        } catch (Exception e) {
            log.warn("Reranker 重排失败，使用 RRF 结果: {}", e.getMessage());
            return candidates;
        }
    }

    private void fillFileNames(List<SearchHitDTO> hits) {
        List<Long> documentIds = hits.stream()
                .map(SearchHitDTO::getDocumentId)
                .filter(id -> id != null && !id.isBlank())
                .map(Long::valueOf)
                .distinct()
                .collect(Collectors.toList());
        if (documentIds.isEmpty()) {
            return;
        }
        Map<String, String> names = documentMapper.selectBatchIds(documentIds).stream()
                .collect(Collectors.toMap(doc -> String.valueOf(doc.getId()), Document::getFileName));
        for (SearchHitDTO hit : hits) {
            hit.setFileName(names.get(hit.getDocumentId()));
        }
    }

    private List<Map<String, Object>> simplify(List<SearchHitDTO> hits) {
        return hits.stream().limit(10).map(hit -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("chunk_id", hit.getChunkId());
            item.put("document_id", hit.getDocumentId());
            item.put("keyword_score", hit.getKeywordScore());
            item.put("vector_score", hit.getVectorScore());
            item.put("rrf_score", hit.getScore());
            item.put("rerank_score", hit.getRerankScore());
            return item;
        }).collect(Collectors.toList());
    }

    private Long singleKb(List<Long> ids) {
        return ids != null && ids.size() == 1 ? ids.get(0) : null;
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Double num(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    private Integer intOrNull(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : null;
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
