package com.agentkb.search.controller;

import com.agentkb.common.BusinessException;
import com.agentkb.common.R;
import com.agentkb.security.KbAccessService;
import com.agentkb.search.dto.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 检索接口：/api/v1/search、/api/v1/search/debug
 * 第一阶段仅定义路径，返回清晰的 501（混合检索属于第三阶段）。
 */
@Tag(name = "检索接口（待实现）")
@RestController
@RequestMapping("/api/v1")
public class SearchController {

    private final KbAccessService accessService;

    public SearchController(KbAccessService accessService) {
        this.accessService = accessService;
    }

    @Operation(summary = "混合检索（第三阶段实现）")
    @PostMapping("/search")
    public R<Void> search(@Valid @RequestBody SearchRequest request) {
        if (request.getKnowledgeBaseId() != null) {
            accessService.require(request.getKnowledgeBaseId(), KbAccessService.PERM_READ);
        }
        throw BusinessException.notImplemented("混合检索将在第三阶段（Elasticsearch + Qdrant + Reranker）实现");
    }

    @Operation(summary = "检索调试（第三阶段实现）")
    @GetMapping("/search/debug")
    public R<Void> debug() {
        throw BusinessException.notImplemented("检索调试将在第三阶段实现");
    }
}
