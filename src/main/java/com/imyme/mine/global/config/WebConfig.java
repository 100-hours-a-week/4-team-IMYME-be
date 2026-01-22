package com.imyme.mine.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 설정
 * - 프론트엔드(Next.js BFF)에서 백엔드 API 호출 허용
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 모든 경로에 대해
                .allowedOrigins(
                        "http://localhost:3000", // Next.js 개발 서버
                        "http://localhost:3001", // 예비 포트
                        "https://imymemine.kr" // 운영 도메인
                        )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true) // 쿠키 허용 (Refresh Token용)
                .maxAge(3600); // Preflight 캐시 1시간
    }
}
