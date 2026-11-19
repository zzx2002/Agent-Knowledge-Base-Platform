package com.agentkb.knowledge.controller;

import com.agentkb.common.PageResult;
import com.agentkb.common.R;
import com.agentkb.knowledge.dto.KbPermissionRequest;
import com.agentkb.knowledge.dto.KnowledgeBaseDTO;
import com.agentkb.knowledge.dto.KnowledgeBaseVO;
import com.agentkb.knowledge.entity.KbPermission;
import com.agentkb.knowledge.service.KbPermissionService;
import com.agentkb.knowledge.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库接口：/api/v1/knowledge-bases
 */
@Tag(name = "知识库管理")
@RestController
@RequestMapping("/api/v1/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final KbPermissionService permissionService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService,
                                   KbPermissionService permissionService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.permissionService = permissionService;
    }

    @Operation(summary = "创建知识库（自动归属当前用户）")
    @PostMapping
    public R<KnowledgeBaseVO> create(@Valid @RequestBody KnowledgeBaseDTO dto) {
        return R.ok(knowledgeBaseService.create(dto));
    }

    @Operation(summary = "知识库列表（仅返回当前用户有权访问的知识库）")
    @GetMapping
    public R<PageResult<KnowledgeBaseVO>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long page_size) {
        String finalName = (name != null && !name.isBlank()) ? name : keyword;
        long finalCurrent = page != null ? page : current;
        long finalSize = page_size != null ? page_size : size;
        return R.ok(knowledgeBaseService.page(finalName, status, finalCurrent, finalSize));
    }

    @Operation(summary = "知识库详情")
    @GetMapping("/{id}")
    public R<KnowledgeBaseVO> detail(@PathVariable Long id) {
        return R.ok(knowledgeBaseService.getDetail(id));
    }

    @Operation(summary = "更新知识库")
    @PutMapping("/{id}")
    public R<KnowledgeBaseVO> update(@PathVariable Long id,
                                     @Valid @RequestBody KnowledgeBaseDTO dto) {
        return R.ok(knowledgeBaseService.update(id, dto));
    }

    @Operation(summary = "删除知识库")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.delete(id);
        return R.ok();
    }

    @Operation(summary = "查询知识库授权列表")
    @GetMapping("/{id}/permissions")
    public R<List<KbPermission>> permissions(@PathVariable Long id) {
        return R.ok(permissionService.list(id));
    }

    @Operation(summary = "授予用户/角色对知识库的权限")
    @PostMapping("/{id}/permissions")
    public R<KbPermission> grant(@PathVariable Long id,
                                 @Valid @RequestBody KbPermissionRequest request) {
        return R.ok(permissionService.grant(id, request));
    }

    @Operation(summary = "撤销用户/角色对知识库的权限")
    @DeleteMapping("/{id}/permissions/{subjectType}/{subjectId}/{permission}")
    public R<Void> revoke(@PathVariable Long id,
                          @PathVariable String subjectType,
                          @PathVariable Long subjectId,
                          @PathVariable String permission) {
        permissionService.revoke(id, subjectType, subjectId, permission);
        return R.ok();
    }
}
