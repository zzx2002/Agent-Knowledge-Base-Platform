package com.agentkb.task.service;

import com.agentkb.common.PageResult;
import com.agentkb.task.entity.ParseTask;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 解析任务查询与状态流转接口。
 */
public interface ParseTaskService extends IService<ParseTask> {

    ParseTask createTask(Long documentId, Long kbId);

    PageResult<ParseTask> page(Long kbId, String status, long current, long size);

    ParseTask retry(Long id);

    ParseTask cancel(Long id);
}
