package com.agentkb.document.service;

import com.agentkb.common.PageResult;
import com.agentkb.document.dto.DocumentDTO;
import com.agentkb.document.entity.Document;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档服务接口。
 */
public interface DocumentService extends IService<Document> {

    DocumentDTO upload(Long kbId, MultipartFile file);

    PageResult<DocumentDTO> page(Long kbId, String keyword, long current, long size);

    DocumentDTO detail(Long id);

    void delete(Long id);

    DocumentDTO reparse(Long id);
}
