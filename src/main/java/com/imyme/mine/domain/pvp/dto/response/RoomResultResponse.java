package com.imyme.mine.domain.pvp.dto.response;

import com.imyme.mine.domain.pvp.entity.PvpRoomStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * PvP 결과 조회 응답 (4.7)
 */
@Getter
@AllArgsConstructor
@Builder
public class RoomResultResponse {
    private Long roomId;
    private PvpRoomStatus status;
    private KeywordInfo keyword;
    private PlayerResult myResult;
    private PlayerResult opponentResult;
    private WinnerInfo winner;
    private LocalDateTime finishedAt;
    private String message;

    @Getter
    @AllArgsConstructor
    @Builder
    public static class KeywordInfo {
        private Long id;
        private String name;
    }

    @Getter
    @AllArgsConstructor
    @Builder
    public static class PlayerResult {
        private Long historyId;
        private Boolean isHidden;
        private Long userId;
        private String nickname;
        private Integer score;
        private String audioUrl;
        private Integer durationSeconds;
        private String sttText;
        private FeedbackDetail feedback;
    }

    @Getter
    @AllArgsConstructor
    @Builder
    public static class FeedbackDetail {
        private String summary;
        private String keywords;
        private String facts;
        private String understanding;
        private String socraticFeedback;
    }

    @Getter
    @AllArgsConstructor
    @Builder
    public static class WinnerInfo {
        private Long userId;
        private String nickname;
    }
}
