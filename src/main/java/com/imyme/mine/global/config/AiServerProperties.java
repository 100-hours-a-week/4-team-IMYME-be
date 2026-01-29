package com.imyme.mine.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 서버 설정
 * - application.yml의 ai-server 설정을 바인딩
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai-server")
public class AiServerProperties {

    /**
     * AI 서버 기본 URL (예: https://ai.imyme.com)
     */
    private String baseUrl;

    /**
     * AI 서버 인증용 Secret Key (X-Internal-Secret 헤더)
     */
    private String secret;

    /**
     * API 호출 타임아웃 (초 단위)
     */
    private Integer timeoutSeconds;
}