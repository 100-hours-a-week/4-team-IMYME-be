package com.imyme.mine.domain.learning.service;

import com.imyme.mine.domain.ai.client.AiServerClient;
import com.imyme.mine.domain.ai.dto.solo.*;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Solo 모드 심층 분석 서비스
 * - AI 서버에 분석 요청 및 결과 폴링
 * - Virtual Thread를 사용한 비동기 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoloService {

    private final AiServerClient aiServerClient;
    private final SoloFeedbackSaveService feedbackSaveService;

    private static final int MAX_RETRIES = 60;  // 최대 60회 (3분)
    private static final long POLL_INTERVAL_MS = 3000;  // 3초 간격

    /**
     * Solo 분석을 비동기로 시작
     * - AI 서버에 분석 요청 후 Virtual Thread로 백그라운드 폴링
     *
     * @param attemptId 시도 ID
     * @param userText  사용자 텍스트
     * @param criteria  채점 기준
     * @param history   이전 기록 (선택)
     */
    public void startSoloAnalysisAsync(
        Long attemptId,
        String userText,
        Map<String, Object> criteria,
        List<Map<String, Object>> history
    ) {
        log.info("Solo 분석 시작 - attemptId: {}", attemptId);

        try {
            // 1. AI 서버에 Solo 분석 요청
            SoloSubmissionRequest request = new SoloSubmissionRequest(
                attemptId,
                userText,
                criteria,
                history
            );

            SoloSubmissionData submission = aiServerClient.submitSolo(request);

            log.info("Solo 분석 요청 완료 - attemptId: {}, status: {}",
                attemptId, submission.status());

            // 2. Virtual Thread로 백그라운드 폴링 시작
            Thread.startVirtualThread(() -> {
                pollAndSaveSoloResult(attemptId);
            });

            log.info("Solo 분석 백그라운드 폴링 시작 - attemptId: {}", attemptId);

        } catch (Exception e) {
            log.error("Solo 분석 요청 실패 - attemptId: {}", attemptId, e);
            throw new BusinessException(ErrorCode.AI_ANALYSIS_FAILED);
        }
    }

    /**
     * 백그라운드에서 결과 폴링 및 저장
     * - 3초마다 AI 서버에 결과 조회
     * - 완료되면 DB에 저장
     * - 최대 3분(60회) 대기
     *
     * @param attemptId 시도 ID
     */
    private void pollAndSaveSoloResult(Long attemptId) {
        log.debug("Solo 폴링 시작 - attemptId: {}", attemptId);

        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                // AI 서버에 결과 조회
                SoloResultData response = aiServerClient.pollSoloResult(attemptId);

                log.debug("Solo 폴링 응답 - attemptId: {}, status: {}, retry: {}/{}",
                    attemptId, response.status(), i + 1, MAX_RETRIES);

                // 완료 상태 확인
                if ("completed".equalsIgnoreCase(response.status())) {
                    // 완료되면 DB에 저장
                    if (response.result() != null) {
                        feedbackSaveService.save(attemptId, response.result());
                        log.info("Solo 분석 완료 및 저장 성공 - attemptId: {}", attemptId);
                    } else {
                        log.warn("Solo 분석 완료되었으나 result가 null - attemptId: {}", attemptId);
                    }
                    return;
                }

                // 실패 상태 확인
                if ("failed".equalsIgnoreCase(response.status())) {
                    log.error("Solo 분석 실패 - attemptId: {}", attemptId);
                    throw new BusinessException(ErrorCode.AI_ANALYSIS_FAILED);
                }

                // 아직 진행 중이면 3초 대기
                Thread.sleep(POLL_INTERVAL_MS);

            } catch (InterruptedException e) {
                log.error("Solo 폴링 중단 - attemptId: {}", attemptId, e);
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCode.AI_POLLING_TIMEOUT);

            } catch (BusinessException e) {
                // BusinessException은 그대로 던짐
                throw e;

            } catch (Exception e) {
                log.error("Solo 폴링 에러 - attemptId: {}, retry: {}", attemptId, i + 1, e);
                // 에러 발생 시 계속 재시도
            }
        }

        // 최대 재시도 횟수 초과
        log.warn("Solo 폴링 타임아웃 (3분 초과) - attemptId: {}", attemptId);
        throw new BusinessException(ErrorCode.AI_POLLING_TIMEOUT);
    }

}
