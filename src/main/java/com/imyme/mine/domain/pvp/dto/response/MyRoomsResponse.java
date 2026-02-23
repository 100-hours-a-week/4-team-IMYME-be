package com.imyme.mine.domain.pvp.dto.response;

import com.imyme.mine.domain.pvp.entity.PvpRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 내 PvP 기록 조회 응답 (4.8)
 */
@Getter
@AllArgsConstructor
@Builder
public class MyRoomsResponse {
    private List<HistoryItem> histories;
    private PageMeta meta;

    @Getter
    @AllArgsConstructor
    @Builder
    public static class HistoryItem {
        private Long id;
        private Long roomId;
        private String categoryName;
        private String keywordName;
        private PvpRole myRole;
        private Integer myScore;
        private Integer myLevel;
        private String opponentNickname;
        private Integer opponentScore;
        private Boolean isWinner;
        private Boolean isHidden;
        private LocalDateTime finishedAt;
    }

    @Getter
    @AllArgsConstructor
    @Builder
    public static class PageMeta {
        private Integer size;
        private Boolean hasNext;
        private String nextCursor;
    }
}
