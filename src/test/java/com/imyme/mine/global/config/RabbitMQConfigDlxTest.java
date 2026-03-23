package com.imyme.mine.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Response 큐에 x-dead-letter-exchange 인수가 올바르게 설정되었는지 검증
 */
class RabbitMQConfigDlxTest {

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    @DisplayName("PvP STT Response 큐에 DLX가 설정되어 있다")
    void pvpSttResponseQueue_hasDlx() {
        Queue queue = config.pvpSttResponseQueue();
        assertDlx(queue, RabbitMQConfig.PVP_DLX);
    }

    @Test
    @DisplayName("PvP Feedback Response 큐에 DLX가 설정되어 있다")
    void pvpFeedbackResponseQueue_hasDlx() {
        Queue queue = config.pvpFeedbackResponseQueue();
        assertDlx(queue, RabbitMQConfig.PVP_DLX);
    }

    @Test
    @DisplayName("Solo STT Response 큐에 DLX가 설정되어 있다")
    void soloSttResponseQueue_hasDlx() {
        SoloMqProperties props = new SoloMqProperties();
        Queue queue = config.soloSttResponseQueue(props);
        assertDlx(queue, RabbitMQConfig.SOLO_DLX);
    }

    @Test
    @DisplayName("Solo Feedback Response 큐에 DLX가 설정되어 있다")
    void soloFeedbackResponseQueue_hasDlx() {
        SoloMqProperties props = new SoloMqProperties();
        Queue queue = config.soloFeedbackResponseQueue(props);
        assertDlx(queue, RabbitMQConfig.SOLO_DLX);
    }

    @Test
    @DisplayName("Challenge Feedback Response 큐에 DLX가 설정되어 있다")
    void challengeFeedbackResponseQueue_hasDlx() {
        ChallengeMqProperties props = new ChallengeMqProperties();
        Queue queue = config.challengeFeedbackResponseQueue(props);
        assertDlx(queue, RabbitMQConfig.CHALLENGE_DLX);
    }

    @Test
    @DisplayName("Challenge Final Done 큐에 DLX가 설정되어 있다")
    void challengeFinalDoneQueue_hasDlx() {
        ChallengeMqProperties props = new ChallengeMqProperties();
        Queue queue = config.challengeFinalDoneQueue(props);
        assertDlx(queue, RabbitMQConfig.CHALLENGE_DLX);
    }

    private void assertDlx(Queue queue, String expectedDlx) {
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", expectedDlx);
    }
}
