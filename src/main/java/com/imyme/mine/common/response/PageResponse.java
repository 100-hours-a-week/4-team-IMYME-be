package com.imyme.mine.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 페이징 응답 DTO
 *
 * 응답 예시:
 * {
 *   "success": true,
 *   "data": {
 *     "content": [...],
 *     "meta": {
 *       "page": 1,
 *       "size": 20,
 *       "total_elements": 100,
 *       "total_pages": 5,
 *       "is_first": true,
 *       "is_last": false
 *     }
 *   },
 *   "timestamp": "2026-01-21T10:30:00Z"
 * }
 */
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageResponse<T> {

    private final List<T> content;
    private final PageMeta meta;

    /**
     * Spring Data Page 객체를 PageResponse로 변환
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
            .content(page.getContent())
            .meta(PageMeta.from(page))
            .build();
    }

    /**
     * 페이징 메타데이터
     */
    @Getter
    @Builder
    public static class PageMeta {
        private final int page;
        private final int size;
        private final long totalElements;
        private final int totalPages;
        private final boolean isFirst;
        private final boolean isLast;

        public static PageMeta from(Page<?> page) {
            return PageMeta.builder()
                .page(page.getNumber() + 1)  // 0-based → 1-based
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .isFirst(page.isFirst())
                .isLast(page.isLast())
                .build();
        }
    }
}
