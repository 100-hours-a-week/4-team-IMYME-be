package com.imyme.mine.domain.ai.client;

import com.imyme.mine.domain.ai.dto.TranscriptionRequest;
import com.imyme.mine.domain.ai.dto.TranscriptionResponse;
import com.imyme.mine.domain.ai.dto.SoloSubmissionRequest;
import com.imyme.mine.domain.ai.dto.SoloSubmissionData;
import com.imyme.mine.domain.ai.dto.SoloResultData;
import com.imyme.mine.domain.ai.dto.AiSoloResponse;
import com.imyme.mine.global.config.AiServerProperties;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * AI 서버 API 클라이언트
 * - STT(Speech-to-Text) API 호출
 * - Solo 모드 심층 분석 API 호출
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiServerClient {

    private final AiServerProperties properties;
    private final RestTemplate restTemplate;

    /**
     * 음성 파일을 텍스트로 변환 (STT)
     *
     * @param audioUrl S3 오디오 파일 URL
     * @return 변환된 텍스트
     * @throws BusinessException AI 서버 오류 시
     */
    public String transcribe(String audioUrl) {
        String url = properties.getBaseUrl() + "/api/v1/transcriptions";

        log.debug("STT API 호출 시작 - url: {}, audioUrl: {}", url, audioUrl);

        // 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", properties.getSecret());
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 요청 바디
        TranscriptionRequest request = new TranscriptionRequest(audioUrl);
        HttpEntity<TranscriptionRequest> entity = new HttpEntity<>(request, headers);

        try {
            // AI 서버 호출
            ResponseEntity<TranscriptionResponse> response = restTemplate.postForEntity(
                url,
                entity,
                TranscriptionResponse.class
            );

            // 응답 검증
            TranscriptionResponse body = response.getBody();
            if (body == null || body.getData() == null || body.getData().getText() == null) {
                log.error("STT API 응답 데이터가 null입니다.");
                throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
            }

            if (!Boolean.TRUE.equals(body.getSuccess())) {
                log.error("STT API 실패 응답 - error: {}", body.getError());
                throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
            }

            String sttText = body.getData().getText();
            log.info("STT API 호출 성공 - 텍스트 길이: {}", sttText.length());

            return sttText;

        } catch (HttpClientErrorException e) {
            // 422 Validation Error
            log.error("STT API 클라이언트 오류 - status: {}, body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.INVALID_AUDIO_URL);

        } catch (HttpServerErrorException e) {
            // 500 Internal Server Error
            log.error("STT API 서버 오류 - status: {}, body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);

        } catch (ResourceAccessException e) {
            // Timeout, Connection Error
            log.error("STT API 타임아웃 또는 연결 실패 - message: {}", e.getMessage());
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);

        } catch (BusinessException e) {
            // 이미 처리된 비즈니스 예외는 그대로 전파
            throw e;

        } catch (Exception e) {
            // 기타 예상치 못한 오류
            log.error("STT API 호출 중 예상치 못한 오류 발생", e);
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    /**
     * GPU 워밍업 요청 (비동기)
     * - AI 서버의 GPU 콜드 스타트 방지
     * - 실패해도 예외를 던지지 않고 로그만 기록
     */
    public void warmup() {
        String url = properties.getBaseUrl() + "/api/v1/gpu/warmup";

        log.debug("GPU 워밍업 API 호출 시작 - url: {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", properties.getSecret());
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Void> response = restTemplate.postForEntity(url, entity, Void.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("GPU 워밍업 API 호출 성공 - status: {}", response.getStatusCode());
            } else {
                log.warn("GPU 워밍업 API 비정상 응답 - status: {}", response.getStatusCode());
            }

        } catch (Exception e) {
            // 워밍업 실패는 치명적이지 않으므로 예외를 던지지 않고 로그만 기록
            log.error("GPU 워밍업 API 호출 실패 - message: {}", e.getMessage());
        }
    }

    /**
     * Solo 모드 심층 분석 요청
     * - AI 서버에 분석 요청을 보내고 attemptId와 상태를 받음
     *
     * @param request Solo 분석 요청 (attemptId, userText, criteria, history)
     * @return attemptId와 status ("pending")
     * @throws BusinessException AI 서버 오류 시
     */
    public SoloSubmissionData submitSolo(SoloSubmissionRequest request) {
        String url = properties.getBaseUrl() + "/api/v1/solo/submissions";

        log.debug("Solo 분석 요청 시작 - url: {}, attemptId: {}", url, request.attemptId());

        // 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", properties.getSecret());
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<SoloSubmissionRequest> entity = new HttpEntity<>(request, headers);

        try {
            // AI 서버 호출
            ResponseEntity<AiSoloResponse<SoloSubmissionData>> response = restTemplate.exchange(
                url,
                org.springframework.http.HttpMethod.POST,
                entity,
                new org.springframework.core.ParameterizedTypeReference<AiSoloResponse<SoloSubmissionData>>() {}
            );

            // 응답 검증
            AiSoloResponse<SoloSubmissionData> body = response.getBody();
            if (body == null || body.getData() == null) {
                log.error("Solo 분석 요청 응답 데이터가 null입니다.");
                throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
            }

            if (!Boolean.TRUE.equals(body.getSuccess())) {
                log.error("Solo 분석 요청 실패 응답 - error: {}", body.getError());
                throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
            }

            SoloSubmissionData data = body.getData();
            log.info("Solo 분석 요청 성공 - attemptId: {}, status: {}", data.attemptId(), data.status());
            return data;

        } catch (HttpClientErrorException e) {
            // 422 Validation Error
            log.error("Solo 분석 요청 클라이언트 오류 - status: {}, body: {}",
                e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);

        } catch (HttpServerErrorException e) {
            // 500 Internal Server Error
            log.error("Solo 분석 요청 서버 오류 - status: {}, body: {}",
                e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);

        } catch (ResourceAccessException e) {
            // Timeout, Connection Error
            log.error("Solo 분석 요청 타임아웃 또는 연결 실패 - message: {}", e.getMessage());
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);

        } catch (BusinessException e) {
            throw e;

        } catch (Exception e) {
            log.error("Solo 분석 요청 중 예상치 못한 오류 발생", e);
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    /**
     * Solo 모드 심층 분석 결과 조회
     * - attemptId로 분석 결과를 폴링하여 조회
     *
     * @param attemptId 시도 ID
     * @return 분석 상태 및 결과 (pending / completed / failed)
     * @throws BusinessException AI 서버 오류 시
     */
    public SoloResultData pollSoloResult(Long attemptId) {
        String url = properties.getBaseUrl() + "/api/v1/solo/submissions/" + attemptId;

        log.debug("Solo 분석 결과 조회 - url: {}, attemptId: {}", url, attemptId);

        // 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", properties.getSecret());

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            // AI 서버 호출
            ResponseEntity<AiSoloResponse<SoloResultData>> response = restTemplate.exchange(
                url,
                org.springframework.http.HttpMethod.GET,
                entity,
                new org.springframework.core.ParameterizedTypeReference<AiSoloResponse<SoloResultData>>() {}
            );

            // 응답 검증
            AiSoloResponse<SoloResultData> body = response.getBody();
            if (body == null || body.getData() == null) {
                log.error("Solo 분석 결과 조회 응답 데이터가 null입니다.");
                throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
            }

            SoloResultData data = body.getData();
            log.debug("Solo 분석 결과 조회 성공 - attemptId: {}, status: {}", attemptId, data.status());
            return data;

        } catch (HttpClientErrorException e) {
            log.error("Solo 분석 결과 조회 클라이언트 오류 - status: {}, body: {}",
                e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);

        } catch (HttpServerErrorException e) {
            log.error("Solo 분석 결과 조회 서버 오류 - status: {}, body: {}",
                e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);

        } catch (ResourceAccessException e) {
            log.error("Solo 분석 결과 조회 타임아웃 또는 연결 실패 - message: {}", e.getMessage());
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);

        } catch (BusinessException e) {
            throw e;

        } catch (Exception e) {
            log.error("Solo 분석 결과 조회 중 예상치 못한 오류 발생", e);
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }
}