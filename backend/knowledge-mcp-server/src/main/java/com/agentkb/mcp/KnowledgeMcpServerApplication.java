package com.agentkb.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 独立 MCP Server 启动类。
 *
 * <p>设计约束（架构文档 7.1 / 18.3）：
 * <ul>
 *     <li>不直接访问任何数据库；所有数据均通过 knowledge-api 的 REST 接口获取。</li>
 *     <li>对外仅暴露 MCP Streamable HTTP 端点 /mcp 与 actuator 健康检查。</li>
 * </ul>
 */
@SpringBootApplication
public class KnowledgeMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeMcpServerApplication.class, args);
    }
}
