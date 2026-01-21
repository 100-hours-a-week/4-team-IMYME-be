package com.imyme.mine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 기본 설정
 * - JWT 기반 인증을 위한 Stateless 설정
 * - OAuth2 로그인 활성화
 * - CSRF 비활성화 (JWT 사용 시)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF 비활성화 (JWT 사용 시 불필요)
            .csrf(csrf -> csrf.disable())

            // 세션 사용 안 함 (Stateless)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // URL별 권한 설정
            .authorizeHttpRequests(auth -> auth
                // 인증 없이 접근 가능한 경로
                .requestMatchers(
                    "/",
                    "/health",
                    "/auth/oauth/**",           // OAuth 로그인
                    "/auth/refresh",            // 토큰 갱신
                    "/error"
                ).permitAll()

                // 그 외 모든 요청은 인증 필요
                .anyRequest().authenticated()
            )

            // OAuth2 로그인 설정 (나중에 카카오 추가)
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/auth/oauth/kakao")  // 커스텀 로그인 페이지
            );

        return http.build();
    }
}
