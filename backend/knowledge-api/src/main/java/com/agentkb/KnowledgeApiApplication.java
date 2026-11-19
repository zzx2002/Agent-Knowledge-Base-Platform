package com.agentkb;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 启动类：业务服务 knowledge-api。
 */
@SpringBootApplication
@MapperScan({
        "com.agentkb.auth.mapper",
        "com.agentkb.knowledge.mapper",
        "com.agentkb.document.mapper",
        "com.agentkb.task.mapper",
        "com.agentkb.security.mapper",
        "com.agentkb.common.mapper"
})
public class KnowledgeApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeApiApplication.class, args);
    }
}
