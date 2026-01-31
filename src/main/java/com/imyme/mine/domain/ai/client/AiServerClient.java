package com.imyme.mine.domain.ai.client;

import com.imyme.mine.domain.ai.dto.TranscriptionRequest;
import com.imyme.mine.domain.ai.dto.TranscriptionResponse;
import com.imyme.mine.domain.ai.dto.knowledge.*;
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

import java.util.List;

/**
 * AI 서버 API 클라이언트
 * - STT(Speech-to-Text) API 호출
 * - Knowledge Management API 호출 (RAG 기반 채점 기준 업데이트)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiServerClient {

    private final AiServerProperties properties;
    private final RestTemplate restTemplate;

    // 음성 파일을 텍스트로 변환 (STT)
    public String transcribe(String audioUrl) {
        String url = properties.getBaseUrl() + "/api/v1/transcriptions";

        log.debug("STT API 호출 시작 - url: {}, audioUrl: {}", url, audioUrl);

        // 헤더 설정
        HttpHeaders headers = createHeaders();

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
     * GPU 워밍업 요청 (비동기) : AI 서버의 GPU 콜드 스타트 방지
     * - 실패해도 예외를 던지지 않고 로그만 기록
     */
    public void warmup() {
        String url = properties.getBaseUrl() + "/api/v1/gpu/warmup";

        log.debug("GPU 워밍업 API 호출 시작 - url: {}", url);

        HttpHeaders headers = createHeaders();

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
     * 지식 후보 배치 생성 (Knowledge Candidate Batch)
     * POST /api/v1/knowledge/candidates/batch
     */
    public List<KnowledgeCandidate> createKnowledgeCandidatesBatch(
            KnowledgeCandidateBatchRequest request) {

        String url = properties.getBaseUrl() + "/api/v1/knowledge/candidates/batch";

        log.debug("Knowledge Candidate Batch API 호출 시작 - url: {}, itemCount: {}",
                url, request.items().size());

        // 헤더 설정
        HttpHeaders headers = createHeaders();
        HttpEntity<KnowledgeCandidateBatchRequest> entity = new HttpEntity<>(request, headers);

        try {
            // AI 서버 호출
            ResponseEntity<KnowledgeCandidateBatchResponse> response = restTemplate.postForEntity(
                    url,
                    entity,
                    KnowledgeCandidateBatchResponse.class
            );

            // 응답 검증
            KnowledgeCandidateBatchResponse body = response.getBody();
            if (body == null || body.data() == null) {
                log.error("Knowledge Candidate Batch API 응답 데이터가 null입니다.");
                throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
            }

            if (!Boolean.TRUE.equals(body.success())) {
                log.error("Knowledge Candidate Batch API 실패 응답 - error: {}", body.error());
                handleKnowledgeApiError(body.error());
            }

            log.info("Knowledge Candidate Batch API 호출 성공 - processedCount: {}",
                    body.data().processedCount());

            return body.data().candidates();

        } catch (HttpClientErrorException e) {
            log.error("Knowledge Candidate Batch API 클라이언트 오류 - status: {}, body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw mapToKnowledgeError(e.getStatusCode().value());

        } catch (HttpServerErrorException e) {
            log.error("Knowledge Candidate Batch API 서버 오류 - status: {}, body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);

        } catch (ResourceAccessException e) {
            log.error("Knowledge Candidate Batch API 타임아웃 또는 연결 실패 - message: {}", e.getMessage());
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);

        } catch (BusinessException e) {
            throw e;

        } catch (Exception e) {
            log.error("Knowledge Candidate Batch API 호출 중 예상치 못한 오류 발생", e);
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 지식 평가 및 업데이트 결정 (Knowledge Evaluation)
     * POST /api/v1/knowledge/evaluations
     */
    public KnowledgeEvaluationResponse.Data evaluateKnowledge(
            KnowledgeEvaluationRequest request) {

        String url = properties.getBaseUrl() + "/api/v1/knowledge/evaluations";

        log.debug("Knowledge Evaluation API 호출 시작 - url: {}, candidateSourceId: {}, similarsCount: {}",
                url, request.candidate().sourceId(), request.similars().size());

        // 헤더 설정
        HttpHeaders headers = createHeaders();
        HttpEntity<KnowledgeEvaluationRequest> entity = new HttpEntity<>(request, headers);

        try {
            // AI 서버 호출
            ResponseEntity<KnowledgeEvaluationResponse> response = restTemplate.postForEntity(
                    url,
                    entity,
                    KnowledgeEvaluationResponse.class
            );

            // 응답 검증
            KnowledgeEvaluationResponse body = response.getBody();
            if (body == null || body.data() == null) {
                log.error("Knowledge Evaluation API 응답 데이터가 null입니다.");
                throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
            }

            if (!Boolean.TRUE.equals(body.success())) {
                log.error("Knowledge Evaluation API 실패 응답 - error: {}", body.error());
                handleKnowledgeApiError(body.error());
            }

            log.info("Knowledge Evaluation API 호출 성공 - decision: {}, reasoning: {}",
                    body.data().decision(), body.data().reasoning());

            return body.data();

        } catch (HttpClientErrorException e) {
            log.error("Knowledge Evaluation API 클라이언트 오류 - status: {}, body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw mapToKnowledgeError(e.getStatusCode().value());

        } catch (HttpServerErrorException e) {
            log.error("Knowledge Evaluation API 서버 오류 - status: {}, body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);

        } catch (ResourceAccessException e) {
            log.error("Knowledge Evaluation API 타임아웃 또는 연결 실패 - message: {}", e.getMessage());
            throw new BusinessException(ErrorCode.LLM_TIMEOUT);

        } catch (BusinessException e) {
            throw e;

        } catch (Exception e) {
            log.error("Knowledge Evaluation API 호출 중 예상치 못한 오류 발생", e);
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    // HTTP 헤더 생성 (X-Internal-Secret + Content-Type)
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", properties.getSecret());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // Knowledge API 에러 메시지를 파싱하여 적절한 예외 발생
    private void handleKnowledgeApiError(String errorMessage) {
        if (errorMessage == null) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }

        if (errorMessage.contains("INVALID_FEEDBACK")) {
            throw new BusinessException(ErrorCode.INVALID_FEEDBACK);
        } else if (errorMessage.contains("TOO_MANY_ITEMS")) {
            throw new BusinessException(ErrorCode.TOO_MANY_ITEMS);
        } else if (errorMessage.contains("INVALID_CANDIDATE")) {
            throw new BusinessException(ErrorCode.INVALID_CANDIDATE);
        } else if (errorMessage.contains("TOO_MANY_SIMILARS")) {
            throw new BusinessException(ErrorCode.TOO_MANY_SIMILARS);
        } else if (errorMessage.contains("EMBEDDING_ERROR")) {
            throw new BusinessException(ErrorCode.EMBEDDING_ERROR);
        } else if (errorMessage.contains("LLM_ERROR") || errorMessage.contains("LLM_TIMEOUT")) {
            throw new BusinessException(ErrorCode.LLM_ERROR);
        } else {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    // HTTP 상태 코드를 Knowledge 에러로 매핑
    private BusinessException mapToKnowledgeError(int statusCode) {
        return switch (statusCode) {
            case 400 -> new BusinessException(ErrorCode.INVALID_CANDIDATE);
            case 408 -> new BusinessException(ErrorCode.LLM_TIMEOUT);
            case 422 -> new BusinessException(ErrorCode.INVALID_FEEDBACK);
            default -> new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        };
    }
}
