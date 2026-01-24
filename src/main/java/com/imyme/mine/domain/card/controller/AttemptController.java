package com.imyme.mine.domain.card.controller;

import com.imyme.mine.domain.card.dto.AttemptCreateRequest;
import com.imyme.mine.domain.card.dto.AttemptCreateResponse;
import com.imyme.mine.domain.card.dto.AttemptDetailResponse;
import com.imyme.mine.domain.card.dto.UploadCompleteRequest;
import com.imyme.mine.domain.card.dto.UploadCompleteResponse;
import com.imyme.mine.domain.card.service.AttemptService;
import jakarta.validation.Valid;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import com.imyme.mine.global.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/cards/{cardId}/attempts")
@RequiredArgsConstructor
public class AttemptController {

    private final AttemptService attemptService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping
    public ResponseEntity<AttemptCreateResponse> createAttempt(
        @RequestHeader("Authorization") String authorization,
        @PathVariable Long cardId,
        @RequestBody(required = false) AttemptCreateRequest request
    ) {
        Long userId = extractUserId(authorization);
        log.info("POST /cards/{}/attempts - userId: {}", cardId, userId);

        if (request == null) {
            request = new AttemptCreateRequest(null);
        }

        AttemptCreateResponse response = attemptService.createAttempt(userId, cardId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{attemptId}")
    public ResponseEntity<AttemptDetailResponse> getAttemptDetail(
        @RequestHeader("Authorization") String authorization,
        @PathVariable Long cardId,
        @PathVariable Long attemptId
    ) {
        Long userId = extractUserId(authorization);
        log.info("GET /cards/{}/attempts/{} - userId: {}", cardId, attemptId, userId);

        AttemptDetailResponse response = attemptService.getAttemptDetail(userId, cardId, attemptId);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{attemptId}/upload-complete")
    public ResponseEntity<UploadCompleteResponse> uploadComplete(
        @RequestHeader("Authorization") String authorization,
        @PathVariable Long cardId,
        @PathVariable Long attemptId,
        @Valid @RequestBody UploadCompleteRequest request
    ) {
        Long userId = extractUserId(authorization);
        log.info("PUT /cards/{}/attempts/{}/upload-complete - userId: {}", cardId, attemptId, userId);

        UploadCompleteResponse response = attemptService.uploadComplete(userId, cardId, attemptId, request);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{attemptId}")
    public ResponseEntity<Void> deleteAttempt(
        @RequestHeader("Authorization") String authorization,
        @PathVariable Long cardId,
        @PathVariable Long attemptId
    ) {
        Long userId = extractUserId(authorization);
        log.info("DELETE /cards/{}/attempts/{} - userId: {}", cardId, attemptId, userId);

        attemptService.deleteAttempt(userId, cardId, attemptId);

        return ResponseEntity.noContent().build();
    }

    private Long extractUserId(String authorization) {
        String token = jwtTokenProvider.extractToken(authorization);

        if (token == null || !jwtTokenProvider.validateToken(token)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        return jwtTokenProvider.getUserIdFromToken(token);
    }
}