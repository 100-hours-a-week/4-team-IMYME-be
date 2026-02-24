package com.imyme.mine.domain.pvp.messaging;

/**
 * PvP 메시지 타입
 * - 클라이언트가 메시지를 구분하여 처리할 수 있도록 타입 정의
 */
public enum PvpMessageType {
    /**
     * 방 상태 변경 (OPEN → MATCHED → THINKING → RECORDING → PROCESSING → FINISHED)
     */
    STATUS_CHANGE,

    /**
     * 게스트 입장 알림 (호스트에게 전송)
     */
    GUEST_JOINED,

    /**
     * 게스트 나가기 알림 (호스트에게 전송)
     */
    GUEST_LEFT,

    /**
     * 녹음 시작 알림 (양쪽에게 전송)
     */
    RECORDING_STARTED,

    /**
     * 제출 완료 알림 (상대방에게 전송)
     */
    SUBMISSION_COMPLETED,

    /**
     * AI 분석 완료 알림 (양쪽에게 전송)
     */
    ANALYSIS_COMPLETED
}