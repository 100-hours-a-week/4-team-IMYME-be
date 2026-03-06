package com.imyme.mine.domain.learning.messaging;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Solo MQ 메시지 수신 (Consumer)
 * - AI 서버로부터 STT Response, Feedback Response 수신
 * - 현재는 스켈레톤: 로그 출력 + Manual Ack/Nack만 처리
 * - DTO 스키마 확정 후 실제 비즈니스 로직 연결 예정
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SoloMqConsumer {

    /**
     * STT 응답 수신 (AI → Main)
     * - 음성→텍스트 변환 결과 수신
     * - 스키마 확정 후: DTO 파싱 → Feedback 요청 발행으로 연결
     */
    @RabbitListener(queues = "${solo.mq.queue.stt-response}")
    public void consumeSttResponse(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            String body = new String(message.getBody());
            log.info("[Solo MQ] STT Response 수신 - body: {}", body);

            // TODO: DTO 파싱 + 비즈니스 로직 연결
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("[Solo MQ] STT Response 처리 실패", e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    /**
     * Feedback 응답 수신 (AI → Main)
     * - AI 분석 결과(점수, 피드백) 수신
     * - 스키마 확정 후: DTO 파싱 → SoloFeedbackSaveService.save() 연결
     */
    @RabbitListener(queues = "${solo.mq.queue.feedback-response}")
    public void consumeFeedbackResponse(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            String body = new String(message.getBody());
            log.info("[Solo MQ] Feedback Response 수신 - body: {}", body);

            // TODO: DTO 파싱 + SoloFeedbackSaveService.save() 연결
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("[Solo MQ] Feedback Response 처리 실패", e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}