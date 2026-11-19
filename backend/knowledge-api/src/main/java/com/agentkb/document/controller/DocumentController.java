package com.agentkb.document.controller;

import com.agentkb.common.BusinessException;
import com.agentkb.common.PageResult;
import com.agentkb.common.R;
import com.agentkb.document.dto.DocumentDTO;
import com.agentkb.document.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 文档接口：/api/v1/documents/*
 */
@Tag(name = "文档管理")
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Operation(summary = "上传文档（单文件/批量，按知识库隔离）")
    @PostMapping("/upload")
    public R<List<DocumentDTO>> upload(
            @RequestParam(value = "kbId", required = false) Long kbId,
            @RequestParam(value = "knowledge_base_id", required = false) Long knowledgeBaseId,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        Long targetKbId = kbId != null ? kbId : knowledgeBaseId;
        if (targetKbId == null) {
            throw BusinessException.badRequest("知识库 ID 不能为空");
        }
        List<MultipartFile> uploadFiles = files != null && !files.isEmpty()
                ? files
                : (file == null ? Collections.emptyList() : List.of(file));
        if (uploadFiles.isEmpty()) {
            throw BusinessException.badRequest("上传文件不能为空");
        }
        List<DocumentDTO> result = new ArrayList<>();
        for (MultipartFile uploadFile : uploadFiles) {
            result.add(documentService.upload(targetKbId, uploadFile));
        }
        return R.ok(result);
    }

    @Operation(summary = "文档列表（仅当前用户有权访问的知识库）")
    @GetMapping
    public R<PageResult<DocumentDTO>> list(
            @RequestParam(required = false) Long kbId,
            @RequestParam(value = "knowledge_base_id", required = false) Long knowledgeBaseId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long page_size) {
        Long targetKbId = kbId != null ? kbId : knowledgeBaseId;
        long finalCurrent = page != null ? page : current;
        long finalSize = page_size != null ? page_size : size;
        return R.ok(documentService.page(targetKbId, keyword, finalCurrent, finalSize));
    }

    @Operation(summary = "文档详情")
    @GetMapping("/{id}")
    public R<DocumentDTO> detail(@PathVariable Long id) {
        return R.ok(documentService.detail(id));
    }

    @Operation(summary = "获取文档解析内容")
    @GetMapping("/{id}/content")
    public R<Map<String, Object>> content(@PathVariable Long id) {
        return R.ok(documentService.content(id));
    }

    @Operation(summary = "删除文档")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return R.ok();
    }

    @Operation(summary = "重新解析")
    @PostMapping("/{id}/reparse")
    public R<DocumentDTO> reparse(@PathVariable Long id) {
        return R.ok(documentService.reparse(id));
    }

    @Operation(summary = "获取文档分块（分页）")
    @GetMapping("/{id}/chunks")
    public R<PageResult<Map<String, Object>>> chunks(
            @PathVariable Long id,
            @RequestParam(required = false) String chunk_type,
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long page_no,
            @RequestParam(required = false) Long page_size) {
        long finalCurrent = page_no != null ? page_no : current;
        long finalSize = page_size != null ? page_size : size;
        return R.ok(documentService.chunks(id, chunk_type, page, finalCurrent, finalSize));
    }
}
