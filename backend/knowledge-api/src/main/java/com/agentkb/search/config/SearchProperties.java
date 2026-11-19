package com.agentkb.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 检索链路配置：ES、Qdrant、Embedding、Reranker。
 */
@Component
@ConfigurationProperties(prefix = "kb.search")
public class SearchProperties {

    private Es es = new Es();
    private Qdrant qdrant = new Qdrant();
    private Embedding embedding = new Embedding();
    private Rerank rerank = new Rerank();

    public Es getEs() { return es; }
    public void setEs(Es es) { this.es = es; }
    public Qdrant getQdrant() { return qdrant; }
    public void setQdrant(Qdrant qdrant) { this.qdrant = qdrant; }
    public Embedding getEmbedding() { return embedding; }
    public void setEmbedding(Embedding embedding) { this.embedding = embedding; }
    public Rerank getRerank() { return rerank; }
    public void setRerank(Rerank rerank) { this.rerank = rerank; }

    public static class Es {
        private String url = "http://localhost:9200";
        private String username = "elastic";
        private String password = "";
        private String index = "kb_chunks";

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getIndex() { return index; }
        public void setIndex(String index) { this.index = index; }
    }

    public static class Qdrant {
        private String url = "http://localhost:6333";
        private String apiKey = "";
        private String collection = "kb_chunks";
        private int dimension = 1024;

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getCollection() { return collection; }
        public void setCollection(String collection) { this.collection = collection; }
        public int getDimension() { return dimension; }
        public void setDimension(int dimension) { this.dimension = dimension; }
    }

    public static class Embedding {
        private String url = "http://localhost:8002";
        private String model = "bge-m3";

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }

    public static class Rerank {
        private String url = "http://localhost:8003";
        private String model = "bge-reranker";
        private boolean enabled = true;

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
