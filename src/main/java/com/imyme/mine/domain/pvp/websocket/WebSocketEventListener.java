package com.imyme.mine.domain.pvp.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;

/**
 * WebSocket 이벤트 리스너
 * - CONNECT: 로깅만 (세션 등록은 @MessageMapping에서 수행)
 * - DISCONNECT: 세션 정리 + 로깅 (Phase 1에서는 broadcast 없음)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final PvpSessionManager sessionManager;

    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        Long userId = extractUserId(headerAccessor);
        log.info("WebSocket CONNECT: sessionId={}, userId={}", sessionId, userId);
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        // 세션 매니저에서 제거
        SessionInfo removed = sessionManager.removeSession(sessionId);

        if (removed != null) {
            log.info("WebSocket DISCONNECT: sessionId={}, roomId={}, userId={} - 세션 정리 완료",
                    sessionId, removed.roomId(), removed.userId());
            // Phase 1: broadcast 없음 (disconnect 이벤트가 완벽히 신뢰되지 않음)
            // Phase 2: 방 참여자에게 ROOM_LEFT broadcast 추가 예정
        } else {
            log.info("WebSocket DISCONNECT: sessionId={} - 등록된 세션 없음 (방 참여 전 종료)", sessionId);
        }
    }

    /**
     * sessionAttributes에서 userId 추출 (null 방어)
     */
    private Long extractUserId(StompHeaderAccessor headerAccessor) {
        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        if (attrs == null) {
            return null;
        }

        Object userIdObj = attrs.get("userId");
        if (userIdObj == null) {
            return null;
        }

        if (userIdObj instanceof Long) {
            return (Long) userIdObj;
        }
        if (userIdObj instanceof Number) {
            return ((Number) userIdObj).longValue();
        }

        log.warn("userId 타입 불일치: type={}, value={}", userIdObj.getClass().getName(), userIdObj);
        return null;
    }
}