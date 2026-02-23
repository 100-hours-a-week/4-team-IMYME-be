package com.imyme.mine.domain.storage.service;

import com.imyme.mine.domain.card.entity.AttemptStatus;
import com.imyme.mine.domain.card.entity.CardAttempt;
import com.imyme.mine.domain.card.repository.CardAttemptRepository;
import com.imyme.mine.domain.storage.dto.PresignedUrlRequest;
import com.imyme.mine.domain.storage.dto.PresignedUrlResponse;
import com.imyme.mine.global.config.AttemptProperties;
import com.imyme.mine.global.config.S3Properties;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;
    private final CardAttemptRepository cardAttemptRepository;
    private final AttemptProperties attemptProperties;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "audio/mpeg",
        "audio/wav",
        "audio/mp4",
        "audio/m4a",
        "audio/webm"
    );

    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB
    private static final int PVP_URL_EXPIRATION_MINUTES = 5; // 5분

    @Transactional
    public PresignedUrlResponse generatePresignedUrl(Long userId, PresignedUrlRequest request) {
        log.debug("Presigned URL 생성 시작 - userId: {}, attemptId: {}, contentType: {}",
            userId, request.attemptId(), request.contentType());

        CardAttempt attempt = cardAttemptRepository.findById(request.attemptId())
            .orElseThrow(() -> new BusinessException(ErrorCode.ATTEMPT_NOT_FOUND));

        if (!attempt.getCard().getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (attempt.getStatus() != AttemptStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }

        LocalDateTime expiresAt = attempt.getCreatedAt().plus(Duration.ofMinutes(attemptProperties.getUploadExpirationMinutes()));
        if (LocalDateTime.now().isAfter(expiresAt)) {
            throw new BusinessException(ErrorCode.UPLOAD_EXPIRED);
        }

        String contentType = normalizeContentType(request.contentType());
        String extension = getExtensionFromContentType(contentType);

        Long cardId = attempt.getCard().getId();
        String objectKey = generateObjectKey(userId, cardId, attempt.getId(), extension);

        attempt.reserveAudioKey(objectKey);

        PresignedPutObjectRequest presignedRequest = generatePresignedPutRequest(objectKey, contentType);

        LocalDateTime presignedExpiresAt = LocalDateTime.now().plus(Duration.ofMinutes(attemptProperties.getUploadExpirationMinutes()));

        log.info("Presigned URL 생성 완료 - attemptId: {}, objectKey: {}", attempt.getId(), objectKey);

        return PresignedUrlResponse.of(
            attempt.getId(),
            presignedRequest.url().toString(),
            contentType,
            objectKey,
            presignedExpiresAt
        );
    }

    private String generateObjectKey(Long userId, Long cardId, Long attemptId, String fileExtension) {
        String uuid = UUID.randomUUID().toString();
        return String.format("audios/%d/%d/%d_%s.%s", userId, cardId, attemptId, uuid, fileExtension);
    }

    private PresignedPutObjectRequest generatePresignedPutRequest(String objectKey, String contentType) {
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(attemptProperties.getUploadExpirationMinutes()))
            .putObjectRequest(builder -> builder
                .bucket(s3Properties.getBucket())
                .key(objectKey)
                .contentType(contentType)
            )
            .build();

        return s3Presigner.presignPutObject(presignRequest);
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            throw new BusinessException(ErrorCode.INVALID_CONTENT_TYPE);
        }

        String normalized = contentType.split(";")[0].trim().toLowerCase();
        if (!ALLOWED_CONTENT_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_CONTENT_TYPE);
        }

        // alias 처리: audio/m4a는 audio/mp4로 서명
        return "audio/m4a".equals(normalized) ? "audio/mp4" : normalized;
    }

    private String getExtensionFromContentType(String contentType) {
        return switch (contentType) {
            case "audio/mpeg" -> "mp3";
            case "audio/wav" -> "wav";
            case "audio/mp4" -> "m4a";
            case "audio/webm" -> "webm";
            default -> throw new BusinessException(ErrorCode.INVALID_CONTENT_TYPE);
        };
    }

    /**
     * PvP 녹음 제출용 Presigned URL 발급
     */
    public PresignedUrlResponse generatePvpPresignedUrl(Long submissionId, String fileName, String contentType, Long fileSize) {
        log.debug("PvP Presigned URL 생성 시작 - submissionId: {}, contentType: {}, fileSize: {}",
            submissionId, contentType, fileSize);

        // 파일 크기 검증
        if (fileSize > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }

        // Content-Type 정규화 및 검증
        String normalizedContentType = normalizeContentType(contentType);
        String extension = getExtensionFromContentType(normalizedContentType);

        // ObjectKey 생성 (pvp/{submissionId}_{uuid}.{ext})
        String uuid = UUID.randomUUID().toString();
        String objectKey = String.format("pvp/%d_%s.%s", submissionId, uuid, extension);

        // Presigned PUT 요청 생성 (5분 만료)
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(PVP_URL_EXPIRATION_MINUTES))
            .putObjectRequest(builder -> builder
                .bucket(s3Properties.getBucket())
                .key(objectKey)
                .contentType(normalizedContentType)
            )
            .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        LocalDateTime presignedExpiresAt = LocalDateTime.now().plus(Duration.ofMinutes(PVP_URL_EXPIRATION_MINUTES));

        log.info("PvP Presigned URL 생성 완료 - submissionId: {}, objectKey: {}", submissionId, objectKey);

        return PresignedUrlResponse.of(
            submissionId,
            presignedRequest.url().toString(),
            normalizedContentType,
            objectKey,
            presignedExpiresAt
        );
    }

    /**
     * AI 서버 접근용 Presigned GET URL 생성
     * - STT 변환을 위해 AI 서버가 S3 파일을 다운로드할 수 있도록 임시 URL 생성
     * - 1시간 유효
     */
    public String generatePresignedGetUrl(String objectKey) {
        log.debug("Presigned GET URL 생성 시작 - objectKey: {}", objectKey);

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofHours(1)) // AI 서버 처리 시간 고려하여 1시간
            .getObjectRequest(builder -> builder
                .bucket(s3Properties.getBucket())
                .key(objectKey)
            )
            .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        String url = presignedRequest.url().toString();

        log.info("Presigned GET URL 생성 완료 - objectKey: {}", objectKey);
        return url;
    }
}
