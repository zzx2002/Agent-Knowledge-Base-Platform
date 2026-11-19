package com.agentkb.task.mq;

/**
 * 解析任务消费者扩展点。
 *
 * <p>真实文档解析由独立的 Python Parser Worker 从 RabbitMQ 队列消费，
 * 并通过 /api/v1/tasks/{id}/callback 回写状态。此类刻意不注册 @RabbitListener，
 * 避免 Java 端与 Python Worker 争抢同一队列消息。
 */
public final class ParseTaskConsumer {

    private ParseTaskConsumer() {
    }

    /** 供后续需要 Java 侧本地解析时接入，当前不使用。 */
    public static void handle(ParseTaskMessage message) {
        // 扩展点：在此接入 Java 侧解析器或转发逻辑。
    }
}
