package com.imyme.mine.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Challenge MQ 설정
 * - Exchange, Queue, Routing Key 이름 관리
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "challenge.mq")
public class ChallengeMqProperties {

    private String exchange = "challenge.direct";

    private Queue queue = new Queue();

    private Routing routing = new Routing();

    @Getter
    @Setter
    public static class Queue {
        private String feedbackResponse = "challenge.feedback.response";
    }

    @Getter
    @Setter
    public static class Routing {
        private String feedbackRequest = "challenge.feedback.request";
    }
}