package com.agentkb.audit.interceptor;

import com.agentkb.audit.entity.AccessLog;
import com.agentkb.audit.service.AuditLogService;
import com.agentkb.security.KbAccessService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.LinkedHashMap;
import java.util.Map;

/** 记录 /api/v1/** 请求的操作审计。 */
@Component
public class AuditLogInterceptor implements HandlerInterceptor {

    private static final String START = "audit_start";

    private final AuditLogService auditLogService;
    private final KbAccessService accessService;
    private final ObjectMapper objectMapper;

    public AuditLogInterceptor(AuditLogService auditLogService,
                               KbAccessService accessService,
                               ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.accessService = accessService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getRequestURI().startsWith("/api/v1/")) {
            request.setAttribute(START, System.currentTimeMillis());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        if (!request.getRequestURI().startsWith("/api/v1/")) {
            return;
        }
        try {
            Object start = request.getAttribute(START);
            long duration = start instanceof Long value ? System.currentTimeMillis() - value : 0L;
            Object successAttr = request.getAttribute(AuditResultAdvice.ATTR_SUCCESS);
            boolean success = successAttr instanceof Boolean value
                    ? value
                    : response.getStatus() >= 200 && response.getStatus() < 400 && ex == null;

            AccessLog log = new AccessLog();
            log.setId(IdWorker.getId());
            try {
                log.setUserId(accessService.currentUserId());
            } catch (Exception ignored) {
                // 匿名请求（如登录前）不记录用户 ID
            }
            String uri = request.getRequestURI();
            log.setOperation(request.getMethod() + " " + normalizePath(uri));
            log.setResourceType(resourceType(uri));
            log.setResourceId(resourceId(request, uri));
            log.setRequestIp(clientIp(request));
            String agent = request.getHeader("User-Agent");
            log.setUserAgent(agent == null ? null : agent.substring(0, Math.min(255, agent.length())));
            log.setSuccess(success);

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("method", request.getMethod());
            detail.put("uri", uri);
            detail.put("http_status", response.getStatus());
            detail.put("duration_ms", duration);
            if (request.getAttribute(AuditResultAdvice.ATTR_CODE) != null) {
                detail.put("result_code", request.getAttribute(AuditResultAdvice.ATTR_CODE));
            }
            if (request.getAttribute(AuditResultAdvice.ATTR_MESSAGE) != null) {
                detail.put("result_message", request.getAttribute(AuditResultAdvice.ATTR_MESSAGE));
            }
            if (ex != null) {
                detail.put("exception", ex.getClass().getSimpleName());
            }
            log.setDetail(objectMapper.writeValueAsString(detail));
            auditLogService.record(log);
        } catch (Exception ignored) {
            // 审计异常不能影响业务响应
        }
    }

    private String normalizePath(String uri) {
        return uri.replaceAll("/\\d+(?=/|$)", "/{}");
    }

    private String resourceType(String uri) {
        String[] parts = uri.split("/");
        return parts.length > 3 ? parts[3] : "api";
    }

    private String resourceId(HttpServletRequest request, String uri) {
        String kbId = firstNonBlank(request.getParameter("kbId"), request.getParameter("knowledgeBaseId"));
        if (kbId != null) {
            return kbId;
        }
        String[] parts = uri.split("/");
        for (int i = parts.length - 1; i >= 3; i--) {
            if (parts[i].matches("\\d+")) {
                return parts[i];
            }
        }
        return null;
    }

    private String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null && !b.isBlank() ? b : null);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
