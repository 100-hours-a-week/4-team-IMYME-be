package com.imyme.mine.domain.pvp.service;

import com.imyme.mine.domain.keyword.entity.Keyword;
import com.imyme.mine.domain.keyword.repository.KeywordRepository;
import com.imyme.mine.domain.pvp.entity.PvpRoom;
import com.imyme.mine.domain.pvp.entity.PvpRoomStatus;
import com.imyme.mine.domain.pvp.repository.PvpRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * PvP 비동기 작업 서비스
 * - @Async 프록시가 정상 작동하도록 별도 클래스로 분리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PvpAsyncService {

    private final PvpRoomRepository pvpRoomRepository;
    private final KeywordRepository keywordRepository;

    // Self-injection: 내부 @Async 메서드 호출 시 프록시를 거치도록
    // @Lazy: 순환 참조 방지 (빈 생성 시점이 아닌 첫 사용 시점에 주입)
    @Lazy
    @Autowired
    private PvpAsyncService self;

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

            // 30초 후 RECORDING 자동 전환 (self를 통해 프록시 거치기)
            self.scheduleRecordingTransition(roomId);

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
}