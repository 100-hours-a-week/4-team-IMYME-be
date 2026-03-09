package com.imyme.mine.domain.perf.dto;

public record PerfUserToken(Long userId, String nickname, String deviceUuid, String accessToken) {}