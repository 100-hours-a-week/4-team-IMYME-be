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
 * - CORS 설정
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
                // 1. CSRF 비활성화
                .csrf(AbstractHttpConfigurer::disable)

                // 2. CORS 설정 연결 (아래 corsConfigurationSource 메서드 사용)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 3. 세션 미사용
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 4. 예외 처리
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))

                // 5. 권한 설정 (중복 경로 제거 & 와일드카드 활용)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/health",        // Nginx가 /server 떼고 줌
                                "/server/health", // Nginx 설정 꼬였을 때를 대비해 비상용으로만 남김
                                "/categories/**",
                                "/keywords/**",
                                "/auth/**",
                                "/oauth2/**",     // 혹시 모를 OAuth 기본 경로
                                "/error",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/webjars/**"
                        ).permitAll()
                        .anyRequest().authenticated())

                // 6. 필터 추가
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // 🔥 CORS 설정을 여기서 직접 정의 (가장 안전함)
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 1. 허용할 출처 (명시적으로 지정)
        configuration.setAllowedOrigins(List.of(
                "http://localhost:3000",  // 프론트엔드 로컬
                "http://localhost:8080",  // 백엔드 로컬 (Swagger 등)
                "https://imymemine.kr"   // 운영 서버
        ));

        // 2. 허용할 메소드
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // 3. 허용할 헤더
        configuration.setAllowedHeaders(List.of("*"));

        // 4. 자격 증명 허용 (쿠키, Authorization 헤더 등)
        configuration.setAllowCredentials(true);

        // 5. 브라우저가 Authorization 헤더를 읽을 수 있게 노출
        configuration.setExposedHeaders(List.of("Authorization", "Content-Type"));

        configuration.setMaxAge(3600L); // 1시간 캐시

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
