package com.imyme.mine.domain.card.controller;

import com.imyme.mine.domain.card.dto.CardCreateRequest;
import com.imyme.mine.domain.card.dto.CardResponse;
import com.imyme.mine.domain.card.dto.CardUpdateRequest;
import com.imyme.mine.domain.card.dto.CardUpdateResponse;
import com.imyme.mine.domain.card.service.CardService;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import com.imyme.mine.global.secret.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 카드 CRUD 컨트롤러
 *
 * [ @RestController ]
 * - @Controller + @ResponseBody 결합
 * - 모든 메서드의 반환값이 JSON으로 직렬화
 *
 * [ @RequestMapping("/cards") ]
 * - 이 컨트롤러의 모든 엔드포인트는 /cards 하위
 * - 실제 URL: /server/cards (context-path 적용)
 *
 * [ 인증 처리 ]
 * - Authorization 헤더에서 Bearer 토큰 추출
 * - JwtTokenProvider로 userId 파싱
 * - 향후 JwtAuthenticationFilter로 리팩토링 가능
 */
@Slf4j
@RestController
@RequestMapping("/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 카드 생성
     *
     * [ API 명세 ]
     * POST /cards
     * - 새로운 학습 카드 생성
     * - 카테고리, 키워드, 제목 필수
     *
     * [ 응답 ]
     * - 201 Created: 카드 생성 성공
     * - 400 Bad Request: 유효성 검증 실패
     * - 401 Unauthorized: 인증 실패
     * - 404 Not Found: 카테고리/키워드 없음
     *
     * @param authorization Bearer 토큰
     * @param request 카드 생성 요청 DTO
     * @return 생성된 카드 정보
     */
    @PostMapping
    public ResponseEntity<CardResponse> createCard(
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody CardCreateRequest request
    ) {
        Long userId = extractUserId(authorization);
        log.info("POST /cards - userId: {}", userId);

        CardResponse response = cardService.createCard(userId, request);

        // 201 Created 반환
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 카드 제목 수정
     *
     * [ API 명세 ]
     * PATCH /cards/{cardId}
     * - 카드 제목만 수정 (부분 수정)
     * - 본인 카드만 수정 가능
     *
     * [ PATCH vs PUT ]
     * - PATCH: 부분 수정 (title만)
     * - PUT: 전체 교체 (모든 필드)
     *
     * [ 응답 ]
     * - 200 OK: 수정 성공
     * - 400 Bad Request: 유효성 검증 실패
     * - 401 Unauthorized: 인증 실패
     * - 404 Not Found: 카드 없음 또는 권한 없음
     *
     * @param authorization Bearer 토큰
     * @param cardId 수정할 카드 ID
     * @param request 제목 수정 요청 DTO
     * @return 수정된 카드 정보
     */
    @PatchMapping("/{cardId}")
    public ResponseEntity<CardUpdateResponse> updateCardTitle(
        @RequestHeader("Authorization") String authorization,
        @PathVariable Long cardId,
        @Valid @RequestBody CardUpdateRequest request
    ) {
        Long userId = extractUserId(authorization);
        log.info("PATCH /cards/{} - userId: {}", cardId, userId);

        CardUpdateResponse response = cardService.updateCardTitle(userId, cardId, request);

        return ResponseEntity.ok(response);
    }

    /**
     * 카드 삭제 (Soft Delete)
     *
     * [ API 명세 ]
     * DELETE /cards/{cardId}
     * - 카드 Soft Delete (deleted_at 설정)
     * - 본인 카드만 삭제 가능
     * - User.activeCardCount 감소
     *
     * [ 응답 ]
     * - 204 No Content: 삭제 성공 (본문 없음)
     * - 401 Unauthorized: 인증 실패
     * - 404 Not Found: 카드 없음 또는 권한 없음
     *
     * @param authorization Bearer 토큰
     * @param cardId 삭제할 카드 ID
     * @return 204 No Content
     */
    @DeleteMapping("/{cardId}")
    public ResponseEntity<Void> deleteCard(
        @RequestHeader("Authorization") String authorization,
        @PathVariable Long cardId
    ) {
        Long userId = extractUserId(authorization);
        log.info("DELETE /cards/{} - userId: {}", cardId, userId);

        cardService.deleteCard(userId, cardId);

        // 204 No Content 반환 (본문 없음)
        return ResponseEntity.noContent().build();
    }

    // ========== Private Helper Methods ==========

    /**
     * Authorization 헤더에서 userId 추출
     *
     * [ 처리 흐름 ]
     * 1. "Bearer " 접두사 제거
     * 2. JWT 토큰 유효성 검증
     * 3. 토큰에서 userId(subject) 추출
     *
     * [ 향후 개선 ]
     * - JwtAuthenticationFilter 구현 후 SecurityContext 활용
     * - @AuthenticationPrincipal 어노테이션으로 간소화
     *
     * @param authorization "Bearer {token}" 형식의 헤더 값
     * @return 추출된 userId
     * @throws BusinessException UNAUTHORIZED (토큰 없거나 유효하지 않음)
     */
    private Long extractUserId(String authorization) {
        // 토큰 추출 (Bearer 접두사 제거)
        String token = jwtTokenProvider.extractToken(authorization);

        if (token == null || !jwtTokenProvider.validateToken(token)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        return jwtTokenProvider.getUserIdFromToken(token);
    }
}