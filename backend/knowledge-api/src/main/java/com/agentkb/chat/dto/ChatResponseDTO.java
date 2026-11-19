package com.agentkb.chat.dto;

import java.util.List;

/**
 * RAG 问答响应。
 */
public class ChatResponseDTO {

    private String answer;
    private List<ChatSourceDTO> sources;

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public List<ChatSourceDTO> getSources() { return sources; }
    public void setSources(List<ChatSourceDTO> sources) { this.sources = sources; }
}
