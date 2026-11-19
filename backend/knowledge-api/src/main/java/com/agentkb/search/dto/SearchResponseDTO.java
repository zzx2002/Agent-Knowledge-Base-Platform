package com.agentkb.search.dto;

import java.util.List;
import java.util.Map;

/**
 * 检索响应，debug 字段用于检索调试接口。
 */
public class SearchResponseDTO {

    private String query;
    private List<SearchHitDTO> hits;
    private Map<String, Object> debug;

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public List<SearchHitDTO> getHits() { return hits; }
    public void setHits(List<SearchHitDTO> hits) { this.hits = hits; }
    public Map<String, Object> getDebug() { return debug; }
    public void setDebug(Map<String, Object> debug) { this.debug = debug; }
}
