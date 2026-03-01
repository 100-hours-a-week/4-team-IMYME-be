package com.imyme.mine.domain.pvp.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoomResultResponse {
    private RoomInfo room;
    private CategoryInfo category;
    private KeywordInfo keyword;
    private PvpRoomStatus status;
    private PlayerResult myResult;
    private PlayerResult opponentResult;
    private UserInfo winner;
    private LocalDateTime finishedAt;
    private String message;

    @Getter
    @AllArgsConstructor
    @Builder
    public static class RoomInfo {
        private Long id;
        private String name;
    }

    @Getter
    @AllArgsConstructor
    @Builder
    public static class CategoryInfo {
        private Long id;
        private String name;
    }

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
        private UserInfo user;
        private Integer score;
        private String audioUrl;
        private Integer durationSeconds;
        private String sttText;
        private FeedbackDetail feedback;
    }

    @Getter
    @AllArgsConstructor
    @Builder
    public static class UserInfo {
        private Long id;
        private String nickname;
        private String profileImageUrl;
        private Integer level;
    }

    @Getter
    @AllArgsConstructor
    @Builder
    public static class FeedbackDetail {
        private String summary;
        private java.util.List<String> keywords;
        private String facts;
        private String understanding;

        @com.fasterxml.jackson.annotation.JsonProperty("personalized_feedback")
        private String personalizedFeedback;
    }
}
