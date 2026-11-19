package com.agentkb.chat.service;

import com.agentkb.chat.config.LlmProperties;
import com.agentkb.chat.dto.ChatResponseDTO;
import com.agentkb.chat.dto.ChatSourceDTO;
import com.agentkb.search.dto.ChatRequest;
import com.agentkb.search.dto.SearchHitDTO;
import com.agentkb.search.dto.SearchRequest;
import com.agentkb.search.dto.SearchResponseDTO;
import com.agentkb.search.service.SearchService;
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
            "你是企业知识库助手。当提供参考资料时，只能依据资料回答，不要编造资料中没有的信息，"
                    + "并用 [1]、[2] 等编号标注引用来源；资料不足时明确说明。"
                    + "当没有参考资料时，可以正常介绍自己并进行通用对话，但不要编造业务事实。"
                    + "回答使用简体中文，保持简洁。";

    private final SearchService searchService;
    private final LlmProperties properties;
    private final RestTemplate restTemplate;
    private final ChatHistoryService historyService;

    public ChatService(SearchService searchService,
                       LlmProperties properties,
                       @Qualifier("searchRestTemplate") RestTemplate restTemplate,
                       ChatHistoryService historyService) {
        this.searchService = searchService;
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.historyService = historyService;
    }

    /** 非流式问答。 */
    @SuppressWarnings("unchecked")
    public ChatResponseDTO chat(ChatRequest request) {
        List<SearchHitDTO> hits = retrieveHits(request);
        List<ChatSourceDTO> sources = toSources(hits);
        Map<String, Object> body = buildBody(request.getQuestion(), hits, false);
        HttpHeaders headers = llmHeaders(false);
        Map response = null;
        RuntimeException lastError = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                response = restTemplate.exchange(
                        properties.getBaseUrl().replaceAll("/$", "") + "/chat/completions",
                        HttpMethod.POST, new HttpEntity<>(body, headers), Map.class).getBody();
                break;
            } catch (RuntimeException e) {
                lastError = e;
                log.warn("模型调用失败，第 {} 次重试: {}", attempt + 1, e.getMessage());
                try {
                    Thread.sleep(300L * (attempt + 1));
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (response == null && lastError != null) {
            throw lastError;
        }
        String answer = extractAnswer(response);
        ChatResponseDTO result = new ChatResponseDTO();
        result.setAnswer(answer);
        result.setSources(sources);
        historyService.append(request.getSessionId(), "user", request.getQuestion(), null);
        historyService.append(request.getSessionId(), "assistant", answer, sources);
        return result;
    }

    /** SSE 流式问答：基于非流式模型结果逐块推送，最后推送 sources。 */
    public SseEmitter stream(ChatRequest request) {
        SseEmitter emitter = new SseEmitter(120000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> log.warn("SSE 连接异常: {}", e.getMessage()));
        SecurityContext securityContext = SecurityContextHolder.getContext();

        CompletableFuture.runAsync(() -> {
            SecurityContextHolder.setContext(securityContext);
            try {
                ChatResponseDTO response = chat(request);
                String answer = response.getAnswer() == null ? "" : response.getAnswer();
                int size = answer.length() > 400 ? 2 : 1;
                long delay = answer.length() > 400 ? 18 : 30;
                for (int i = 0; i < answer.length(); i += size) {
                    String chunk = answer.substring(i, Math.min(i + size, answer.length()))
                            .replace("\r", " ").replace("\n", " ");
                    emitter.send(SseEmitter.event().name("delta").data(chunk));
                    Thread.sleep(delay);
                }
                emitter.send(SseEmitter.event().name("sources").data(response.getSources()));
                emitter.complete();
            } catch (Exception e) {
                log.error("流式问答失败: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(e.getMessage() == null ? "服务暂时不可用" : e.getMessage()));
                } catch (Exception ignored) {
                    // 客户端已断开
                }
                emitter.completeWithError(e);
            }
        }).whenComplete((v, e) -> SecurityContextHolder.clearContext());
        return emitter;
    }

    private List<SearchHitDTO> retrieveHits(ChatRequest request) {
        if (isSmallTalk(request.getQuestion())) {
            return List.of();
        }
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setKnowledgeBaseId(request.getKnowledgeBaseId());
        searchRequest.setQuery(request.getQuestion());
        searchRequest.setTopK(request.getTopK());
        searchRequest.setRerank(true);
        SearchResponseDTO result = searchService.search(searchRequest, false);
        return result.getHits() == null ? List.of() : result.getHits();
    }

    private boolean isSmallTalk(String question) {
        if (question == null) {
            return false;
        }
        String text = question.trim().toLowerCase();
        return text.matches("(?i)(你好|您好|嗨|哈喽|hi|hello|hey|在吗|你是谁|你叫什么|你是谁呀)[!！。？?\\s]*")
                || text.contains("你是谁") || text.contains("你叫什么");
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
        String userPrompt = hits.isEmpty()
                ? "问题: " + question
                : "参考资料:\n" + context + "问题: " + question;
        String systemPrompt = hits.isEmpty()
                ? "你是企业知识库助手。当前没有参考资料，可以自然问候、介绍自己或进行通用对话，"
                        + "不要编造业务事实，也不要使用引用编号。回答使用简体中文，保持简洁。"
                : SYSTEM_PROMPT;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
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
