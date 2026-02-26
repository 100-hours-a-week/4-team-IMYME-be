package com.imyme.mine.domain.pvp.service;

import com.imyme.mine.domain.keyword.entity.Keyword;
import com.imyme.mine.domain.keyword.repository.KeywordRepository;
import com.imyme.mine.domain.pvp.entity.PvpRoom;
import com.imyme.mine.domain.pvp.entity.PvpRoomStatus;
import com.imyme.mine.domain.pvp.messaging.PvpChannels;
import com.imyme.mine.domain.pvp.messaging.PvpMessage;
import com.imyme.mine.domain.pvp.repository.PvpRoomRepository;
import com.imyme.mine.global.messaging.MessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * PvP 비동기 작업 서비스
 * - @Async 프록시가 정상 작동하도록 별도 클래스로 분리
 * - sleep은 트랜잭션 밖에서 수행하여 DB 커넥션 점유 방지
 *   (@Async 메서드: sleep만 담당 / @Transactional 메서드: DB 작업만 담당)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PvpAsyncService {

    private final PvpRoomRepository pvpRoomRepository;
    private final KeywordRepository keywordRepository;
    private final MessagePublisher messagePublisher;
    private final com.imyme.mine.domain.pvp.websocket.PvpReadyManager pvpReadyManager;

    // Self-injection: 내부 @Async / @Transactional 메서드 호출 시 프록시를 거치도록
    // @Lazy: 순환 참조 방지
    @Lazy
    @Autowired
    private PvpAsyncService self;

    /**
     * 3초 대기 후 THINKING 전환 위임
     * - sleep은 트랜잭션 없이 수행
     */
    @Async
    public void scheduleThinkingTransition(Long roomId) {
        try {
            Thread.sleep(3000);
            self.doThinkingTransition(roomId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("THINKING 전환 중단: roomId={}", roomId, e);
        }
    }

    /**
     * THINKING 전환 DB 작업 (트랜잭션 짧게 유지)
     */
    @Transactional
    public void doThinkingTransition(Long roomId) {
        PvpRoom room = pvpRoomRepository.findByIdWithDetails(roomId).orElse(null);

        if (room == null || room.getStatus() != PvpRoomStatus.MATCHED) {
            log.warn("THINKING 전환 실패: 방 상태 불일치 - roomId={}", roomId);
            return;
        }

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

        final Long keywordId = randomKeyword.getId();
        final String keywordName = randomKeyword.getName();
        final var startedAt = room.getStartedAt();
        final var thinkingEndsAt = startedAt != null ? startedAt.plusSeconds(30) : null;

        // 커밋 후 Redis Pub/Sub 발행
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                messagePublisher.publish(PvpChannels.getRoomChannel(roomId),
                        PvpMessage.thinkingStarted(roomId, keywordId, keywordName, startedAt, thinkingEndsAt));
            }
        });

        // 30초 후 RECORDING 자동 전환 예약
        self.scheduleRecordingTransition(roomId);
    }

    /**
     * 30초 대기 후 RECORDING 전환 위임
     * - sleep은 트랜잭션 없이 수행
     */
    @Async
    public void scheduleRecordingTransition(Long roomId) {
        try {
            Thread.sleep(30000);
            self.doRecordingTransition(roomId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("RECORDING 전환 중단: roomId={}", roomId, e);
        }
    }

    /**
     * RECORDING 전환 DB 작업 (트랜잭션 짧게 유지)
     * - 이미 RECORDING이면 아무 것도 하지 않음 (READY로 조기 전환된 경우)
     * - ready set 삭제
     */
    @Transactional
    public void doRecordingTransition(Long roomId) {
        PvpRoom room = pvpRoomRepository.findByIdWithDetails(roomId).orElse(null);

        if (room == null) {
            log.warn("RECORDING 전환 실패: 방 없음 - roomId={}", roomId);
            return;
        }

        // 이미 RECORDING 이상이면 스킵 (READY로 조기 전환된 경우)
        if (room.getStatus() != PvpRoomStatus.THINKING) {
            log.info("RECORDING 타이머 스킵: 이미 전환됨 - roomId={}, status={}", roomId, room.getStatus());
            pvpReadyManager.clearReady(roomId);
            return;
        }

        room.startRecording();
        pvpRoomRepository.save(room);
        pvpReadyManager.clearReady(roomId);
        log.info("RECORDING 타이머 자동 전환 완료: roomId={}", roomId);

        // 커밋 후 Redis Pub/Sub 발행
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                messagePublisher.publish(PvpChannels.getRoomChannel(roomId),
                        PvpMessage.recordingStarted(roomId));
            }
        });
    }
}
