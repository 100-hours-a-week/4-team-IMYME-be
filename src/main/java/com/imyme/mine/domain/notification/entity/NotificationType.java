package com.imyme.mine.domain.notification.entity;

/**
 * 알림 타입
 */
public enum NotificationType {
    PVP_MATCHED,        // PvP 매칭 완료
    PVP_GAME_START,     // PvP 게임 시작
    PVP_GAME_END,       // PvP 게임 종료
    CHALLENGE_INVITE,   // 챌린지 초대
    CHALLENGE_START,    // 챌린지 시작
    CHALLENGE_END,      // 챌린지 종료
    SYSTEM_NOTICE,      // 시스템 공지
    ACHIEVEMENT         // 업적 달성
}