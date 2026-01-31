package com.imyme.mine.domain.ai.dto;

import java.util.List;
import java.util.Map;

/**
 * AI 서버로 Solo 분석 요청 시 전송하는 DTO
 */
public record SoloSubmissionRequest(
    Long attemptId,
    String userText,
    Map<String, Object> criteria,
    List<Map<String, Object>> history
) {}