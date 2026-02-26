package com.imyme.mine.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 설정
 * - Queue/Exchange/Binding은 AI 서버에서 생성 (메인 서버는 선언하지 않음)
 * - Jackson2JsonMessageConverter: JSON 직렬화/역직렬화
 * - Manual Ack: 메시지 처리 확인 후 수동 승인 (신뢰성 보장)
 */
@Configuration
public class RabbitMQConfig {

    // ===== 큐/라우팅 키 상수 (AI 서버가 생성, 메인 서버는 참조만) =====

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
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);   // application.yml 설정 적용
        factory.setMessageConverter(messageConverter);       // 커스텀 converter 유지
        return factory;
    }
}
