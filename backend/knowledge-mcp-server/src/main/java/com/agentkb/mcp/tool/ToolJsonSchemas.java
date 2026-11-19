package com.agentkb.mcp.tool;

/**
 * MCP 工具入参 JSON Schema 常量（架构文档 7.4）。
 *
 * <p>这里保持纯字符串形式，便于与 src/main/resources/mcp-tools.json 对照；
 * 注册工具时由 McpTools 转换为 SDK 所需的 Tool 对象。
 * 第一版仅注册前 6 个查询工具，后 3 个写入/删除工具为可选能力。
 */
public final class ToolJsonSchemas {

    private ToolJsonSchemas() {
    }

    public static final String LIST_KNOWLEDGE_BASES = """
            {
              "type": "object",
              "properties": {},
              "additionalProperties": false
            }
            """;

    public static final String SEARCH_KNOWLEDGE = """
            {
              "type": "object",
              "properties": {
                "knowledge_base_id": { "type": "string", "description": "知识库 ID" },
                "query": { "type": "string", "description": "检索问题" },
                "top_k": { "type": "integer", "description": "返回条数", "default": 5, "minimum": 1 },
                "rerank": { "type": "boolean", "description": "是否启用重排序", "default": true }
              },
              "required": ["knowledge_base_id", "query"]
            }
            """;

    public static final String ASK_KNOWLEDGE = """
            {
              "type": "object",
              "properties": {
                "knowledge_base_id": { "type": "string", "description": "知识库 ID" },
                "question": { "type": "string", "description": "用户问题" },
                "top_k": { "type": "integer", "description": "召回条数", "default": 5, "minimum": 1 }
              },
              "required": ["knowledge_base_id", "question"]
            }
            """;

    public static final String GET_DOCUMENT = """
            {
              "type": "object",
              "properties": {
                "document_id": { "type": "string", "description": "文档 ID" }
              },
              "required": ["document_id"]
            }
            """;

    public static final String LIST_DOCUMENTS = """
            {
              "type": "object",
              "properties": {
                "knowledge_base_id": { "type": "string", "description": "知识库 ID" },
                "keyword": { "type": "string", "description": "文件名关键字（可选）" },
                "page": { "type": "integer", "description": "页码，从 1 开始", "default": 1, "minimum": 1 },
                "page_size": { "type": "integer", "description": "每页条数", "default": 20, "minimum": 1 }
              },
              "required": ["knowledge_base_id"]
            }
            """;

    public static final String GET_DOCUMENT_CONTENT = """
            {
              "type": "object",
              "properties": {
                "document_id": { "type": "string", "description": "文档 ID" }
              },
              "required": ["document_id"]
            }
            """;

    // ===== 以下 3 个为可选写入/删除工具：默认不注册，启用前必须增加权限校验与操作确认 =====

    public static final String UPLOAD_DOCUMENT = """
            {
              "type": "object",
              "properties": {
                "knowledge_base_id": { "type": "string", "description": "目标知识库 ID" },
                "file_name": { "type": "string", "description": "文件名" },
                "file_type": { "type": "string", "description": "文件扩展名，如 DOCX / PDF" },
                "file_content_base64": { "type": "string", "description": "文件内容 Base64 编码" },
                "confirm": { "type": "boolean", "description": "操作确认，必须为 true", "default": false }
              },
              "required": ["knowledge_base_id", "file_name", "file_content_base64", "confirm"]
            }
            """;

    public static final String GET_PARSE_STATUS = """
            {
              "type": "object",
              "properties": {
                "task_id": { "type": "string", "description": "解析任务 ID" }
              },
              "required": ["task_id"]
            }
            """;

    public static final String DELETE_DOCUMENT = """
            {
              "type": "object",
              "properties": {
                "document_id": { "type": "string", "description": "文档 ID" },
                "confirm": { "type": "boolean", "description": "删除确认，必须为 true", "default": false }
              },
              "required": ["document_id", "confirm"]
            }
            """;
}
