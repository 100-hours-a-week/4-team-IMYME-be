package com.imyme.mine.domain.pvp.controller;

import com.imyme.mine.domain.pvp.dto.MessageType;
import com.imyme.mine.domain.pvp.dto.websocket.PvpWebSocketMessage;
import com.imyme.mine.domain.pvp.dto.websocket.RoomJoinedMessage;
import com.imyme.mine.domain.pvp.dto.websocket.RoomStatusChangeMessage;
import com.imyme.mine.domain.pvp.entity.PvpRoom;
import com.imyme.mine.domain.pvp.entity.PvpRoomStatus;
import com.imyme.mine.domain.pvp.repository.PvpRoomRepository;
import com.imyme.mine.domain.pvp.websocket.PvpSessionManager;
import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * PvP WebSocket 컨트롤러
 * - /app/pvp/{roomId}/join  → 방 참여 (DB 연동 + 2명 제한)
 * - /app/pvp/{roomId}/leave → 방 퇴장 (호스트: cancel, 게스트: removeGuest)
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class PvpWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final PvpSessionManager sessionManager;
    private final PvpRoomRepository pvpRoomRepository;
    private final UserRepository userRepository;

    /**
     * 방 참여
     * 클라이언트 → /app/pvp/{roomId}/join
     * 서버 → /topic/pvp/{roomId} (ROOM_JOINED + STATUS_CHANGE 브로드캐스트)
     */
    @MessageMapping("/pvp/{roomId}/join")
    @Transactional
    public void joinRoom(@DestinationVariable Long roomId, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        Long userId = extractUserId(headerAccessor);

        if (userId == null) {
            log.warn("방 참여 실패: userId 추출 불가 - sessionId={}, roomId={}", sessionId, roomId);
            sendError(roomId, "인증 정보를 확인할 수 없습니다.");
            return;
        }

        // 1. 방 조회
        Optional<PvpRoom> roomOpt = pvpRoomRepository.findByIdWithDetails(roomId);
        if (roomOpt.isEmpty()) {
            log.warn("방 참여 실패: 방 없음 - roomId={}, userId={}", roomId, userId);
            sendError(roomId, "존재하지 않는 방입니다.");
            return;
        }

        PvpRoom room = roomOpt.get();

        // 2. 호스트 본인이면 세션만 등록 (재연결)
        if (room.isHost(userId)) {
            sessionManager.addSession(sessionId, roomId, userId);
            log.info("호스트 연결: userId={}, roomId={}", userId, roomId);

            RoomJoinedMessage joinedData = RoomJoinedMessage.builder()
                    .userId(userId)
                    .nickname(room.getHostNickname())
                    .message(room.getHostNickname() + "님이 입장했습니다.")
                    .build();
            messagingTemplate.convertAndSend(
                    "/topic/pvp/" + roomId,
                    PvpWebSocketMessage.of(MessageType.ROOM_JOINED, roomId, joinedData)
            );
            return;
        }

        // 3. 이미 게스트로 참여 중이면 세션만 등록 (재연결)
        if (room.isGuest(userId)) {
            sessionManager.addSession(sessionId, roomId, userId);
            log.info("게스트 재연결: userId={}, roomId={}", userId, roomId);
            return;
        }

        // 4. 방 상태 검증 (OPEN 상태에서만 게스트 입장 가능)
        if (room.getStatus() != PvpRoomStatus.OPEN) {
            log.warn("방 참여 실패: 방 상태 {} - roomId={}, userId={}", room.getStatus(), roomId, userId);
            sendError(roomId, "현재 입장할 수 없는 방입니다. (상태: " + room.getStatus() + ")");
            return;
        }

        // 5. 2명 제한 검증
        if (room.getGuestUser() != null) {
            log.warn("방 참여 실패: 이미 2명 - roomId={}, userId={}", roomId, userId);
            sendError(roomId, "이미 대결 상대가 있습니다.");
            return;
        }

        // 6. 유저 조회
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            log.warn("방 참여 실패: 유저 없음 - userId={}", userId);
            sendError(roomId, "유저 정보를 찾을 수 없습니다.");
            return;
        }

        User guest = userOpt.get();

        // 7. 게스트 입장 (DB 업데이트: OPEN → MATCHED)
        room.joinGuest(guest, guest.getNickname());
        pvpRoomRepository.save(room);

        // 8. 세션 등록
        sessionManager.addSession(sessionId, roomId, userId);

        log.info("게스트 입장: userId={}, roomId={}, 방 상태: OPEN → MATCHED", userId, roomId);

        // 9. ROOM_JOINED 브로드캐스트
        RoomJoinedMessage joinedData = RoomJoinedMessage.builder()
                .userId(userId)
                .nickname(guest.getNickname())
                .message(guest.getNickname() + "님이 입장했습니다.")
                .build();

        messagingTemplate.convertAndSend(
                "/topic/pvp/" + roomId,
                PvpWebSocketMessage.of(MessageType.ROOM_JOINED, roomId, joinedData)
        );

        // 10. STATUS_CHANGE 브로드캐스트 (MATCHED)
        RoomStatusChangeMessage statusData = RoomStatusChangeMessage.builder()
                .status(PvpRoomStatus.MATCHED)
                .message("대결 상대가 입장했습니다. 키워드를 선정해주세요.")
                .build();

        messagingTemplate.convertAndSend(
                "/topic/pvp/" + roomId,
                PvpWebSocketMessage.of(MessageType.STATUS_CHANGE, roomId, statusData)
        );
    }

    /**
     * 방 퇴장
     * 클라이언트 → /app/pvp/{roomId}/leave
     * - 호스트 퇴장: 방 CANCELED
     * - 게스트 퇴장: 게스트 제거, 방 OPEN으로 복구
     */
    @MessageMapping("/pvp/{roomId}/leave")
    @Transactional
    public void leaveRoom(@DestinationVariable Long roomId, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        Long userId = extractUserId(headerAccessor);

        if (userId == null) {
            log.warn("방 퇴장 실패: userId 추출 불가 - sessionId={}, roomId={}", sessionId, roomId);
            return;
        }

        // 세션 매니저에서 제거
        sessionManager.removeSession(sessionId);

        // 방 조회
        Optional<PvpRoom> roomOpt = pvpRoomRepository.findByIdWithDetails(roomId);
        if (roomOpt.isEmpty()) {
            log.warn("방 퇴장: 방 없음 - roomId={}", roomId);
            return;
        }

        PvpRoom room = roomOpt.get();

        if (room.isHost(userId)) {
            // 호스트 퇴장 → 방 취소
            room.cancel();
            pvpRoomRepository.save(room);
            log.info("호스트 퇴장: roomId={} → CANCELED", roomId);

            RoomStatusChangeMessage statusData = RoomStatusChangeMessage.builder()
                    .status(PvpRoomStatus.CANCELED)
                    .message("호스트가 퇴장하여 방이 취소되었습니다.")
                    .build();
            messagingTemplate.convertAndSend(
                    "/topic/pvp/" + roomId,
                    PvpWebSocketMessage.of(MessageType.STATUS_CHANGE, roomId, statusData)
            );

        } else if (room.isGuest(userId)) {
            // 게스트 퇴장 → 방 OPEN으로 복구
            room.removeGuest();
            pvpRoomRepository.save(room);
            log.info("게스트 퇴장: roomId={} → OPEN", roomId);

            RoomJoinedMessage leftData = RoomJoinedMessage.builder()
                    .userId(userId)
                    .message("상대방이 퇴장했습니다.")
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
        } else {
            log.warn("방 퇴장: 참여자가 아님 - roomId={}, userId={}", roomId, userId);
        }
    }

    private void sendError(Long roomId, String message) {
        messagingTemplate.convertAndSend(
                "/topic/pvp/" + roomId,
                PvpWebSocketMessage.error(roomId, message)
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