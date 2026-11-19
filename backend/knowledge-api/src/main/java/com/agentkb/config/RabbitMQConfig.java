package com.agentkb.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 解析任务交换机 / 队列声明。
 */
@Configuration
public class RabbitMQConfig {

    public static final String PARSE_EXCHANGE = "kb.parse.exchange";
    public static final String PARSE_QUEUE = "kb.parse.queue";
    public static final String PARSE_ROUTING_KEY = "kb.parse.created";

    @Bean
    public TopicExchange parseExchange() {
        return new TopicExchange(PARSE_EXCHANGE, true, false);
    }

    @Bean
    public Queue parseQueue() {
        return new Queue(PARSE_QUEUE, true);
    }

    @Bean
    public Binding parseBinding() {
        return BindingBuilder.bind(parseQueue()).to(parseExchange()).with(PARSE_ROUTING_KEY);
    }
}
