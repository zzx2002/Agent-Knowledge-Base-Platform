package com.agentkb.chat.service;

import com.agentkb.chat.config.LlmProperties;
import com.agentkb.chat.dto.ChatResponseDTO;
import com.agentkb.chat.dto.ChatSourceDTO;
import com.agentkb.search.dto.ChatRequest;
import com.agentkb.search.dto.SearchHitDTO;
import com.agentkb.search.dto.SearchRequest;
import com.agentkb.search.dto.SearchResponseDTO;
import com.agentkb.search.service.SearchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * RAG 问答：检索增强 + OpenAI 兼容模型生成 + 来源引用。
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final String SYSTEM_PROMPT =
            "你是企业知识库助手。只能依据提供的参考资料回答问题，不要编造资料中没有的信息。"
                    + "回答中需要使用 [1]、[2] 等编号标注引用来源。若资料不足以回答，请明确说明。"
                    + "回答使用简体中文，保持简洁。";

    private final SearchService searchService;
    private final LlmProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ChatService(SearchService searchService,
                       LlmProperties properties,
                       @Qualifier("searchRestTemplate") RestTemplate restTemplate,
                       ObjectMapper objectMapper) {
        this.searchService = searchService;
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /** 非流式问答。 */
    @SuppressWarnings("unchecked")
    public ChatResponseDTO chat(ChatRequest request) {
        SearchResponseDTO searchResult = retrieve(request);
        List<ChatSourceDTO> sources = toSources(searchResult.getHits());
        Map<String, Object> body = buildBody(request.getQuestion(), searchResult.getHits(), false);
        HttpHeaders headers = llmHeaders(false);
        Map response = restTemplate.exchange(
                properties.getBaseUrl().replaceAll("/$", "") + "/chat/completions",
                HttpMethod.POST, new HttpEntity<>(body, headers), Map.class).getBody();
        String answer = extractAnswer(response);
        ChatResponseDTO result = new ChatResponseDTO();
        result.setAnswer(answer);
        result.setSources(sources);
        return result;
    }

    /** SSE 流式问答：先逐块推送 delta，最后推送 sources。 */
    public SseEmitter stream(ChatRequest request) {
        SseEmitter emitter = new SseEmitter(120000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> log.warn("SSE 连接异常: {}", e.getMessage()));
        SecurityContext securityContext = SecurityContextHolder.getContext();

        CompletableFuture.runAsync(() -> {
            SecurityContextHolder.setContext(securityContext);
            try {
                SearchResponseDTO searchResult = retrieve(request);
                List<ChatSourceDTO> sources = toSources(searchResult.getHits());
                Map<String, Object> body = buildBody(request.getQuestion(), searchResult.getHits(), true);
                streamLlm(body, emitter);
                emitter.send(SseEmitter.event().name("sources").data(sources));
                emitter.complete();
            } catch (Exception e) {
                log.error("流式问答失败: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (Exception ignored) {
                    // 客户端已断开
                }
                emitter.completeWithError(e);
            }
        }).whenComplete((v, e) -> SecurityContextHolder.clearContext());
        return emitter;
    }

    private SearchResponseDTO retrieve(ChatRequest request) {
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setKnowledgeBaseId(request.getKnowledgeBaseId());
        searchRequest.setQuery(request.getQuestion());
        searchRequest.setTopK(request.getTopK());
        searchRequest.setRerank(true);
        return searchService.search(searchRequest, false);
    }

    private Map<String, Object> buildBody(String question, List<SearchHitDTO> hits, boolean stream) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            SearchHitDTO hit = hits.get(i);
            context.append('[').append(i + 1).append("] 文档: ")
                    .append(hit.getFileName() == null ? hit.getDocumentId() : hit.getFileName());
            if (hit.getPage() != null) {
                context.append("，页码: ").append(hit.getPage());
            }
            context.append('\n').append(hit.getContent()).append("\n\n");
        }
        String userPrompt = "参考资料:\n" + context
                + (hits.isEmpty() ? "（没有检索到相关资料）\n\n" : "")
                + "问题: " + question;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", userPrompt)));
        body.put("temperature", properties.getTemperature());
        body.put("max_tokens", properties.getMaxTokens());
        body.put("stream", stream);
        return body;
    }

    @SuppressWarnings("unchecked")
    private String extractAnswer(Map response) {
        if (response == null || !(response.get("choices") instanceof List)) {
            return "模型未返回有效回答";
        }
        List choices = (List) response.get("choices");
        if (choices.isEmpty() || !(choices.get(0) instanceof Map)) {
            return "模型未返回有效回答";
        }
        Map choice = (Map) choices.get(0);
        Object message = choice.get("message");
        if (message instanceof Map) {
            Object content = ((Map) message).get("content");
            return content == null ? "" : String.valueOf(content);
        }
        return "";
    }

    private void streamLlm(Map<String, Object> body, SseEmitter emitter) throws Exception {
        String url = properties.getBaseUrl().replaceAll("/$", "") + "/chat/completions";
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .build();
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(url))
                .timeout(java.time.Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream");
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + properties.getApiKey());
        }
        HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(body)))
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("模型服务返回 HTTP " + response.statusCode());
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) {
                    continue;
                }
                JsonNode node = objectMapper.readTree(data);
                JsonNode delta = node.path("choices").path(0).path("delta").path("content");
                if (!delta.isMissingNode() && !delta.isNull()) {
                    emitter.send(SseEmitter.event().name("delta").data(delta.asText()));
                }
            }
        }
    }

    private List<ChatSourceDTO> toSources(List<SearchHitDTO> hits) {
        List<ChatSourceDTO> sources = new ArrayList<>();
        for (SearchHitDTO hit : hits) {
            ChatSourceDTO source = new ChatSourceDTO();
            source.setChunkId(hit.getChunkId());
            source.setDocumentId(hit.getDocumentId());
            source.setFileName(hit.getFileName());
            source.setPage(hit.getPage());
            source.setScore(hit.getScore());
            String content = hit.getContent() == null ? "" : hit.getContent();
            source.setContent(content.length() > 300 ? content.substring(0, 300) + "…" : content);
            sources.add(source);
        }
        return sources;
    }

    private HttpHeaders llmHeaders(boolean stream) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (stream) {
            headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
        }
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            headers.setBearerAuth(properties.getApiKey());
        }
        return headers;
    }
}
