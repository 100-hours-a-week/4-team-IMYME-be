package com.imyme.mine.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 설정
 * - DirectExchange: 라우팅 키 기반 메시지 전달
 * - Queue: 메시지 저장소 (STT Request/Response, Feedback Request/Response, DLQ)
 * - Binding: Exchange와 Queue 연결
 * - Jackson2JsonMessageConverter: JSON 직렬화/역직렬화
 * - Manual Ack: 메시지 처리 확인 후 수동 승인 (신뢰성 보장)
 */
@Configuration
public class RabbitMQConfig {

    // ===== Exchange & Queue & Routing Key 상수 =====

    public static final String PVP_DIRECT_EXCHANGE = "pvp.direct";

    // Phase 1: STT (Speech-to-Text)
    public static final String PVP_STT_REQUEST_QUEUE = "pvp.stt.request";
    public static final String PVP_STT_RESPONSE_QUEUE = "pvp.stt.response";
    public static final String PVP_STT_REQUEST_ROUTING_KEY = "pvp.stt.request";
    public static final String PVP_STT_RESPONSE_ROUTING_KEY = "pvp.stt.response";

    // Phase 2: Feedback
    public static final String PVP_FEEDBACK_REQUEST_QUEUE = "pvp.feedback.request";
    public static final String PVP_FEEDBACK_RESPONSE_QUEUE = "pvp.feedback.response";
    public static final String PVP_FEEDBACK_REQUEST_ROUTING_KEY = "pvp.feedback.request";
    public static final String PVP_FEEDBACK_RESPONSE_ROUTING_KEY = "pvp.feedback.response";

    // Dead Letter Queue (DLQ) - 처리 실패 메시지 보관소
    public static final String PVP_MATCH_DLQ = "pvp.match.dlq";
    public static final String PVP_MATCH_DLQ_ROUTING_KEY = "pvp.match.dlq";

    // ===== Exchange 빈 등록 =====

    /**
     * DirectExchange: 라우팅 키가 정확히 일치하는 Queue로 메시지 전달
     * - Topic Exchange보다 단순하고 빠름
     * - PvP 메시지는 명확한 타입(STT, Feedback)으로 구분 가능
     */
    @Bean
    public DirectExchange pvpDirectExchange() {
        return new DirectExchange(PVP_DIRECT_EXCHANGE, true, false);
    }

    // ===== Queue 빈 등록 =====

    /**
     * STT Request Queue: 메인 서버 → AI 서버 (음성 인식 요청)
     */
    @Bean
    public Queue pvpSttRequestQueue() {
        return QueueBuilder.durable(PVP_STT_REQUEST_QUEUE)
                .withArgument("x-dead-letter-exchange", PVP_DIRECT_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", PVP_MATCH_DLQ_ROUTING_KEY)
                .build();
    }

    /**
     * STT Response Queue: AI 서버 → 메인 서버 (음성 인식 결과)
     */
    @Bean
    public Queue pvpSttResponseQueue() {
        return QueueBuilder.durable(PVP_STT_RESPONSE_QUEUE)
                .withArgument("x-dead-letter-exchange", PVP_DIRECT_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", PVP_MATCH_DLQ_ROUTING_KEY)
                .build();
    }

    /**
     * Feedback Request Queue: 메인 서버 → AI 서버 (피드백 생성 요청)
     */
    @Bean
    public Queue pvpFeedbackRequestQueue() {
        return QueueBuilder.durable(PVP_FEEDBACK_REQUEST_QUEUE)
                .withArgument("x-dead-letter-exchange", PVP_DIRECT_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", PVP_MATCH_DLQ_ROUTING_KEY)
                .build();
    }

    /**
     * Feedback Response Queue: AI 서버 → 메인 서버 (피드백 생성 결과)
     */
    @Bean
    public Queue pvpFeedbackResponseQueue() {
        return QueueBuilder.durable(PVP_FEEDBACK_RESPONSE_QUEUE)
                .withArgument("x-dead-letter-exchange", PVP_DIRECT_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", PVP_MATCH_DLQ_ROUTING_KEY)
                .build();
    }

    /**
     * Dead Letter Queue: 처리 실패 메시지 보관소
     * - Max retries 초과 시 자동으로 DLQ로 이동
     * - 관리자가 수동으로 확인하여 원인 파악
     */
    @Bean
    public Queue pvpMatchDlq() {
        return QueueBuilder.durable(PVP_MATCH_DLQ).build();
    }

    // ===== Binding 빈 등록 (Exchange ↔ Queue 연결) =====

    @Bean
    public Binding pvpSttRequestBinding(Queue pvpSttRequestQueue, DirectExchange pvpDirectExchange) {
        return BindingBuilder.bind(pvpSttRequestQueue)
                .to(pvpDirectExchange)
                .with(PVP_STT_REQUEST_ROUTING_KEY);
    }

    @Bean
    public Binding pvpSttResponseBinding(Queue pvpSttResponseQueue, DirectExchange pvpDirectExchange) {
        return BindingBuilder.bind(pvpSttResponseQueue)
                .to(pvpDirectExchange)
                .with(PVP_STT_RESPONSE_ROUTING_KEY);
    }

    @Bean
    public Binding pvpFeedbackRequestBinding(Queue pvpFeedbackRequestQueue, DirectExchange pvpDirectExchange) {
        return BindingBuilder.bind(pvpFeedbackRequestQueue)
                .to(pvpDirectExchange)
                .with(PVP_FEEDBACK_REQUEST_ROUTING_KEY);
    }

    @Bean
    public Binding pvpFeedbackResponseBinding(Queue pvpFeedbackResponseQueue, DirectExchange pvpDirectExchange) {
        return BindingBuilder.bind(pvpFeedbackResponseQueue)
                .to(pvpDirectExchange)
                .with(PVP_FEEDBACK_RESPONSE_ROUTING_KEY);
    }

    @Bean
    public Binding pvpMatchDlqBinding(Queue pvpMatchDlq, DirectExchange pvpDirectExchange) {
        return BindingBuilder.bind(pvpMatchDlq)
                .to(pvpDirectExchange)
                .with(PVP_MATCH_DLQ_ROUTING_KEY);
    }

    // ===== Message Converter =====

    /**
     * Jackson2JsonMessageConverter: JSON 직렬화/역직렬화
     * - 모든 메시지는 JSON 형태로 송수신
     * - LocalDateTime 등 Java 8 시간 타입 지원
     */
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * RabbitTemplate: 메시지 발행 (Producer)
     * - Jackson2JsonMessageConverter 적용
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    /**
     * SimpleRabbitListenerContainerFactory: 메시지 수신 (Consumer)
     * - Jackson2JsonMessageConverter 적용
     * - Manual Ack 모드 (application.yml에서 설정)
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        return factory;
    }
}