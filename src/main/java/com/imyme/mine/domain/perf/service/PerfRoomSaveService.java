package com.imyme.mine.domain.perf.service;

import com.imyme.mine.domain.pvp.entity.PvpRoom;
import com.imyme.mine.domain.pvp.repository.PvpRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * perf 전용 방 저장 서비스
 * PerfBootstrapController에서 직접 @Transactional 메서드를 호출하면
 * Spring 프록시를 타지 않아 트랜잭션이 적용되지 않으므로 별도 @Service로 분리
 */
@Profile("perf")
@Service
@RequiredArgsConstructor
public class PerfRoomSaveService {

    private final PvpRoomRepository pvpRoomRepository;

    @Transactional
    public PvpRoom saveRoom(PvpRoom room) {
        return pvpRoomRepository.saveAndFlush(room);
    }
}