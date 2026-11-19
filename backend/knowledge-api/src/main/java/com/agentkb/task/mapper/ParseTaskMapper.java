package com.agentkb.task.mapper;

import com.agentkb.task.entity.ParseTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 解析任务表 Mapper。
 */
@Mapper
public interface ParseTaskMapper extends BaseMapper<ParseTask> {
}
