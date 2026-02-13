package com.imyme.mine.global.config;

import io.sentry.Sentry;
import io.sentry.SentryOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Sentry 수동 초기화 설정
 * Spring Boot 3.4.12와의 호환성 문제로 인해 수동 초기화
 */
@Slf4j
@Configuration
public class SentryConfig {

    @Value("${sentry.dsn}")
    private String dsn;

    @PostConstruct
    public void init() {
        log.info("🚀 Sentry 수동 초기화 시작...");
        log.info("📍 DSN: {}", dsn.substring(0, 30) + "...");

        Sentry.init(options -> {
            options.setDsn(dsn);
            options.setEnvironment("local");
            options.setTracesSampleRate(0.1);
            options.setSendDefaultPii(false);
            options.setDebug(true);  // 디버그 모드 활성화

            log.info("✅ Sentry 초기화 완료!");
        });
    }
}