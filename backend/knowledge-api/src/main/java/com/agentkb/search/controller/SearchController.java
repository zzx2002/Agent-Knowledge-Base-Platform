package com.agentkb.search.controller;

import com.agentkb.common.R;
import com.agentkb.search.dto.SearchRequest;
import com.agentkb.search.dto.SearchResponseDTO;
import com.agentkb.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 检索接口：/api/v1/search、/api/v1/search/debug。
 */
@Tag(name = "检索接口")
@RestController
@RequestMapping("/api/v1")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(summary = "混合检索")
    @PostMapping("/search")
    public R<SearchResponseDTO> search(@Valid @RequestBody SearchRequest request) {
        return R.ok(searchService.search(request, false));
    }

    @Operation(summary = "检索调试（返回各阶段召回明细）")
    @GetMapping("/search/debug")
    public R<SearchResponseDTO> debug(@Valid SearchRequest request) {
        return R.ok(searchService.search(request, true));
    }
}
