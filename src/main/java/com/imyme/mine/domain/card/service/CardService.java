package com.imyme.mine.domain.card.service;

import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.card.dto.CardCreateRequest;
import com.imyme.mine.domain.card.dto.CardResponse;
import com.imyme.mine.domain.card.dto.CardUpdateRequest;
import com.imyme.mine.domain.card.dto.CardUpdateResponse;
import com.imyme.mine.domain.card.entity.Card;
import com.imyme.mine.domain.card.repository.CardRepository;
import com.imyme.mine.domain.category.entity.Category;
import com.imyme.mine.domain.category.repository.CategoryRepository;
import com.imyme.mine.domain.keyword.entity.Keyword;
import com.imyme.mine.domain.keyword.repository.KeywordRepository;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카드 비즈니스 로직 서비스
 *
 * [ @Service ]
 * - 스프링이 이 클래스를 "비즈니스 로직 담당 빈"으로 등록
 * - Controller에서 의존성 주입으로 사용
 *
 * [ @Transactional ]
 * - 클래스 레벨: 모든 public 메서드에 트랜잭션 적용
 * - readOnly = true: 조회 전용 (성능 최적화, 변경 감지 비활성화)
 * - 쓰기 메서드는 개별적으로 @Transactional 오버라이드
 *
 * [ @RequiredArgsConstructor ]
 * - final 필드 기반 생성자 자동 생성 (Lombok)
 * - 생성자가 1개면 @Autowired 생략 가능
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CardService {

    private final CardRepository cardRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final KeywordRepository keywordRepository;

    /**
     * 카드 생성
     *
     * [ 처리 흐름 ]
     * 1. 사용자 조회 (존재 검증)
     * 2. 카테고리 조회 (존재 검증)
     * 3. 키워드 조회 (존재 검증)
     * 4. Card 엔티티 생성 및 저장
     * 5. User 통계 업데이트 (totalCardCount++, activeCardCount++)
     * 6. CardResponse DTO 반환
     *
     * [ @Transactional ]
     * - readOnly = false (기본값): 쓰기 작업 허용
     * - Card 저장 + User 통계 업데이트가 하나의 트랜잭션으로 처리
     * - 하나라도 실패하면 전체 롤백
     *
     * @param userId 로그인한 사용자 ID
     * @param request 카드 생성 요청 DTO
     * @return 생성된 카드 정보
     * @throws BusinessException USER_NOT_FOUND, CATEGORY_NOT_FOUND, KEYWORD_NOT_FOUND
     */
    @Transactional
    public CardResponse createCard(Long userId, CardCreateRequest request) {
        log.debug("카드 생성 시작 - userId: {}, categoryId: {}, keywordId: {}",
            userId, request.categoryId(), request.keywordId());

        // 1. 사용자 조회
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 카테고리 조회
        Category category = categoryRepository.findById(request.categoryId())
            .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        // 3. 키워드 조회
        Keyword keyword = keywordRepository.findById(request.keywordId())
            .orElseThrow(() -> new BusinessException(ErrorCode.KEYWORD_NOT_FOUND));

        // 4. Card 엔티티 생성
        // Builder 패턴: 가독성 좋고, 필드 순서와 무관하게 객체 생성 가능
        Card card = Card.builder()
            .user(user)
            .category(category)
            .keyword(keyword)
            .title(request.title())
            .build();

        // 5. 저장 (INSERT 쿼리 실행)
        Card savedCard = cardRepository.save(card);

        // 6. User 통계 업데이트
        // - totalCardCount: 누적 생성 카드 수 (레벨 계산에 사용)
        // - activeCardCount: 현재 보유 카드 수 (삭제 시 감소)
        user.incrementTotalCardCount();  // 내부에서 레벨 재계산
        user.incrementActiveCardCount();

        log.info("카드 생성 완료 - cardId: {}, userId: {}", savedCard.getId(), userId);

        // 7. Entity → DTO 변환 후 반환
        return CardResponse.from(savedCard);
    }

    /**
     * 카드 제목 수정
     *
     * [ 처리 흐름 ]
     * 1. 사용자의 카드 조회 (존재 + 소유권 동시 검증)
     * 2. 제목 업데이트
     * 3. CardUpdateResponse DTO 반환
     *
     * [ Dirty Checking ]
     * - JPA는 영속성 컨텍스트 내 엔티티 변경을 자동 감지
     * - 트랜잭션 커밋 시 변경된 필드만 UPDATE 쿼리 실행
     * - card.updateTitle() 호출만으로 DB 반영 (save() 불필요)
     *
     * @param userId 로그인한 사용자 ID
     * @param cardId 수정할 카드 ID
     * @param request 제목 수정 요청 DTO
     * @return 수정된 카드 정보
     * @throws BusinessException CARD_NOT_FOUND (카드 없음 또는 권한 없음)
     */
    @Transactional
    public CardUpdateResponse updateCardTitle(Long userId, Long cardId, CardUpdateRequest request) {
        log.debug("카드 제목 수정 시작 - userId: {}, cardId: {}", userId, cardId);

        // 1. 사용자의 카드 조회 (존재 + 소유권 동시 검증)
        // findByIdAndUserId: 다른 사용자의 카드는 조회되지 않음
        Card card = cardRepository.findByIdAndUserId(cardId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));

        // 2. 제목 업데이트 (Dirty Checking으로 자동 반영)
        card.updateTitle(request.title());

        log.info("카드 제목 수정 완료 - cardId: {}, newTitle: {}", cardId, request.title());

        // 3. DTO 변환 후 반환
        return CardUpdateResponse.from(card);
    }

    /**
     * 카드 삭제 (Soft Delete)
     *
     * [ Soft Delete 방식 ]
     * - Card 엔티티에 @SQLDelete 설정
     * - delete() 호출 시 실제 DELETE 대신 UPDATE deleted_at = NOW() 실행
     * - @SQLRestriction으로 조회 시 자동 필터링
     *
     * [ 처리 흐름 ]
     * 1. 사용자의 카드 조회 (존재 + 소유권 동시 검증)
     * 2. 사용자 조회 (통계 업데이트용)
     * 3. Soft Delete 실행
     * 4. User.activeCardCount 감소
     *
     * @param userId 로그인한 사용자 ID
     * @param cardId 삭제할 카드 ID
     * @throws BusinessException CARD_NOT_FOUND (카드 없음 또는 권한 없음)
     */
    @Transactional
    public void deleteCard(Long userId, Long cardId) {
        log.debug("카드 삭제 시작 - userId: {}, cardId: {}", userId, cardId);

        // 1. 사용자의 카드 조회 (존재 + 소유권 동시 검증)
        Card card = cardRepository.findByIdAndUserId(cardId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));

        // 2. 사용자 조회 (통계 업데이트용)
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 3. Soft Delete 실행
        // @SQLDelete로 인해 UPDATE cards SET deleted_at = NOW() WHERE id = ? 실행
        cardRepository.delete(card);

        // 4. User.activeCardCount 감소
        // totalCardCount는 유지 (누적 기록)
        user.decrementActiveCardCount();

        log.info("카드 삭제 완료 - cardId: {}, userId: {}", cardId, userId);
    }
}