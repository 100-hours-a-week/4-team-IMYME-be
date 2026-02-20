package com.imyme.mine.domain.pvp.service;

import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.category.entity.Category;
import com.imyme.mine.domain.category.repository.CategoryRepository;
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

    /**
     * 4.1 방 목록 조회 (커서 페이징)
     */
    public RoomListResponse getRooms(Long categoryId, PvpRoomStatus status, String cursor, int size) {
        LocalDateTime cursorTime = null;
        Long lastId = null;

        if (cursor != null && !cursor.isBlank()) {
            String[] parts = cursor.split("_");
            if (parts.length == 2) {
                cursorTime = LocalDateTime.parse(parts[0]);
                lastId = Long.parseLong(parts[1]);
            }
        }

        Pageable pageable = PageRequest.of(0, size + 1);
        List<PvpRoom> rooms;

        if (categoryId != null) {
            rooms = pvpRoomRepository.findRoomsByCategoryAndStatus(
                    categoryId, status, cursorTime, lastId, pageable);
        } else {
            rooms = pvpRoomRepository.findRoomsByStatus(
                    status, cursorTime, lastId, pageable);
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