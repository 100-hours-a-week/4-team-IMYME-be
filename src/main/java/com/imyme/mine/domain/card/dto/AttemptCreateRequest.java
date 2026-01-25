package com.imyme.mine.domain.card.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AttemptCreateRequest(

    @JsonProperty("duration_seconds")
    Integer durationSeconds

) {}