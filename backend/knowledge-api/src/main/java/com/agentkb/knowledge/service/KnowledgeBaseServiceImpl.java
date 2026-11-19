package com.agentkb.knowledge.service;

import com.agentkb.common.BusinessException;
import com.agentkb.common.PageResult;
import com.agentkb.common.mapper.LogicalDeleteMapper;
import com.agentkb.knowledge.dto.KnowledgeBaseDTO;
import com.agentkb.knowledge.dto.KnowledgeBaseVO;
import com.agentkb.knowledge.entity.KnowledgeBase;
import com.agentkb.knowledge.mapper.KnowledgeBaseMapper;
import com.agentkb.security.KbAccessService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库 CRUD 实现，带归属与数据隔离。
 */
@Service
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase>
        implements KnowledgeBaseService {

    private final KbAccessService accessService;
    private final LogicalDeleteMapper logicalDeleteMapper;

    public KnowledgeBaseServiceImpl(KbAccessService accessService,
                                    LogicalDeleteMapper logicalDeleteMapper) {
        this.accessService = accessService;
        this.logicalDeleteMapper = logicalDeleteMapper;
    }

    @Override
    public KnowledgeBaseVO create(KnowledgeBaseDTO dto) {
        accessService.requireCanCreateKnowledgeBase();
        KnowledgeBase entity = buildEntity(dto);
        entity.setOwnerId(accessService.currentUserId());
        entity.setStatus("ACTIVE");
        save(entity);
        return toVO(entity);
    }

    @Override
    public KnowledgeBaseVO update(Long id, KnowledgeBaseDTO dto) {
        accessService.require(id, KbAccessService.PERM_ADMIN);
        KnowledgeBase entity = getById(id);
        if (entity == null) {
            throw BusinessException.notFound("知识库不存在");
        }
        if (dto.getName() != null && !dto.getName().isBlank()) {
            entity.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        if (dto.getEmbeddingModel() != null && !dto.getEmbeddingModel().isBlank()) {
            entity.setEmbeddingModel(dto.getEmbeddingModel());
        }
        if (dto.getChunkStrategy() != null && !dto.getChunkStrategy().isBlank()) {
            entity.setChunkStrategy(dto.getChunkStrategy());
        }
        if (dto.getChunkSize() != null) {
            entity.setChunkSize(dto.getChunkSize());
        }
        if (dto.getChunkOverlap() != null) {
            entity.setChunkOverlap(dto.getChunkOverlap());
        }
        updateById(entity);
        return toVO(entity);
    }

    @Override
    public void delete(Long id) {
        accessService.require(id, KbAccessService.PERM_ADMIN);
        if (getById(id) == null) {
            throw BusinessException.notFound("知识库不存在");
        }
        // 关联业务数据全部逻辑删除；权限记录允许物理删除。
        logicalDeleteMapper.deleteTasksByKbId(id);
        logicalDeleteMapper.deleteChunksByKbId(id);
        logicalDeleteMapper.deleteElementsByKbId(id);
        logicalDeleteMapper.deleteVersionsByKbId(id);
        logicalDeleteMapper.deleteDocumentsByKbId(id);
        logicalDeleteMapper.deletePermissionsByKbId(id);
        removeById(id);
    }

    @Override
    public KnowledgeBaseVO getDetail(Long id) {
        accessService.require(id, KbAccessService.PERM_READ);
        KnowledgeBase entity = getById(id);
        if (entity == null) {
            throw BusinessException.notFound("知识库不存在");
        }
        return toVO(entity);
    }

    @Override
    public PageResult<KnowledgeBaseVO> page(String name, String status, long current, long size) {
        LambdaQueryWrapper<KnowledgeBase> query = Wrappers.<KnowledgeBase>lambdaQuery();
        if (name != null && !name.isBlank()) {
            query.like(KnowledgeBase::getName, name);
        }
        if (status != null && !status.isBlank()) {
            query.eq(KnowledgeBase::getStatus, status);
        }

        List<Long> readableIds = accessService.readableKbIds();
        if (readableIds != null && readableIds.isEmpty()) {
            return PageResult.of(new Page<KnowledgeBase>(current, size), Collections.emptyList());
        }
        if (readableIds != null) {
            query.in(KnowledgeBase::getId, readableIds);
        }

        query.orderByDesc(KnowledgeBase::getCreatedAt);
        Page<KnowledgeBase> pageReq = new Page<>(current, size);
        IPage<KnowledgeBase> result = page(pageReq, query);
        List<KnowledgeBaseVO> records = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(result, records);
    }

    @Override
    public List<KnowledgeBaseVO> listAll() {
        LambdaQueryWrapper<KnowledgeBase> query = Wrappers.<KnowledgeBase>lambdaQuery()
                .orderByDesc(KnowledgeBase::getCreatedAt);
        List<Long> readableIds = accessService.readableKbIds();
        if (readableIds != null && readableIds.isEmpty()) {
            return Collections.emptyList();
        }
        if (readableIds != null) {
            query.in(KnowledgeBase::getId, readableIds);
        }
        return list(query).stream().map(this::toVO).collect(Collectors.toList());
    }

    private KnowledgeBase buildEntity(KnowledgeBaseDTO dto) {
        KnowledgeBase entity = new KnowledgeBase();
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setEmbeddingModel(isBlank(dto.getEmbeddingModel()) ? "bge-m3" : dto.getEmbeddingModel());
        entity.setChunkStrategy(isBlank(dto.getChunkStrategy()) ? "STRUCTURE_FIRST" : dto.getChunkStrategy());
        entity.setChunkSize(dto.getChunkSize() == null ? 800 : dto.getChunkSize());
        entity.setChunkOverlap(dto.getChunkOverlap() == null ? 100 : dto.getChunkOverlap());
        return entity;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private KnowledgeBaseVO toVO(KnowledgeBase entity) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setEmbeddingModel(entity.getEmbeddingModel());
        vo.setChunkStrategy(entity.getChunkStrategy());
        vo.setChunkSize(entity.getChunkSize());
        vo.setChunkOverlap(entity.getChunkOverlap());
        vo.setOwnerId(entity.getOwnerId());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
