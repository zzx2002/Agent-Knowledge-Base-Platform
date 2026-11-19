package com.agentkb.task.mq;

import com.agentkb.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 解析任务生产者（第一阶段骨架）。
 * 投递失败仅记录日志：文档已成功入库，任务保持 PENDING，可后续补偿重试。
 */
@Component
public class ParseTaskProducer {

    private static final Logger log = LoggerFactory.getLogger(ParseTaskProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public ParseTaskProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void send(ParseTaskMessage message) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.PARSE_EXCHANGE,
                    RabbitMQConfig.PARSE_ROUTING_KEY,
                    message);
            log.info("解析任务已投递: {}", message);
        } catch (Exception e) {
            log.warn("解析任务投递失败（任务保留 PENDING，可重试）: {}, error={}", message, e.getMessage());
        }
    }
}
