package com.imyme.mine.domain.pvp.websocket;

import com.imyme.mine.domain.pvp.dto.MessageType;
import com.imyme.mine.domain.pvp.dto.websocket.PvpWebSocketMessage;
import com.imyme.mine.domain.pvp.dto.websocket.RoomJoinedMessage;
import com.imyme.mine.domain.pvp.dto.websocket.RoomStatusChangeMessage;
import com.imyme.mine.domain.pvp.entity.PvpRoom;
import com.imyme.mine.domain.pvp.entity.PvpRoomStatus;
import com.imyme.mine.domain.pvp.repository.PvpRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.Optional;

/**
 * WebSocket 이벤트 리스너
 * - CONNECT: 로깅
 * - DISCONNECT: 세션 정리 + DB 업데이트 + 상대방에게 broadcast
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final PvpSessionManager sessionManager;
    private final PvpRoomRepository pvpRoomRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        Long userId = extractUserId(headerAccessor);
        log.info("WebSocket CONNECT: sessionId={}, userId={}", sessionId, userId);
    }

    @EventListener
    @Transactional
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        // 세션 매니저에서 제거
        SessionInfo removed = sessionManager.removeSession(sessionId);

        if (removed == null) {
            log.info("WebSocket DISCONNECT: sessionId={} - 등록된 세션 없음 (방 참여 전 종료)", sessionId);
            return;
        }

        Long roomId = removed.roomId();
        Long userId = removed.userId();

        log.info("WebSocket DISCONNECT: sessionId={}, roomId={}, userId={} - 세션 정리 완료",
                sessionId, roomId, userId);

        // 방 조회 후 상태 업데이트 + broadcast
        Optional<PvpRoom> roomOpt = pvpRoomRepository.findByIdWithDetails(roomId);
        if (roomOpt.isEmpty()) {
            return;
        }

        PvpRoom room = roomOpt.get();

        // 이미 종료된 방이면 무시
        if (room.getStatus() == PvpRoomStatus.FINISHED
                || room.getStatus() == PvpRoomStatus.CANCELED
                || room.getStatus() == PvpRoomStatus.EXPIRED) {
            return;
        }

        if (room.isHost(userId)) {
            // 호스트 disconnect → 방 취소
            room.cancel();
            pvpRoomRepository.save(room);
            log.info("호스트 disconnect → 방 취소: roomId={}", roomId);

            RoomStatusChangeMessage statusData = RoomStatusChangeMessage.builder()
                    .status(PvpRoomStatus.CANCELED)
                    .message("호스트가 연결이 끊겨 방이 취소되었습니다.")
                    .build();
            messagingTemplate.convertAndSend(
                    "/topic/pvp/" + roomId,
                    PvpWebSocketMessage.of(MessageType.STATUS_CHANGE, roomId, statusData)
            );

        } else if (room.isGuest(userId)) {
            // 게스트 disconnect → 게스트 제거, 방 OPEN 복구
            room.removeGuest();
            pvpRoomRepository.save(room);
            log.info("게스트 disconnect → 방 OPEN 복구: roomId={}", roomId);

            RoomJoinedMessage leftData = RoomJoinedMessage.builder()
                    .userId(userId)
                    .message("상대방의 연결이 끊겼습니다.")
                    .build();
            messagingTemplate.convertAndSend(
                    "/topic/pvp/" + roomId,
                    PvpWebSocketMessage.of(MessageType.ROOM_LEFT, roomId, leftData)
            );

            RoomStatusChangeMessage statusData = RoomStatusChangeMessage.builder()
                    .status(PvpRoomStatus.OPEN)
                    .message("대결 상대를 기다리고 있습니다.")
                    .build();
            messagingTemplate.convertAndSend(
                    "/topic/pvp/" + roomId,
                    PvpWebSocketMessage.of(MessageType.STATUS_CHANGE, roomId, statusData)
            );
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