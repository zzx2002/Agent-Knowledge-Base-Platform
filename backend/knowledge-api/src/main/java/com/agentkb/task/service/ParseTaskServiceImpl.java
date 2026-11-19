package com.agentkb.task.service;

import com.agentkb.common.BusinessException;
import com.agentkb.common.PageResult;
import com.agentkb.security.KbAccessService;
import com.agentkb.task.entity.ParseTask;
import com.agentkb.task.mapper.ParseTaskMapper;
import com.agentkb.task.mq.ParseTaskMessage;
import com.agentkb.task.mq.ParseTaskProducer;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 解析任务服务实现：状态查询、重试、取消。
 */
@Service
public class ParseTaskServiceImpl extends ServiceImpl<ParseTaskMapper, ParseTask>
        implements ParseTaskService {

    private final ParseTaskProducer parseTaskProducer;
    private final KbAccessService accessService;

    public ParseTaskServiceImpl(ParseTaskProducer parseTaskProducer, KbAccessService accessService) {
        this.parseTaskProducer = parseTaskProducer;
        this.accessService = accessService;
    }

    @Override
    public ParseTask createTask(Long documentId, Long kbId) {
        ParseTask task = new ParseTask();
        task.setDocumentId(documentId);
        task.setKbId(kbId);
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setRetryCount(0);
        save(task);
        return task;
    }

    @Override
    public PageResult<ParseTask> page(Long kbId, String status, long current, long size) {
        LambdaQueryWrapper<ParseTask> query = Wrappers.<ParseTask>lambdaQuery();
        if (kbId != null) {
            accessService.require(kbId, KbAccessService.PERM_READ);
            query.eq(ParseTask::getKbId, kbId);
        } else {
            List<Long> readableIds = accessService.readableKbIds();
            if (readableIds != null && readableIds.isEmpty()) {
                return PageResult.of(new Page<ParseTask>(current, size), Collections.emptyList());
            }
            if (readableIds != null) {
                query.in(ParseTask::getKbId, readableIds);
            }
        }
        if (status != null && !status.isBlank()) {
            query.eq(ParseTask::getStatus, status);
        }
        query.orderByDesc(ParseTask::getCreatedAt);
        Page<ParseTask> pageReq = new Page<>(current, size);
        IPage<ParseTask> result = page(pageReq, query);
        return PageResult.of(result, result.getRecords());
    }

    @Override
    public ParseTask retry(Long id) {
        ParseTask task = getById(id);
        if (task == null) {
            throw BusinessException.notFound("解析任务不存在");
        }
        accessService.require(task.getKbId(), KbAccessService.PERM_WRITE);
        if ("PROCESSING".equals(task.getStatus())) {
            throw BusinessException.conflict("任务正在处理中，无法重试");
        }
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setErrorMessage(null);
        task.setRetryCount(task.getRetryCount() == null ? 1 : task.getRetryCount() + 1);
        task.setFinishedAt(null);
        updateById(task);

        ParseTaskMessage message = new ParseTaskMessage();
        message.setTaskId(task.getId());
        message.setDocumentId(task.getDocumentId());
        message.setKnowledgeBaseId(task.getKbId());
        parseTaskProducer.send(message);
        return task;
    }

    @Override
    public ParseTask cancel(Long id) {
        ParseTask task = getById(id);
        if (task == null) {
            throw BusinessException.notFound("解析任务不存在");
        }
        accessService.require(task.getKbId(), KbAccessService.PERM_WRITE);
        if ("COMPLETED".equals(task.getStatus()) || "FAILED".equals(task.getStatus())
                || "CANCELLED".equals(task.getStatus())) {
            throw BusinessException.conflict("任务已结束，无法取消");
        }
        task.setStatus("CANCELLED");
        task.setFinishedAt(LocalDateTime.now());
        updateById(task);
        return task;
    }
}
