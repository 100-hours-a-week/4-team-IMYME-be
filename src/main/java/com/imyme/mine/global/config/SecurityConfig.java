package com.imyme.mine.global.config;

import com.imyme.mine.global.security.handler.JwtAccessDeniedHandler;
import com.imyme.mine.global.security.handler.JwtAuthenticationEntryPoint;
import com.imyme.mine.global.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 설정
 * - JWT 기반 인증 및 권한 관리
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF 비활성화 (JWT 사용 시 불필요)
                .csrf(AbstractHttpConfigurer::disable)

                // CORS 설정 활성화
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 세션 사용 안 함 (Stateless)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 예외 처리 설정
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)  // 401 처리
                        .accessDeniedHandler(jwtAccessDeniedHandler))  // 403 처리

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
                                "/test/**", // 테스트용 (TODO: 운영 시 제거)
                                "/error",
                                "/swagger-ui/**", // Swagger UI
                                "/v3/api-docs/**") // OpenAPI 문서
                        .permitAll()

                        // 그 외 모든 요청은 인증 필요
                        .anyRequest()
                        .authenticated())

                // JWT 인증 필터 추가 (UsernamePasswordAuthenticationFilter 이전에 실행)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS 설정
     * - 프론트엔드 도메인에서의 API 요청 허용
     * - credentials(쿠키, Authorization 헤더) 포함 요청 허용
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 허용할 Origin (프론트엔드 도메인)
        configuration.setAllowedOrigins(List.of(
                "https://imymemine.kr",           // 운영 프론트엔드
                "http://localhost:3000",          // 로컬 개발 (React 기본 포트)
                "http://localhost:5173"           // 로컬 개발 (Vite 기본 포트)
        ));

        // 허용할 HTTP 메서드
        configuration.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        // 허용할 헤더
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Requested-With"
        ));

        // 노출할 헤더 (프론트엔드에서 읽을 수 있는 헤더)
        configuration.setExposedHeaders(List.of(
                "Authorization",
                "Content-Type"
        ));

        // credentials(쿠키, Authorization 헤더) 포함 허용
        configuration.setAllowCredentials(true);

        // 모든 경로에 CORS 설정 적용
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
