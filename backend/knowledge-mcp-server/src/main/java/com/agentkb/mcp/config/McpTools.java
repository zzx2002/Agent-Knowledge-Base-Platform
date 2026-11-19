package com.agentkb.mcp.config;

import com.agentkb.mcp.tool.KnowledgeTools;
import com.agentkb.mcp.tool.ToolJsonSchemas;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * MCP 工具注册配置（架构文档 7.3 / 7.4）。
 *
 * <p>第一版仅注册 6 个 READ 查询工具；3 个写入/删除工具的实现已保留在 KnowledgeTools，
 * 但默认不注册，需完成权限确认机制后再开启（见 mcp-tools.json）。
 *
 * <p>============================== SDK 版本校准提示 ==============================
 * 本类按 io.modelcontextprotocol.sdk 0.11.3 的 API 编写。MCP Java SDK 迭代较快，
 * 升级版本时请逐项核对以下标注 “ [SDK 版本校准] ” 的位置：
 *   1) WebMvcStreamableServerTransportProvider 的构造方式（0.11.3 使用 builder().mcpEndpoint("/mcp")）；
 *   2) McpSchema.Tool 构造器入参类型（0.8.1 接受 JSON Schema 字符串）；
 *   3) McpServerFeatures.SyncToolSpecification#callHandler 的函数签名；
 *   4) McpSchema.CallToolResult / McpSchema.TextContent 的构造器；
 *   5) SyncSpecification#tools 与 #capabilities 的链式调用。
 * 工具名称、描述与 inputSchema 以 src/main/resources/mcp-tools.json 为对照清单。
 */
@Configuration
public class McpTools {

    private static final String SERVER_NAME = "knowledge-mcp-server";
    private static final String SERVER_VERSION = "1.0.0";

    private final KnowledgeTools knowledgeTools;
    private final ObjectMapper objectMapper;

    public McpTools(KnowledgeTools knowledgeTools, ObjectMapper objectMapper) {
        this.knowledgeTools = knowledgeTools;
        this.objectMapper = objectMapper;
    }

    /**
     * SDK 0.11.3：通过 builder 指定 /mcp 端点。
     */
    @Bean
    public WebMvcStreamableServerTransportProvider mcpTransportProvider() {
        return WebMvcStreamableServerTransportProvider.builder()
                .objectMapper(objectMapper)
                .mcpEndpoint("/mcp")
                .build();
    }

    /**
     * 创建同步 MCP Server 并注册第一版 6 个查询工具。
     * Streamable HTTP 的请求由 Spring MVC 接入，工具回调在当前请求线程同步执行，
     * 因此 SecurityContext 中的 JWT 可被 BearerTokenResolver 正确读取。
     */
    @Bean
    public McpSyncServer mcpSyncServer(WebMvcStreamableServerTransportProvider transportProvider) {
        // [SDK 版本校准] 0.8.1：McpServer.sync(transportProvider) 返回 SyncSpecification 构造器。
        // [SDK 版本校准] tools(...) 在部分版本为 List 入参、部分版本为可变参数，
        // 若编译不通过请将 List.of(...) 改为逐个参数传入。
        return McpServer.sync(transportProvider)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                // [SDK 版本校准] 0.9+ 部分版本会在 tools(...) 时自动声明 capabilities，
                // 若重复声明报错，可删除下面这一行。
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .tools(List.of(
                        syncTool("list_knowledge_bases",
                                "获取当前用户有权访问的知识库列表",
                                ToolJsonSchemas.LIST_KNOWLEDGE_BASES,
                                knowledgeTools::listKnowledgeBases),
                        syncTool("search_knowledge",
                                "在指定知识库中执行全文/语义混合检索",
                                ToolJsonSchemas.SEARCH_KNOWLEDGE,
                                knowledgeTools::searchKnowledge),
                        syncTool("ask_knowledge",
                                "基于指定知识库进行 RAG 问答并返回答案与引用来源",
                                ToolJsonSchemas.ASK_KNOWLEDGE,
                                knowledgeTools::askKnowledge),
                        syncTool("get_document",
                                "根据文档 ID 获取文档元数据详情",
                                ToolJsonSchemas.GET_DOCUMENT,
                                knowledgeTools::getDocument),
                        syncTool("list_documents",
                                "按知识库分页获取文档列表，支持关键字过滤",
                                ToolJsonSchemas.LIST_DOCUMENTS,
                                knowledgeTools::listDocuments),
                        syncTool("get_document_content",
                                "根据文档 ID 获取解析后的文档正文内容",
                                ToolJsonSchemas.GET_DOCUMENT_CONTENT,
                                knowledgeTools::getDocumentContent)
                ))
                .build();
    }

    /**
     * 构造一个工具规格。工具方法返回 JSON 字符串，并统一包装为 MCP CallToolResult。
     *
     * <p>[SDK 版本校准] 0.8.1：
     * <ul>
     *     <li>{@code new McpSchema.Tool(name, description, inputSchemaJson)}：
     *         若你使用的版本只接受 {@code McpSchema.JsonSchema}，
     *         请改为 {@code new McpSchema.Tool(name, description, parseJsonSchema(inputSchemaJson))}。</li>
     *     <li>{@code callHandler((exchange, arguments) -> ...)}：
     *         arguments 为 {@code Map<String, Object>}，新版本可能改为强类型请求对象。</li>
     *     <li>{@code new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(json)), isError)}：
     *         新版本可能改用 builder()。</li>
     * </ul>
     */
    private McpServerFeatures.SyncToolSpecification syncTool(
            String name,
            String description,
            String inputSchemaJson,
            Function<Map<String, Object>, String> handler) {

        McpSchema.Tool tool = new McpSchema.Tool(name, description, inputSchemaJson);

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> arguments = request == null || request.arguments() == null
                                ? Map.of() : request.arguments();
                        String json = handler.apply(arguments);
                        return new McpSchema.CallToolResult(
                                List.of(new McpSchema.TextContent(json)), false);
                    } catch (Exception exception) {
                        return errorResult(exception);
                    }
                })
                .build();
    }

    /** 把异常转成结构化的 JSON 错误结果，避免 MCP 客户端拿到裸异常信息。 */
    private McpSchema.CallToolResult errorResult(Exception exception) {
        String message = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("success", false);
        error.put("error", message);
        try {
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent(objectMapper.writeValueAsString(error))), true);
        } catch (JsonProcessingException jsonException) {
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("{\"success\":false,\"error\":\"internal error\"}")), true);
        }
    }
}
