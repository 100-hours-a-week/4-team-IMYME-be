package com.imyme.mine.domain.card.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 카드 제목 수정 요청 DTO
 *
 * [ PATCH vs PUT ]
 * - PATCH: 부분 수정 (이 API는 title만 수정)
 * - PUT: 전체 교체 (모든 필드 필요)
 * - 현재는 title만 수정 가능하므로 PATCH 사용
 *
 * [ 향후 확장 ]
 * - 다른 필드도 수정 가능해지면 Optional 필드 추가
 * - 예: Optional<Long> keywordId
 */
public record CardUpdateRequest(

    @NotBlank(message = "제목은 필수입니다.")
    @Size(min = 1, max = 20, message = "제목은 1~20자여야 합니다.")
    String title

) {}