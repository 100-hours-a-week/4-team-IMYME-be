package com.imyme.mine.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 설정
 * - JWT 기반 인증 및 권한 관리
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF 비활성화 (JWT 사용 시 불필요)
                .csrf(csrf -> csrf.disable())

                // 세션 사용 안 함 (Stateless)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // URL별 권한 설정
                .authorizeHttpRequests(auth -> auth
                        // 인증 없이 접근 가능한 경로
                        .requestMatchers(
                                "/",
                                "/health",
                                "/categories/**",
                                "/keywords/**",
                                "/auth/oauth/**", // OAuth 로그인
                                "/auth/refresh", // 토큰 갱신
                                "/auth/logout", // 로그아웃
                                "/error",
                                "/swagger-ui/**", // Swagger UI
                                "/v3/api-docs/**") // OpenAPI 문서
                        .permitAll()

                        // 그 외 모든 요청은 인증 필요
                        .anyRequest()
                        .authenticated());

        return http.build();
    }
}
