package com.imyme.mine.domain.storage.service;

import com.imyme.mine.domain.card.entity.AttemptStatus;
import com.imyme.mine.domain.card.entity.Card;
import com.imyme.mine.domain.card.entity.CardAttempt;
import com.imyme.mine.domain.card.repository.CardAttemptRepository;
import com.imyme.mine.domain.card.repository.CardRepository;
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
    private final CardRepository cardRepository;
    private final CardAttemptRepository cardAttemptRepository;

    private static final int MAX_ATTEMPTS_PER_CARD = 5;
    private static final Duration PRESIGNED_URL_EXPIRATION = Duration.ofMinutes(10);

    @Transactional
    public PresignedUrlResponse generatePresignedUrl(Long userId, PresignedUrlRequest request) {
        log.debug("Presigned URL 생성 시작 - userId: {}, cardId: {}", userId, request.cardId());

        Card card = cardRepository.findByIdAndUserId(request.cardId(), userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));

        long attemptCount = cardAttemptRepository.countByCardId(card.getId());
        if (attemptCount >= MAX_ATTEMPTS_PER_CARD) {
            throw new BusinessException(ErrorCode.MAX_ATTEMPTS_EXCEEDED);
        }

        Short nextAttemptNo = calculateNextAttemptNo(card.getId());

        CardAttempt attempt = CardAttempt.builder()
            .card(card)
            .attemptNo(nextAttemptNo)
            .status(AttemptStatus.PENDING)
            .build();

        CardAttempt savedAttempt = cardAttemptRepository.save(attempt);

        String objectKey = generateObjectKey(userId, card.getId(), savedAttempt.getId(), request.fileExtension());

        PresignedPutObjectRequest presignedRequest = generatePresignedPutRequest(objectKey, request.fileExtension());

        LocalDateTime expiresAt = LocalDateTime.now().plus(PRESIGNED_URL_EXPIRATION);

        log.info("Presigned URL 생성 완료 - attemptId: {}, objectKey: {}", savedAttempt.getId(), objectKey);

        return PresignedUrlResponse.of(
            savedAttempt.getId(),
            presignedRequest.url().toString(),
            objectKey,
            expiresAt
        );
    }

    private Short calculateNextAttemptNo(Long cardId) {
        Short maxAttemptNo = cardAttemptRepository.findMaxAttemptNoByCardId(cardId);
        return (maxAttemptNo == null) ? 1 : (short) (maxAttemptNo + 1);
    }

    private String generateObjectKey(Long userId, Long cardId, Long attemptId, String fileExtension) {
        String uuid = UUID.randomUUID().toString();
        return String.format("audio/%d/%d/%d_%s.%s", userId, cardId, attemptId, uuid, fileExtension);
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