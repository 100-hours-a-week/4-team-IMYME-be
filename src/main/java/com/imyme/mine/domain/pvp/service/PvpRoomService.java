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
import com.imyme.mine.domain.pvp.dto.request.CreateSubmissionRequest;
import com.imyme.mine.domain.pvp.dto.response.RoomListResponse;
import com.imyme.mine.domain.pvp.dto.response.RoomResponse;
import com.imyme.mine.domain.pvp.dto.response.SubmissionResponse;
import com.imyme.mine.domain.pvp.entity.PvpRoom;
import com.imyme.mine.domain.pvp.entity.PvpRoomStatus;
import com.imyme.mine.domain.pvp.entity.PvpSubmission;
import com.imyme.mine.domain.pvp.repository.PvpRoomRepository;
import com.imyme.mine.domain.pvp.repository.PvpSubmissionRepository;
import com.imyme.mine.domain.storage.dto.PresignedUrlResponse;
import com.imyme.mine.domain.storage.service.StorageService;
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
    private final PvpSubmissionRepository pvpSubmissionRepository;
    private final StorageService storageService;

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

            // 30초 후 RECORDING 자동 전환
            scheduleRecordingTransition(roomId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("THINKING 전환 중단: roomId={}", roomId, e);
        }
    }

    /**
     * 30초 후 RECORDING 자동 전환
     */
    @Async
    @Transactional
    public void scheduleRecordingTransition(Long roomId) {
        try {
            Thread.sleep(30000); // 30초 대기

            PvpRoom room = pvpRoomRepository.findByIdWithDetails(roomId)
                    .orElse(null);

            if (room == null || room.getStatus() != PvpRoomStatus.THINKING) {
                log.warn("RECORDING 전환 실패: 방 상태 불일치 - roomId={}", roomId);
                return;
            }

            room.startRecording();
            pvpRoomRepository.save(room);
            log.info("RECORDING 자동 전환 완료: roomId={}", roomId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("RECORDING 전환 중단: roomId={}", roomId, e);
        }
    }

    /**
     * 녹음 시작 (수동 전환용)
     */
    @Transactional
    public RoomResponse startRecording(Long userId, Long roomId) {
        PvpRoom room = pvpRoomRepository.findByIdWithDetails(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        // 참여자 확인
        if (!room.isParticipant(userId)) {
            throw new BusinessException(ErrorCode.NOT_PARTICIPANT);
        }

        // 방 상태 검증 (THINKING 상태여야 함)
        if (room.getStatus() != PvpRoomStatus.THINKING) {
            throw new BusinessException(ErrorCode.INVALID_ROOM_STATUS);
        }

        room.startRecording();
        pvpRoomRepository.save(room);
        log.info("녹음 수동 시작: roomId={}, userId={}", roomId, userId);

        return toRoomResponse(room, "녹음을 시작합니다.");
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

    /**
     * 4.5 녹음 제출 (Presigned URL 발급)
     */
    @Transactional
    public SubmissionResponse createSubmission(Long userId, Long roomId, CreateSubmissionRequest request) {
        // 1. 방 조회
        PvpRoom room = pvpRoomRepository.findByIdWithDetails(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        // 2. 참여자 검증
        if (!room.isParticipant(userId)) {
            throw new BusinessException(ErrorCode.NOT_PARTICIPANT);
        }

        // 3. 방 상태 검증 (RECORDING 상태여야 함)
        if (room.getStatus() != PvpRoomStatus.RECORDING) {
            throw new BusinessException(ErrorCode.INVALID_ROOM_STATUS);
        }

        // 4. 중복 제출 검증
        if (pvpSubmissionRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new BusinessException(ErrorCode.ALREADY_SUBMITTED);
        }

        // 5. 유저 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 6. PvpSubmission 엔티티 생성 및 저장 (PENDING 상태)
        PvpSubmission submission = PvpSubmission.builder()
                .room(room)
                .user(user)
                .build();

        pvpSubmissionRepository.save(submission);
        log.info("제출 레코드 생성: submissionId={}, roomId={}, userId={}, status=PENDING",
                submission.getId(), roomId, userId);

        // 7. Presigned URL 발급
        PresignedUrlResponse presignedUrl = storageService.generatePvpPresignedUrl(
                submission.getId(),
                request.fileName(),
                request.contentType(),
                request.fileSize()
        );

        // 8. audioUrl 임시 저장 (objectKey)
        submission.setAudioUrl(presignedUrl.objectKey());
        pvpSubmissionRepository.save(submission);

        log.info("Presigned URL 발급 완료: submissionId={}, objectKey={}", submission.getId(), presignedUrl.objectKey());

        // 9. SubmissionResponse 반환
        return SubmissionResponse.builder()
                .submissionId(submission.getId())
                .roomId(roomId)
                .uploadUrl(presignedUrl.uploadUrl())
                .audioUrl(presignedUrl.objectKey()) // 임시로 objectKey 반환 (업로드 완료 후 CDN URL로 변경)
                .expiresIn(300) // 5분
                .status(submission.getStatus())
                .build();
    }

    /**
     * 4.10 방 나가기
     */
    @Transactional
    public void leaveRoom(Long userId, Long roomId) {
        PvpRoom room = pvpRoomRepository.findByIdWithDetails(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        // 참여자 확인
        if (!room.isParticipant(userId)) {
            throw new BusinessException(ErrorCode.NOT_PARTICIPANT);
        }

        // THINKING 이후 상태에서는 나가기 불가
        if (room.getStatus() == PvpRoomStatus.THINKING ||
            room.getStatus() == PvpRoomStatus.RECORDING ||
            room.getStatus() == PvpRoomStatus.PROCESSING ||
            room.getStatus() == PvpRoomStatus.FINISHED) {
            throw new BusinessException(ErrorCode.GAME_ALREADY_STARTED);
        }

        if (room.isHost(userId)) {
            // 호스트 나가기
            if (room.getStatus() == PvpRoomStatus.MATCHED) {
                // 게스트 입장 후에는 방 삭제 불가
                throw new BusinessException(ErrorCode.ROOM_CANNOT_BE_DELETED);
            }

            // OPEN 상태에서만 방 취소 가능
            room.cancel();
            pvpRoomRepository.save(room);
            log.info("호스트 방 나가기: roomId={}, status=CANCELED", roomId);

        } else {
            // 게스트 나가기
            if (room.getStatus() != PvpRoomStatus.MATCHED) {
                throw new BusinessException(ErrorCode.GAME_ALREADY_STARTED);
            }

            // 게스트 제거, 방 OPEN으로 복구
            room.removeGuest();
            pvpRoomRepository.save(room);
            log.info("게스트 방 나가기: roomId={}, status=OPEN", roomId);
        }
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

        // THINKING 상태일 때 생각 종료 시간 계산 (시작 시간 + 30초)
        LocalDateTime thinkingEndsAt = null;
        if (room.getStatus() == PvpRoomStatus.THINKING && room.getStartedAt() != null) {
            thinkingEndsAt = room.getStartedAt().plusSeconds(30);
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
                .thinkingEndsAt(thinkingEndsAt)
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