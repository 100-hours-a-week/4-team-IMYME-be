package com.imyme.mine.domain.pvp.service;

import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.category.entity.Category;
import com.imyme.mine.domain.category.repository.CategoryRepository;
import com.imyme.mine.domain.forbidden.entity.ForbiddenWordType;
import com.imyme.mine.domain.forbidden.service.ForbiddenWordService;
import com.imyme.mine.domain.keyword.entity.Keyword;
import com.imyme.mine.domain.keyword.repository.KeywordRepository;
import com.imyme.mine.domain.pvp.dto.request.CreateRoomRequest;
import com.imyme.mine.domain.pvp.dto.response.RoomListResponse;
import com.imyme.mine.domain.pvp.dto.response.RoomResponse;
import com.imyme.mine.domain.pvp.entity.PvpRoom;
import com.imyme.mine.domain.pvp.entity.PvpRoomStatus;
import com.imyme.mine.domain.pvp.repository.PvpRoomRepository;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PvpRoomService {

    private final PvpRoomRepository pvpRoomRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final ForbiddenWordService forbiddenWordService;
    private final KeywordRepository keywordRepository;

    /**
     * 4.1 방 목록 조회 (커서 페이징)
     */
    public RoomListResponse getRooms(Long categoryId, PvpRoomStatus status, String cursor, int size) {
        // 카테고리 유효성 검사
        if (categoryId != null) {
            categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        }

        LocalDateTime cursorTime = null;
        Long lastId = null;

        // 커서 파싱 (예외 처리 포함)
        if (cursor != null && !cursor.isBlank()) {
            try {
                String[] parts = cursor.split("_");
                if (parts.length == 2) {
                    cursorTime = LocalDateTime.parse(parts[0]);
                    lastId = Long.parseLong(parts[1]);
                }
            } catch (Exception e) {
                log.warn("Invalid cursor format: {}", cursor, e);
                throw new BusinessException(ErrorCode.INVALID_CURSOR);
            }
        }

        Pageable pageable = PageRequest.of(0, size + 1);
        List<PvpRoom> rooms;

        boolean hasCursor = cursorTime != null && lastId != null;

        if (categoryId != null && hasCursor) {
            rooms = pvpRoomRepository.findRoomsByCategoryAndStatusWithCursor(
                    categoryId, status, cursorTime, lastId, pageable);
        } else if (categoryId != null) {
            rooms = pvpRoomRepository.findRoomsByCategoryAndStatus(
                    categoryId, status, pageable);
        } else if (hasCursor) {
            rooms = pvpRoomRepository.findRoomsByStatusWithCursor(
                    status, cursorTime, lastId, pageable);
        } else {
            rooms = pvpRoomRepository.findRoomsByStatus(
                    status, pageable);
        }

        boolean hasNext = rooms.size() > size;
        List<PvpRoom> pageRooms = hasNext ? rooms.subList(0, size) : rooms;

        String nextCursor = null;
        if (hasNext && !pageRooms.isEmpty()) {
            PvpRoom last = pageRooms.get(pageRooms.size() - 1);
            nextCursor = last.getCreatedAt() + "_" + last.getId();
        }

        List<RoomListResponse.RoomItem> items = pageRooms.stream()
                .map(this::toRoomItem)
                .toList();

        return RoomListResponse.builder()
                .rooms(items)
                .meta(RoomListResponse.PageMeta.builder()
                        .size(pageRooms.size())
                        .hasNext(hasNext)
                        .nextCursor(nextCursor)
                        .build())
                .build();
    }

    /**
     * 4.2 방 생성
     */
    @Transactional
    public RoomResponse createRoom(Long userId, CreateRoomRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        // 금지어 검증
        if (forbiddenWordService.containsForbiddenWord(request.roomName(), ForbiddenWordType.ROOM_NAME)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_WORD);
        }

        pvpRoomRepository.findByHostUserIdAndStatus(userId, PvpRoomStatus.OPEN)
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.DUPLICATE_ROOM);
                });

        User host = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        PvpRoom room = PvpRoom.builder()
                .category(category)
                .roomName(request.roomName())
                .hostUser(host)
                .hostNickname(host.getNickname())
                .build();

        pvpRoomRepository.save(room);
        log.info("방 생성: roomId={}, userId={}, categoryId={}", room.getId(), userId, request.categoryId());

        return toRoomResponse(room, "방이 생성되었습니다.");
    }

    /**
     * 4.3 방 입장 (게스트)
     */
    @Transactional
    public RoomResponse joinRoom(Long userId, Long roomId) {
        // 방 조회 (낙관적 락)
        PvpRoom room = pvpRoomRepository.findByIdWithDetails(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        // 호스트 본인 체크
        if (room.isHost(userId)) {
            throw new BusinessException(ErrorCode.CANNOT_JOIN_OWN_ROOM);
        }

        // 방 상태 검증
        if (room.getStatus() != PvpRoomStatus.OPEN) {
            if (room.getStatus() == PvpRoomStatus.CANCELED) {
                throw new BusinessException(ErrorCode.ROOM_EXPIRED);
            }
            throw new BusinessException(ErrorCode.ROOM_ALREADY_MATCHED);
        }

        // 게스트 중복 체크 (동시성 제어)
        if (room.getGuestUser() != null) {
            throw new BusinessException(ErrorCode.ROOM_ALREADY_MATCHED);
        }

        // 게스트 조회
        User guest = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 게스트 입장 (MATCHED 전환)
        room.joinGuest(guest, guest.getNickname());

        // 저장 (낙관적 락으로 동시성 제어)
        try {
            pvpRoomRepository.save(room);
        } catch (Exception e) {
            log.warn("방 입장 실패 (동시성): roomId={}, userId={}", roomId, userId, e);
            throw new BusinessException(ErrorCode.ROOM_ALREADY_MATCHED);
        }

        log.info("게스트 입장: roomId={}, userId={}, status=MATCHED", roomId, userId);

        // 3초 후 키워드 배정 및 THINKING 전환 (비동기)
        scheduleThinkingTransition(roomId);

        return toRoomResponse(room, "매칭 완료! 잠시 후 키워드가 공개됩니다.");
    }

    /**
     * 3초 후 키워드 배정 및 THINKING 전환
     */
    @Async
    @Transactional
    public void scheduleThinkingTransition(Long roomId) {
        try {
            Thread.sleep(3000);

            PvpRoom room = pvpRoomRepository.findByIdWithDetails(roomId)
                    .orElse(null);

            if (room == null || room.getStatus() != PvpRoomStatus.MATCHED) {
                log.warn("THINKING 전환 실패: 방 상태 불일치 - roomId={}", roomId);
                return;
            }

            // 키워드 랜덤 배정
            List<Keyword> keywords = keywordRepository.findAllByCategoryIdAndIsActiveOrderByDisplayOrderAsc(
                    room.getCategory().getId(), true);

            if (keywords.isEmpty()) {
                log.error("THINKING 전환 실패: 키워드 없음 - roomId={}, categoryId={}", roomId, room.getCategory().getId());
                return;
            }

            Keyword randomKeyword = keywords.get((int) (Math.random() * keywords.size()));
            room.startThinking(randomKeyword);

            pvpRoomRepository.save(room);
            log.info("THINKING 전환 완료: roomId={}, keywordId={}", roomId, randomKeyword.getId());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("THINKING 전환 중단: roomId={}", roomId, e);
        }
    }

    /**
     * 4.4 방 상태 조회
     */
    public RoomResponse getRoom(Long userId, Long roomId) {
        PvpRoom room = pvpRoomRepository.findByIdWithDetails(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        if (!room.isParticipant(userId)) {
            throw new BusinessException(ErrorCode.NOT_PARTICIPANT);
        }

        return toRoomResponse(room, null);
    }

    // ===== 내부 변환 메서드 =====

    RoomResponse toRoomResponse(PvpRoom room, String message) {
        RoomResponse.KeywordInfo keywordInfo = null;
        if (room.getKeyword() != null) {
            keywordInfo = RoomResponse.KeywordInfo.builder()
                    .id(room.getKeyword().getId())
                    .name(room.getKeyword().getName())
                    .build();
        }

        return RoomResponse.builder()
                .id(room.getId())
                .categoryId(room.getCategory().getId())
                .categoryName(room.getCategory().getName())
                .roomName(room.getRoomName())
                .status(room.getStatus())
                .hostUserId(room.getHostUser().getId())
                .hostNickname(room.getHostNickname())
                .guestUserId(room.getGuestUser() != null ? room.getGuestUser().getId() : null)
                .guestNickname(room.getGuestNickname())
                .keyword(keywordInfo)
                .createdAt(room.getCreatedAt())
                .matchedAt(room.getMatchedAt())
                .startedAt(room.getStartedAt())
                .message(message)
                .build();
    }

    private RoomListResponse.RoomItem toRoomItem(PvpRoom room) {
        return RoomListResponse.RoomItem.builder()
                .id(room.getId())
                .categoryId(room.getCategory().getId())
                .categoryName(room.getCategory().getName())
                .roomName(room.getRoomName())
                .status(room.getStatus())
                .hostUserId(room.getHostUser().getId())
                .hostNickname(room.getHostNickname())
                .createdAt(room.getCreatedAt())
                .build();
    }
}