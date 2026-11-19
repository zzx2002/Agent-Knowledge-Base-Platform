package com.agentkb.mcp.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * knowledge-api 的 REST 客户端。
 *
 * <p>架构文档 7.1：MCP Server 不直接访问数据库，所有数据都必须经由此客户端调用
 * knowledge-api 获取。调用方传入的 Bearer Token 会原样透传，
 * 由业务 API 完成最终的知识库权限校验。
 */
@Component
public class KnowledgeApiClient {

    private final WebClient webClient;

    public KnowledgeApiClient(WebClient.Builder webClientBuilder,
                              @Value("${kb.api.base-url}") String baseUrl) {
        // base-url 已包含统一前缀 /api/v1（见 application.yml）
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    // ===== 第一版 6 个查询工具对应的 REST 调用 =====

    /** GET /api/v1/knowledge-bases */
    public String listKnowledgeBases(String bearerToken) {
        return get("/knowledge-bases", null, bearerToken);
    }

    /** POST /api/v1/search */
    public String searchKnowledge(Map<String, Object> args, String bearerToken) {
        return post("/search", pick(args, "knowledge_base_id", "query", "top_k", "rerank"), bearerToken);
    }

    /** POST /api/v1/chat */
    public String askKnowledge(Map<String, Object> args, String bearerToken) {
        return post("/chat", pick(args, "knowledge_base_id", "question", "top_k"), bearerToken);
    }

    /** GET /api/v1/documents/{id} */
    public String getDocument(String documentId, String bearerToken) {
        return get("/documents/" + encode(documentId), null, bearerToken);
    }

    /** GET /api/v1/documents?knowledge_base_id=...&keyword=...&page=...&page_size=... */
    public String listDocuments(Map<String, Object> args, String bearerToken) {
        return get("/documents", pick(args, "knowledge_base_id", "keyword", "page", "page_size"), bearerToken);
    }

    /** GET /api/v1/documents/{id}/content */
    public String getDocumentContent(String documentId, String bearerToken) {
        return get("/documents/" + encode(documentId) + "/content", null, bearerToken);
    }

    // ===== 以下 3 个为可选写入/删除能力：默认未注册为 MCP Tool =====
    // 启用前必须：1) 校验调用方具备 WRITE/DELETE 权限；2) 校验 confirm 参数为 true；
    //            3) 增加二次操作确认与审计日志（架构文档 7.3）。

    /** POST /api/v1/documents/upload（具体实现可能为 multipart，需按 knowledge-api 实际签名校准） */
    public String uploadDocument(Map<String, Object> args, String bearerToken) {
        return post("/documents/upload",
                pick(args, "knowledge_base_id", "file_name", "file_type", "file_content_base64"),
                bearerToken);
    }

    /** GET /api/v1/tasks/{id} */
    public String getParseStatus(String taskId, String bearerToken) {
        return get("/tasks/" + encode(taskId), null, bearerToken);
    }

    /** DELETE /api/v1/documents/{id} */
    public String deleteDocument(String documentId, String bearerToken) {
        return webClient.delete()
                .uri(uriBuilder -> uriBuilder.path("/documents/{id}").build(documentId))
                .headers(headers -> headers.setBearerAuth(bearerToken))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    // ===== 通用 HTTP 方法 =====

    private String get(String path, Map<String, Object> queryParams, String bearerToken) {
        return webClient.get()
                .uri(uriBuilder -> buildUri(uriBuilder, path, queryParams))
                .headers(headers -> headers.setBearerAuth(bearerToken))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private String post(String path, Object body, String bearerToken) {
        return webClient.post()
                .uri(uriBuilder -> uriBuilder.path(path).build())
                .headers(headers -> headers.setBearerAuth(bearerToken))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private URI buildUri(UriBuilder uriBuilder, String path, Map<String, Object> queryParams) {
        UriBuilder builder = uriBuilder.path(path);
        if (queryParams != null) {
            queryParams.forEach((key, value) -> {
                if (value != null) {
                    builder.queryParam(key, value);
                }
            });
        }
        return builder.build();
    }

    /** 仅保留指定键且值非空的字段，避免把 SDK 注入的多余参数传给业务 API。 */
    private Map<String, Object> pick(Map<String, Object> source, String... keys) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null) {
                result.put(key, value);
            }
        }
        return result;
    }

    private String encode(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }
}
