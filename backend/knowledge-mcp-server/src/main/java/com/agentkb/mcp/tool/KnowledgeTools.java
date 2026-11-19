package com.agentkb.mcp.tool;

import com.agentkb.mcp.client.KnowledgeApiClient;
import com.agentkb.mcp.util.BearerTokenResolver;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

/**
 * MCP 工具的业务实现：每个方法接收 SDK 传入的参数 Map，返回 JSON 字符串结果。
 *
 * <p>第一版注册 6 个查询工具（READ）。3 个写入/删除工具作为可选能力保留实现，
 * 默认不注册为 MCP Tool；启用前必须补充角色权限校验（EDITOR/KB_ADMIN/ADMIN）、
 * confirm 操作确认与审计日志。最终的权限判定仍由 knowledge-api 在服务端执行。
 */
@Service
public class KnowledgeTools {

    private final KnowledgeApiClient apiClient;
    private final BearerTokenResolver tokenResolver;

    public KnowledgeTools(KnowledgeApiClient apiClient, BearerTokenResolver tokenResolver) {
        this.apiClient = apiClient;
        this.tokenResolver = tokenResolver;
    }

    // ===== 第一版 6 个查询工具 =====

    /** list_knowledge_bases：获取当前用户有权访问的知识库列表。 */
    public String listKnowledgeBases(Map<String, Object> arguments) {
        return apiClient.listKnowledgeBases(tokenResolver.requireToken());
    }

    /** search_knowledge：混合检索（权限过滤由 knowledge-api 完成）。 */
    public String searchKnowledge(Map<String, Object> arguments) {
        return apiClient.searchKnowledge(args(arguments), tokenResolver.requireToken());
    }

    /** ask_knowledge：RAG 问答。 */
    public String askKnowledge(Map<String, Object> arguments) {
        return apiClient.askKnowledge(args(arguments), tokenResolver.requireToken());
    }

    /** get_document：获取文档元数据详情。 */
    public String getDocument(Map<String, Object> arguments) {
        return apiClient.getDocument(requireString(arguments, "document_id"), tokenResolver.requireToken());
    }

    /** list_documents：按知识库分页列出文档。 */
    public String listDocuments(Map<String, Object> arguments) {
        return apiClient.listDocuments(args(arguments), tokenResolver.requireToken());
    }

    /** get_document_content：获取文档解析后的正文内容。 */
    public String getDocumentContent(Map<String, Object> arguments) {
        return apiClient.getDocumentContent(requireString(arguments, "document_id"), tokenResolver.requireToken());
    }

    // ===== 可选写入/删除工具：默认不注册，启用前请完成权限确认机制 =====

    /** upload_document：需要 WRITE 权限（EDITOR 及以上）+ confirm=true。 */
    public String uploadDocument(Map<String, Object> arguments) {
        requireConfirm(arguments);
        return apiClient.uploadDocument(args(arguments), tokenResolver.requireToken());
    }

    /** get_parse_status：READ 权限，查询解析任务状态。 */
    public String getParseStatus(Map<String, Object> arguments) {
        return apiClient.getParseStatus(requireString(arguments, "task_id"), tokenResolver.requireToken());
    }

    /** delete_document：需要 DELETE 权限（KB_ADMIN/ADMIN）+ confirm=true。 */
    public String deleteDocument(Map<String, Object> arguments) {
        requireConfirm(arguments);
        return apiClient.deleteDocument(requireString(arguments, "document_id"), tokenResolver.requireToken());
    }

    // ===== 内部工具方法 =====

    private Map<String, Object> args(Map<String, Object> arguments) {
        return arguments == null ? Collections.emptyMap() : arguments;
    }

    private String requireString(Map<String, Object> arguments, String key) {
        Object value = args(arguments).get(key);
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw new IllegalArgumentException("缺少必填参数: " + key);
        }
        return stringValue;
    }

    /**
     * 写入/删除操作的二次确认。
     * [待补充] 生产启用时在此处增加基于 JwtAuthenticationToken 的 ROLE/SCOPE 校验，
     * 并写入 kb_access_log 审计日志；knowledge-api 仍会做最终权限判定。
     */
    private void requireConfirm(Map<String, Object> arguments) {
        if (!Boolean.TRUE.equals(args(arguments).get("confirm"))) {
            throw new IllegalArgumentException("写入/删除操作必须显式设置 confirm=true 以完成操作确认");
        }
    }
}
