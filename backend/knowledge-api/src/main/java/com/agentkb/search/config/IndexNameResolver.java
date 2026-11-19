package com.agentkb.search.config;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 按 embedding 模型派生 ES 索引与 Qdrant 集合名，避免不同向量空间混用。
 */
@Component
public class IndexNameResolver {

    private final SearchProperties properties;

    public IndexNameResolver(SearchProperties properties) {
        this.properties = properties;
    }

    public String esIndex() {
        return withModelSuffix(properties.getEs().getIndex());
    }

    public String qdrantCollection() {
        return withModelSuffix(properties.getQdrant().getCollection());
    }

    public String modelSlug() {
        String model = properties.getEmbedding().getModel();
        if (model == null || model.isBlank()) {
            return "default";
        }
        return model.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    private String withModelSuffix(String base) {
        return base + "_" + modelSlug();
    }
}
