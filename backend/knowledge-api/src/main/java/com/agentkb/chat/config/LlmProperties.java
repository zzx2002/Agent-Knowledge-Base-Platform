package com.agentkb.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OpenAI 兼容聊天模型配置。
 */
@Component
@ConfigurationProperties(prefix = "kb.llm")
public class LlmProperties {

    private String baseUrl = "https://api.siliconflow.cn/v1";
    private String apiKey = "";
    private String model = "Qwen/Qwen2.5-7B-Instruct";
    private double temperature = 0.2;
    private int maxTokens = 1024;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
}
