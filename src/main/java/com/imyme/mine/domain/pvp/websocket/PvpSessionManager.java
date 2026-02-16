package com.imyme.mine.domain.pvp.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PvP WebSocket 세션 관리자
 * - sessionId ↔ SessionInfo 매핑 (ConcurrentHashMap)
 * - Phase 1: 단방향 매핑만 관리
 * - Phase 2: roomId → sessions 역매핑 추가 예정
 */
@Slf4j
@Component
public class PvpSessionManager {

    private final Map<String, SessionInfo> sessionToRoom = new ConcurrentHashMap<>();

    /**
     * 세션 등록
     */
    public void addSession(String sessionId, Long roomId, Long userId) {
        if (sessionId == null || roomId == null || userId == null) {
            log.warn("addSession 실패: null 파라미터 - sessionId={}, roomId={}, userId={}",
                    sessionId, roomId, userId);
            return;
        }

        SessionInfo info = new SessionInfo(sessionId, roomId, userId, LocalDateTime.now());
        sessionToRoom.put(sessionId, info);
        log.info("세션 등록: sessionId={}, roomId={}, userId={}", sessionId, roomId, userId);
    }

    /**
     * 세션 제거
     */
    public SessionInfo removeSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }

        SessionInfo removed = sessionToRoom.remove(sessionId);
        if (removed != null) {
            log.info("세션 제거: sessionId={}, roomId={}, userId={}",
                    sessionId, removed.roomId(), removed.userId());
        }
        return removed;
    }

    /**
     * 세션 ID로 SessionInfo 조회
     */
    public SessionInfo getSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        return sessionToRoom.get(sessionId);
    }

    /**
     * 전체 세션 수 조회
     */
    public int getSessionCount() {
        return sessionToRoom.size();
    }

    /**
     * 전체 세션 목록 조회 (읽기 전용)
     */
    public Map<String, SessionInfo> getAllSessions() {
        return Collections.unmodifiableMap(sessionToRoom);
    }
}