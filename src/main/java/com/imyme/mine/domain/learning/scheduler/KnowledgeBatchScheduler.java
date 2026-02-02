package com.imyme.mine.domain.learning.scheduler;

import com.imyme.mine.domain.learning.dto.KnowledgeBatchResult;
import com.imyme.mine.domain.learning.service.KnowledgeBatchService;
import com.imyme.mine.global.config.KnowledgeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Knowledge 배치 스케줄러
 * - 매일 자정(00:00) 실행
 * - knowledge.batch.enabled 설정으로 활성화/비활성화 가능
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "knowledge.batch",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true  // 설정이 없으면 기본적으로 활성화
)
public class KnowledgeBatchScheduler {

    private final KnowledgeBatchService batchService;
    private final KnowledgeProperties properties;

    /**
     * 매일 자정 Knowledge 배치 실행
     * - Cron: 0 0 0 * * * (초 분 시 일 월 요일)
     * - 한국 시간 기준 매일 00:00:00
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void executeDailyBatch() {
        if (!properties.isEnabled()) {
            log.debug("Knowledge 배치가 비활성화되어 있습니다. 실행을 건너뜁니다.");
            return;
        }

        log.info("========================================");
        log.info("Knowledge 일일 배치 스케줄러 시작");
        log.info("========================================");

        try {
            KnowledgeBatchResult result = batchService.executeDaily();

            log.info("========================================");
            log.info("Knowledge 일일 배치 스케줄러 완료");
            log.info("총 키워드: {}, 성공: {}, 실패: {}",
                result.totalKeywords(),
                result.successKeywords(),
                result.failedKeywords());
            log.info("총 피드백: {}, 생성: {}, 업데이트: {}, 무시: {}",
                result.totalFeedbacks(),
                result.createdKnowledge(),
                result.updatedKnowledge(),
                result.ignoredCandidates());

            if (!result.errors().isEmpty()) {
                log.warn("배치 실행 중 {}개 오류 발생:", result.errors().size());
                result.errors().forEach(error -> log.warn("  - {}", error));
            }

            log.info("========================================");

        } catch (Exception e) {
            log.error("========================================");
            log.error("Knowledge 일일 배치 스케줄러 실패", e);
            log.error("========================================");
        }
    }
}