package com.imyme.mine.domain.storage.service;

import com.imyme.mine.domain.card.entity.AttemptStatus;
import com.imyme.mine.domain.card.entity.CardAttempt;
import com.imyme.mine.domain.card.repository.CardAttemptRepository;
import com.imyme.mine.domain.storage.dto.PresignedUrlRequest;
import com.imyme.mine.domain.storage.dto.PresignedUrlResponse;
import com.imyme.mine.global.config.S3Properties;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;
    private final CardAttemptRepository cardAttemptRepository;

    private static final Duration PRESIGNED_URL_EXPIRATION = Duration.ofMinutes(10);
    private static final Duration UPLOAD_EXPIRATION = Duration.ofMinutes(10);

    @Transactional
    public PresignedUrlResponse generatePresignedUrl(Long userId, PresignedUrlRequest request) {
        log.debug("Presigned URL 생성 시작 - userId: {}, attemptId: {}", userId, request.attemptId());

        CardAttempt attempt = cardAttemptRepository.findById(request.attemptId())
            .orElseThrow(() -> new BusinessException(ErrorCode.ATTEMPT_NOT_FOUND));

        if (!attempt.getCard().getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (attempt.getStatus() != AttemptStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }

        LocalDateTime expiresAt = attempt.getCreatedAt().plus(UPLOAD_EXPIRATION);
        if (LocalDateTime.now().isAfter(expiresAt)) {
            throw new BusinessException(ErrorCode.UPLOAD_EXPIRED);
        }

        Long cardId = attempt.getCard().getId();
        String objectKey = generateObjectKey(userId, cardId, attempt.getId(), request.fileExtension());

        PresignedPutObjectRequest presignedRequest = generatePresignedPutRequest(objectKey, request.fileExtension());

        LocalDateTime presignedExpiresAt = LocalDateTime.now().plus(PRESIGNED_URL_EXPIRATION);

        log.info("Presigned URL 생성 완료 - attemptId: {}, objectKey: {}", attempt.getId(), objectKey);

        return PresignedUrlResponse.of(
            attempt.getId(),
            presignedRequest.url().toString(),
            objectKey,
            presignedExpiresAt
        );
    }

    private String generateObjectKey(Long userId, Long cardId, Long attemptId, String fileExtension) {
        String uuid = UUID.randomUUID().toString();
        return String.format("audios/%d/%d/%d_%s.%s", userId, cardId, attemptId, uuid, fileExtension);
    }

    private PresignedPutObjectRequest generatePresignedPutRequest(String objectKey, String fileExtension) {
        String contentType = getContentType(fileExtension);

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(PRESIGNED_URL_EXPIRATION)
            .putObjectRequest(builder -> builder
                .bucket(s3Properties.getBucket())
                .key(objectKey)
                .contentType(contentType)
            )
            .build();

        return s3Presigner.presignPutObject(presignRequest);
    }

    private String getContentType(String fileExtension) {
        return switch (fileExtension.toLowerCase()) {
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "m4a" -> "audio/mp4";
            case "webm" -> "audio/webm";
            default -> "application/octet-stream";
        };
    }
}
