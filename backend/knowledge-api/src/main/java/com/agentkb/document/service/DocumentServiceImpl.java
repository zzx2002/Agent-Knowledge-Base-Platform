package com.agentkb.document.service;

import com.agentkb.common.BusinessException;
import com.agentkb.common.PageResult;
import com.agentkb.common.mapper.LogicalDeleteMapper;
import com.agentkb.document.dto.DocumentDTO;
import com.agentkb.document.entity.Document;
import com.agentkb.document.mapper.DocumentMapper;
import com.agentkb.knowledge.service.KnowledgeBaseService;
import com.agentkb.storage.MinioStorageService;
import com.agentkb.security.KbAccessService;
import com.agentkb.task.entity.ParseTask;
import com.agentkb.task.mq.ParseTaskMessage;
import com.agentkb.task.mq.ParseTaskProducer;
import com.agentkb.task.service.ParseTaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文档上传、列表、详情、删除、重新解析。
 * 上传流程：MinIO 保存原文件 -> PostgreSQL 写入元数据 -> 创建解析任务 -> 投递 RabbitMQ。
 */
@Service
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, Document> implements DocumentService {

    private final KnowledgeBaseService knowledgeBaseService;
    private final MinioStorageService minioStorageService;
    private final ParseTaskService parseTaskService;
    private final ParseTaskProducer parseTaskProducer;
    private final KbAccessService accessService;
    private final LogicalDeleteMapper logicalDeleteMapper;

    @Value("${minio.bucket}")
    private String bucket;

    public DocumentServiceImpl(KnowledgeBaseService knowledgeBaseService,
                               MinioStorageService minioStorageService,
                               ParseTaskService parseTaskService,
                               ParseTaskProducer parseTaskProducer,
                               KbAccessService accessService,
                               LogicalDeleteMapper logicalDeleteMapper) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.minioStorageService = minioStorageService;
        this.parseTaskService = parseTaskService;
        this.parseTaskProducer = parseTaskProducer;
        this.accessService = accessService;
        this.logicalDeleteMapper = logicalDeleteMapper;
    }

    @Override
    public DocumentDTO upload(Long kbId, MultipartFile file) {
        if (kbId == null) {
            throw BusinessException.badRequest("kbId 不能为空");
        }
        if (knowledgeBaseService.getById(kbId) == null) {
            throw BusinessException.notFound("知识库不存在");
        }
        accessService.require(kbId, KbAccessService.PERM_WRITE);
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("上传文件不能为空");
        }

        String originalName = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
        String fileType = resolveFileType(originalName);
        String objectKey = minioStorageService.buildObjectKey(kbId, originalName);

        try {
            minioStorageService.upload(objectKey, file);
        } catch (Exception e) {
            throw new BusinessException(500, "文件存储失败: " + e.getMessage());
        }

        Document document = new Document();
        document.setKbId(kbId);
        document.setFileName(originalName);
        document.setFileType(fileType);
        document.setFileSize(file.getSize());
        document.setStorageKey(objectKey);
        document.setStoragePath(objectKey);
        document.setBucket(bucket);
        document.setStatus("PENDING");
        document.setVersion(1);
        save(document);

        ParseTask task = parseTaskService.createTask(document.getId(), kbId);
        ParseTaskMessage message = new ParseTaskMessage();
        message.setTaskId(task.getId());
        message.setDocumentId(document.getId());
        message.setKnowledgeBaseId(kbId);
        message.setStorageKey(objectKey);
        message.setFileName(originalName);
        message.setFileType(fileType);
        parseTaskProducer.send(message);

        return toDTO(document);
    }

    @Override
    public PageResult<DocumentDTO> page(Long kbId, String keyword, long current, long size) {
        LambdaQueryWrapper<Document> query = Wrappers.<Document>lambdaQuery();
        if (kbId != null) {
            accessService.require(kbId, KbAccessService.PERM_READ);
            query.eq(Document::getKbId, kbId);
        } else {
            List<Long> readableIds = accessService.readableKbIds();
            if (readableIds != null && readableIds.isEmpty()) {
                return PageResult.of(new Page<Document>(current, size), Collections.emptyList());
            }
            if (readableIds != null) {
                query.in(Document::getKbId, readableIds);
            }
        }
        if (keyword != null && !keyword.isBlank()) {
            query.like(Document::getFileName, keyword);
        }
        query.orderByDesc(Document::getCreatedAt);
        Page<Document> pageReq = new Page<>(current, size);
        IPage<Document> result = page(pageReq, query);
        return PageResult.of(result, result.getRecords().stream().map(this::toDTO).collect(Collectors.toList()));
    }

    @Override
    public DocumentDTO detail(Long id) {
        Document document = getById(id);
        if (document == null) {
            throw BusinessException.notFound("文档不存在");
        }
        accessService.require(document.getKbId(), KbAccessService.PERM_READ);
        return toDTO(document);
    }

    @Override
    public void delete(Long id) {
        Document document = getById(id);
        if (document == null) {
            throw BusinessException.notFound("文档不存在");
        }
        accessService.require(document.getKbId(), KbAccessService.PERM_DELETE);
        // MinIO 对象按约定允许物理删除；数据库记录全部逻辑删除。
        if (document.getStorageKey() != null) {
            minioStorageService.remove(document.getStorageKey());
        }
        logicalDeleteMapper.deleteTasksByDocumentId(id);
        logicalDeleteMapper.deleteVersionsByDocumentId(id);
        logicalDeleteMapper.deleteElementsByDocumentId(id);
        logicalDeleteMapper.deleteChunksByDocumentId(id);
        removeById(id);
    }

    @Override
    public DocumentDTO reparse(Long id) {
        Document document = getById(id);
        if (document == null) {
            throw BusinessException.notFound("文档不存在");
        }
        accessService.require(document.getKbId(), KbAccessService.PERM_WRITE);
        ParseTask task = parseTaskService.createTask(document.getId(), document.getKbId());
        ParseTaskMessage message = new ParseTaskMessage();
        message.setTaskId(task.getId());
        message.setDocumentId(document.getId());
        message.setKnowledgeBaseId(document.getKbId());
        message.setStorageKey(document.getStorageKey());
        message.setFileName(document.getFileName());
        message.setFileType(document.getFileType());
        parseTaskProducer.send(message);

        document.setStatus("PENDING");
        updateById(document);
        return toDTO(document);
    }

    private String resolveFileType(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "UNKNOWN";
        }
        return fileName.substring(index + 1).toUpperCase();
    }

    private DocumentDTO toDTO(Document entity) {
        DocumentDTO dto = new DocumentDTO();
        dto.setId(entity.getId());
        dto.setKbId(entity.getKbId());
        dto.setFileName(entity.getFileName());
        dto.setFileType(entity.getFileType());
        dto.setFileSize(entity.getFileSize());
        dto.setStorageKey(entity.getStorageKey());
        dto.setStatus(entity.getStatus());
        dto.setUploaderId(entity.getUploaderId());
        dto.setVersion(entity.getVersion());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
