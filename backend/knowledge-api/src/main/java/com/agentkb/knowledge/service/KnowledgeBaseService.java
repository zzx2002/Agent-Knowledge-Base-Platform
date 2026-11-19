package com.agentkb.knowledge.service;

import com.agentkb.common.PageResult;
import com.agentkb.knowledge.dto.KnowledgeBaseDTO;
import com.agentkb.knowledge.dto.KnowledgeBaseVO;
import com.agentkb.knowledge.entity.KnowledgeBase;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 知识库服务接口。
 */
public interface KnowledgeBaseService extends IService<KnowledgeBase> {

    KnowledgeBaseVO create(KnowledgeBaseDTO dto);

    KnowledgeBaseVO update(Long id, KnowledgeBaseDTO dto);

    void delete(Long id);

    KnowledgeBaseVO getDetail(Long id);

    PageResult<KnowledgeBaseVO> page(String name, String status, long current, long size);

    List<KnowledgeBaseVO> listAll();
}
