package com.imyme.mine.global.security.jwt;

import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.global.error.ErrorCode;
import com.imyme.mine.global.security.UserPrincipal;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 인증 필터
 * - Authorization 헤더에서 JWT 토큰을 추출하여 검증
 * - 유효한 토큰인 경우 SecurityContext에 인증 정보 설정
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    // JWT 토큰을 추출하고 검증하여 인증 정보 설정
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            // Authorization 헤더에서 JWT 토큰 추출
            String token = getJwtFromRequest(request);

            // 토큰이 존재하고 유효한 경우
            if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
                // 토큰에서 사용자 ID 추출
                Long userId = jwtTokenProvider.getUserIdFromToken(token);

                // 사용자 조회
                // TODO: 캐싱 적용 고려 혹은 토큰에 사용자 정보 포함(사용자 늘면) -> 토큰 정보로만 객체 생성 후 DB 조회 없이 인증 처리
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

                // UserPrincipal 생성
                UserPrincipal userPrincipal = UserPrincipal.from(user);

                // Authentication 객체 생성
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userPrincipal,
                                null,
                                userPrincipal.getAuthorities()
                        );

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // SecurityContext에 인증 정보 설정
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("Set authentication for user: {}", userId);
            }
        } catch (ExpiredJwtException e) {
            request.setAttribute("exception", ErrorCode.TOKEN_EXPIRED.getCode());
        } catch (JwtException | IllegalArgumentException e) {
            request.setAttribute("exception", ErrorCode.INVALID_TOKEN.getCode());
        }

        filterChain.doFilter(request, response);
    }

    // HTTP 요청의 Authorization 헤더에서 Bearer 토큰 추출
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        return null;
    }
}
