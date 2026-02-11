package com.imyme.mine.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 설정 (STOMP 프로토콜)
 * - /ws 엔드포인트로 WebSocket 연결
 * - SockJS 폴백 지원
 * - 메시지 브로커 설정: /app (클라이언트→서버), /topic (브로드캐스트), /user (개인)
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    private final CorsProperties corsProperties;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(corsProperties.getAllowedOrigins().toArray(new String[0]))
                .addInterceptors(webSocketAuthInterceptor)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 클라이언트 → 서버로 보내는 메시지 prefix
        registry.setApplicationDestinationPrefixes("/app");

        // 서버 → 클라이언트 브로드캐스트 prefix
        // /topic: 여러 사용자에게 전송 (게임 룸 전체)
        // /user: 특정 사용자에게만 전송
        registry.enableSimpleBroker("/topic", "/user");

        // /user 프리픽스를 사용할 때 사용자별 큐 설정
        registry.setUserDestinationPrefix("/user");
    }
}