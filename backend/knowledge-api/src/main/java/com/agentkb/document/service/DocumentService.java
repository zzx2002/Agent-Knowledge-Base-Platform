package com.agentkb.document.service;

import com.agentkb.common.PageResult;
import com.agentkb.document.dto.DocumentDTO;
import com.agentkb.document.dto.DocumentPreviewDTO;
import com.agentkb.document.entity.Document;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文档服务接口。
 */
public interface DocumentService extends IService<Document> {

    DocumentDTO upload(Long kbId, MultipartFile file);

    PageResult<DocumentDTO> page(Long kbId, String keyword, long current, long size);

    DocumentDTO detail(Long id);

    Map<String, Object> content(Long id);

    /** 原文档预览（Office 自动转换为 PDF）。 */
    DocumentPreviewDTO preview(Long id);

    PageResult<Map<String, Object>> chunks(Long id, String chunkType, Integer page, long current, long size);

    void delete(Long id);

    DocumentDTO reparse(Long id);
}
