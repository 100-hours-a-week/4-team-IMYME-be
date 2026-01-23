package com.imyme.mine.domain.card.controller;

import com.imyme.mine.domain.card.dto.CardCreateRequest;
import com.imyme.mine.domain.card.dto.CardDetailResponse;
import com.imyme.mine.domain.card.dto.CardListResponse;
import com.imyme.mine.domain.card.dto.CardResponse;
import com.imyme.mine.domain.card.dto.CardUpdateRequest;
import com.imyme.mine.domain.card.dto.CardUpdateResponse;
import com.imyme.mine.domain.card.service.CardService;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import com.imyme.mine.global.security.jwt.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping
    public ResponseEntity<CardResponse> createCard(
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody CardCreateRequest request
    ) {
        Long userId = extractUserId(authorization);
        log.info("POST /cards - userId: {}", userId);

        CardResponse response = cardService.createCard(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

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

    @DeleteMapping("/{cardId}")
    public ResponseEntity<Void> deleteCard(
        @RequestHeader("Authorization") String authorization,
        @PathVariable Long cardId
    ) {
        Long userId = extractUserId(authorization);
        log.info("DELETE /cards/{} - userId: {}", cardId, userId);

        cardService.deleteCard(userId, cardId);

        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<CardListResponse> getCards(
        @RequestHeader("Authorization") String authorization,
        @RequestParam(name = "category_id", required = false) Long categoryId,
        @RequestParam(name = "keyword_ids", required = false) List<Long> keywordIds,
        @RequestParam(name = "ghost", required = false, defaultValue = "true") Boolean ghost,
        @RequestParam(name = "sort", required = false, defaultValue = "recent") String sort,
        @RequestParam(name = "cursor", required = false) String cursor,
        @RequestParam(name = "limit", required = false, defaultValue = "20") Integer limit
    ) {
        Long userId = extractUserId(authorization);
        log.info("GET /cards - userId: {}, categoryId: {}, keywordIds: {}, ghost: {}, sort: {}",
            userId, categoryId, keywordIds, ghost, sort);

        boolean excludeGhost = !Boolean.TRUE.equals(ghost);

        CardListResponse response = cardService.getCards(
            userId, categoryId, keywordIds, excludeGhost, sort, cursor, limit
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{cardId}")
    public ResponseEntity<CardDetailResponse> getCardDetail(
        @RequestHeader("Authorization") String authorization,
        @PathVariable Long cardId
    ) {
        Long userId = extractUserId(authorization);
        log.info("GET /cards/{} - userId: {}", cardId, userId);

        CardDetailResponse response = cardService.getCardDetail(userId, cardId);

        return ResponseEntity.ok(response);
    }

    private Long extractUserId(String authorization) {
        String token = jwtTokenProvider.extractToken(authorization);

        if (token == null || !jwtTokenProvider.validateToken(token)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        return jwtTokenProvider.getUserIdFromToken(token);
    }
}
