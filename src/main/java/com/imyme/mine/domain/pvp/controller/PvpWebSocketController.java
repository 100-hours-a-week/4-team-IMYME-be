package com.imyme.mine.domain.pvp.controller;

import com.imyme.mine.domain.pvp.dto.MessageType;
import com.imyme.mine.domain.pvp.dto.websocket.PvpWebSocketMessage;
import com.imyme.mine.domain.pvp.dto.websocket.RoomJoinedMessage;
import com.imyme.mine.domain.pvp.dto.websocket.RoomStatusChangeMessage;
import com.imyme.mine.domain.pvp.entity.PvpRoomStatus;
import com.imyme.mine.domain.pvp.websocket.PvpSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * PvP WebSocket 컨트롤러
 * - /app/pvp/{roomId}/join  → 방 참여
 * - /app/pvp/{roomId}/leave → 방 퇴장
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class PvpWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final PvpSessionManager sessionManager;

    /**
     * 방 참여
     * 클라이언트 → /app/pvp/{roomId}/join
     * 서버 → /topic/pvp/{roomId} (STATUS_CHANGE 브로드캐스트)
     */
    @MessageMapping("/pvp/{roomId}/join")
    public void joinRoom(@DestinationVariable Long roomId, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        Long userId = extractUserId(headerAccessor);

        if (userId == null) {
            log.warn("방 참여 실패: userId 추출 불가 - sessionId={}, roomId={}", sessionId, roomId);
            messagingTemplate.convertAndSend(
                    "/topic/pvp/" + roomId,
                    PvpWebSocketMessage.error(roomId, "인증 정보를 확인할 수 없습니다.")
            );
            return;
        }

        // 세션 매니저에 등록
        sessionManager.addSession(sessionId, roomId, userId);

        log.info("방 참여: userId={}, roomId={}, sessionId={}", userId, roomId, sessionId);

        // ROOM_JOINED 브로드캐스트
        RoomJoinedMessage joinedData = RoomJoinedMessage.builder()
                .userId(userId)
                .message("유저 " + userId + "님이 입장했습니다.")
                .build();

        messagingTemplate.convertAndSend(
                "/topic/pvp/" + roomId,
                PvpWebSocketMessage.of(MessageType.ROOM_JOINED, roomId, joinedData)
        );

        // STATUS_CHANGE 브로드캐스트 (Phase 1: THINKING 하드코딩)
        RoomStatusChangeMessage statusData = RoomStatusChangeMessage.builder()
                .status(PvpRoomStatus.THINKING)
                .message("게임 준비 중")
                .build();

        messagingTemplate.convertAndSend(
                "/topic/pvp/" + roomId,
                PvpWebSocketMessage.of(MessageType.STATUS_CHANGE, roomId, statusData)
        );
    }

    /**
     * 방 퇴장
     * 클라이언트 → /app/pvp/{roomId}/leave
     * 서버 → /topic/pvp/{roomId} (ROOM_LEFT 브로드캐스트)
     */
    @MessageMapping("/pvp/{roomId}/leave")
    public void leaveRoom(@DestinationVariable Long roomId, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        Long userId = extractUserId(headerAccessor);

        if (userId == null) {
            log.warn("방 퇴장 실패: userId 추출 불가 - sessionId={}, roomId={}", sessionId, roomId);
            return;
        }

        // 세션 매니저에서 제거
        sessionManager.removeSession(sessionId);

        log.info("방 퇴장: userId={}, roomId={}, sessionId={}", userId, roomId, sessionId);

        // ROOM_LEFT 브로드캐스트
        RoomJoinedMessage leftData = RoomJoinedMessage.builder()
                .userId(userId)
                .message("유저 " + userId + "님이 퇴장했습니다.")
                .build();

        messagingTemplate.convertAndSend(
                "/topic/pvp/" + roomId,
                PvpWebSocketMessage.of(MessageType.ROOM_LEFT, roomId, leftData)
        );
    }

    /**
     * sessionAttributes에서 userId 추출 (null 방어 + 타입 캐스팅 방어)
     */
    private Long extractUserId(SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        if (attrs == null) {
            log.warn("sessionAttributes가 null입니다.");
            return null;
        }

        Object userIdObj = attrs.get("userId");
        if (userIdObj == null) {
            log.warn("sessionAttributes에 userId가 없습니다.");
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