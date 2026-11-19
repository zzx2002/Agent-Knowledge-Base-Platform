package com.agentkb.document.service;

import com.agentkb.common.BusinessException;
import com.agentkb.common.PageResult;
import com.agentkb.common.mapper.LogicalDeleteMapper;
import com.agentkb.document.dto.DocumentDTO;
import com.agentkb.document.dto.DocumentPreviewDTO;
import com.agentkb.document.entity.Document;
import com.agentkb.document.entity.DocumentChunk;
import com.agentkb.document.entity.DocumentElement;
import com.agentkb.document.mapper.DocumentChunkMapper;
import com.agentkb.document.mapper.DocumentElementMapper;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final DocumentElementMapper elementMapper;
    private final DocumentChunkMapper chunkMapper;

    @Value("${minio.bucket}")
    private String bucket;

    public DocumentServiceImpl(KnowledgeBaseService knowledgeBaseService,
                               MinioStorageService minioStorageService,
                               ParseTaskService parseTaskService,
                               ParseTaskProducer parseTaskProducer,
                               KbAccessService accessService,
                               LogicalDeleteMapper logicalDeleteMapper,
                               DocumentElementMapper elementMapper,
                               DocumentChunkMapper chunkMapper) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.minioStorageService = minioStorageService;
        this.parseTaskService = parseTaskService;
        this.parseTaskProducer = parseTaskProducer;
        this.accessService = accessService;
        this.logicalDeleteMapper = logicalDeleteMapper;
        this.elementMapper = elementMapper;
        this.chunkMapper = chunkMapper;
    }

    @Override
    public DocumentDTO upload(Long kbId, MultipartFile file) {
        if (kbId == null) {
            throw BusinessException.badRequest("kbId 不能为空");
        }
        var kb = knowledgeBaseService.getById(kbId);
        if (kb == null) {
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
        message.setAttempt(1);
        message.setChunkConfig(buildChunkConfig(kb));
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
    public Map<String, Object> content(Long id) {
        Document document = getById(id);
        if (document == null) {
            throw BusinessException.notFound("文档不存在");
        }
        accessService.require(document.getKbId(), KbAccessService.PERM_READ);
        long version = document.getVersion() == null ? 1L : document.getVersion().longValue();
        List<DocumentElement> elements = elementMapper.selectList(
                Wrappers.<DocumentElement>lambdaQuery()
                        .eq(DocumentElement::getDocumentId, id)
                        .eq(DocumentElement::getVersionId, version)
                        .orderByAsc(DocumentElement::getCreatedAt));

        StringBuilder fullText = new StringBuilder();
        for (DocumentElement element : elements) {
            if (element.getContent() == null || element.getContent().isBlank()) {
                continue;
            }
            if (fullText.length() > 0) {
                fullText.append('\n');
            }
            fullText.append(element.getContent());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("document_id", id);
        result.put("version", version);
        result.put("status", document.getStatus());
        result.put("content", fullText.toString());
        result.put("elements", elements.stream().map(this::toElementMap).collect(Collectors.toList()));
        return result;
    }

    @Override
    public DocumentPreviewDTO preview(Long id) {
        Document document = getById(id);
        if (document == null) {
            throw BusinessException.notFound("文档不存在");
        }
        accessService.require(document.getKbId(), KbAccessService.PERM_READ);

        byte[] bytes;
        try {
            bytes = minioStorageService.download(
                    document.getStorageKey() == null ? document.getStoragePath() : document.getStorageKey());
        } catch (Exception e) {
            throw new BusinessException(500, "读取原文件失败: " + e.getMessage());
        }

        String fileName = document.getFileName() == null ? "document" : document.getFileName();
        String ext = extension(fileName);
        if (ext.matches("png|jpg|jpeg|pdf")) {
            return new DocumentPreviewDTO(bytes, contentType(ext), fileName);
        }
        if (ext.matches("txt|md|markdown|csv")) {
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (text.contains("\uFFFD")) {
                text = new String(bytes, java.nio.charset.Charset.forName("GBK"));
            }
            return new DocumentPreviewDTO(text.getBytes(StandardCharsets.UTF_8), "text/plain;charset=UTF-8", fileName);
        }
        if (Set.of("doc", "docx", "ppt", "pptx", "xls", "xlsx").contains(ext)) {
            return new DocumentPreviewDTO(convertOfficeToPdf(bytes, fileName), "application/pdf", fileName);
        }
        throw BusinessException.badRequest("该格式暂不支持在线预览，请下载后查看");
    }

    private byte[] convertOfficeToPdf(byte[] bytes, String fileName) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("kb-preview-");
            String safeName = fileName.replace('\\', '_').replace('/', '_');
            Path source = tempDir.resolve(safeName);
            Files.write(source, bytes);

            String soffice = System.getenv().getOrDefault("LIBREOFFICE_PATH", "soffice");
            ProcessBuilder builder = new ProcessBuilder(
                    soffice, "--headless", "--norestore", "--convert-to", "pdf",
                    "--outdir", tempDir.toString(), source.toString());
            Process process = builder.redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new BusinessException(500, "文档转换超时");
            }
            if (process.exitValue() != 0) {
                throw new BusinessException(500, "文档转换失败: " + output.trim());
            }

            String base = safeName.contains(".") ? safeName.substring(0, safeName.lastIndexOf('.')) : safeName;
            Path pdf = tempDir.resolve(base + ".pdf");
            if (!Files.exists(pdf)) {
                try (Stream<Path> stream = Files.list(tempDir)) {
                    pdf = stream.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
                            .findFirst().orElse(null);
                }
            }
            if (pdf == null || !Files.exists(pdf)) {
                throw new BusinessException(500, "文档转换完成但未生成 PDF");
            }
            return Files.readAllBytes(pdf);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "文档转换失败: " + e.getMessage());
        } finally {
            if (tempDir != null) {
                deleteRecursively(tempDir);
            }
        }
    }

    private void deleteRecursively(Path path) {
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String contentType(String ext) {
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            default -> "text/plain;charset=UTF-8";
        };
    }

    @Override
    public PageResult<Map<String, Object>> chunks(Long id, String chunkType, Integer page,
                                                  long current, long size) {
        Document document = getById(id);
        if (document == null) {
            throw BusinessException.notFound("文档不存在");
        }
        accessService.require(document.getKbId(), KbAccessService.PERM_READ);
        long version = document.getVersion() == null ? 1L : document.getVersion().longValue();

        var query = Wrappers.<DocumentChunk>lambdaQuery()
                .eq(DocumentChunk::getDocumentId, id)
                .eq(DocumentChunk::getVersionId, version);
        if (chunkType != null && !chunkType.isBlank()) {
            query.eq(DocumentChunk::getChunkType, chunkType);
        }
        if (page != null) {
            query.le(DocumentChunk::getPageStart, page).ge(DocumentChunk::getPageEnd, page);
        }
        query.orderByAsc(DocumentChunk::getChunkIndex);
        IPage<DocumentChunk> result = chunkMapper.selectPage(new Page<>(current, size), query);
        return PageResult.of(result, result.getRecords().stream()
                .map(this::toChunkMap)
                .collect(Collectors.toList()));
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
        var kb = knowledgeBaseService.getById(document.getKbId());
        ParseTask task = parseTaskService.createTask(document.getId(), document.getKbId());
        ParseTaskMessage message = new ParseTaskMessage();
        message.setTaskId(task.getId());
        message.setDocumentId(document.getId());
        message.setKnowledgeBaseId(document.getKbId());
        message.setStorageKey(document.getStorageKey());
        message.setFileName(document.getFileName());
        message.setFileType(document.getFileType());
        int attempt = task.getRetryCount() == null ? 1 : task.getRetryCount() + 1;
        message.setAttempt(attempt);
        message.setChunkConfig(kb == null ? null : buildChunkConfig(kb));
        parseTaskProducer.send(message);

        document.setStatus("PENDING");
        document.setVersion((document.getVersion() == null ? 1 : document.getVersion()) + 1);
        updateById(document);
        return toDTO(document);
    }

    private Map<String, Object> toElementMap(DocumentElement element) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("element_id", element.getElementId());
        map.put("type", element.getType());
        map.put("content", element.getContent());
        map.put("level", element.getLevel());
        map.put("page", element.getPage());
        map.put("page_start", element.getPageStart());
        map.put("page_end", element.getPageEnd());
        map.put("heading_path", element.getHeadingPath());
        map.put("table_data", element.getTableData());
        map.put("metadata", element.getMetadata());
        return map;
    }

    private Map<String, Object> toChunkMap(DocumentChunk chunk) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("chunk_index", chunk.getChunkIndex());
        map.put("chunk_type", chunk.getChunkType());
        map.put("content", chunk.getContent());
        map.put("token_count", chunk.getTokenCount());
        map.put("heading_path", chunk.getHeadingPath());
        map.put("page_start", chunk.getPageStart());
        map.put("page_end", chunk.getPageEnd());
        map.put("element_ids", chunk.getElementIds());
        map.put("metadata", chunk.getMetadata());
        return map;
    }

    private Map<String, Object> buildChunkConfig(com.agentkb.knowledge.entity.KnowledgeBase kb) {
        Map<String, Object> config = new HashMap<>();
        config.put("chunk_size", kb.getChunkSize());
        config.put("chunk_overlap", kb.getChunkOverlap());
        config.put("chunk_strategy", kb.getChunkStrategy());
        config.put("parent_child_enabled", true);
        return config;
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
