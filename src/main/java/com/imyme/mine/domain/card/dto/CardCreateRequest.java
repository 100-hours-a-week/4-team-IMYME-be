package com.imyme.mine.domain.card.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 카드 생성 요청 DTO
 *
 * [ Java Record 사용 이유 ]
 * - 불변(Immutable) 객체로 스레드 안전
 * - getter, equals, hashCode, toString 자동 생성
 * - DTO처럼 데이터 전달 목적의 클래스에 적합
 *
 * [ @JsonProperty ]
 * - JSON의 snake_case를 Java의 camelCase로 매핑
 * - 프론트엔드: category_id → 백엔드: categoryId
 *
 * [ Validation 어노테이션 ]
 * - @NotNull: null 불가
 * - @NotBlank: null, 빈 문자열, 공백만 있는 문자열 불가
 * - @Size: 문자열 길이 제한
 */
public record CardCreateRequest(

    @NotNull(message = "카테고리 ID는 필수입니다.")
    @JsonProperty("category_id")
    Long categoryId,

    @NotNull(message = "키워드 ID는 필수입니다.")
    @JsonProperty("keyword_id")
    Long keywordId,

    @NotBlank(message = "제목은 필수입니다.")
    @Size(min = 1, max = 20, message = "제목은 1~20자여야 합니다.")
    String title

) {}