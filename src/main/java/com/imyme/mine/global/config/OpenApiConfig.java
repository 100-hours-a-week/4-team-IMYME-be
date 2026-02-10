package com.imyme.mine.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.tags.Tag;

import java.util.List;

/**
 * OpenAPI (Swagger) 설정
 * - JWT 인증 스키마 추가
 * - 공통 에러 응답 스키마 정의
 * - API 상세 설명 및 서버 정보
 */
@Configuration
public class OpenApiConfig {

    @Value("${swagger.dev-server-url}")
    private String devServerUrl;

    @Bean
    public OpenAPI openAPI() {
        String jwtSchemeName = "JWT";

        SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwtSchemeName);

        Components components = new Components()
            .addSecuritySchemes(
                jwtSchemeName,
                new SecurityScheme()
                    .name(jwtSchemeName)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT Access Token을 입력하세요 (Bearer prefix 자동 추가)")
            )
            // 공통 에러 응답 스키마 추가
            .addSchemas("ErrorResponse", new Schema<>()
                .type("object")
                .description("에러 응답 구조")
                .addProperty("error", new StringSchema()
                    .description("에러 코드")
                    .example("UNAUTHORIZED"))
                .addProperty("message", new StringSchema()
                    .description("사용자에게 표시할 에러 메시지")
                    .example("인증 토큰이 없거나 만료되었습니다."))
                .addProperty("details", new Schema<>()
                    .type("object")
                    .description("상세 정보 (선택적)"))
                .addProperty("timestamp", new StringSchema()
                    .format("date-time")
                    .description("에러 발생 시각")
                    .example("2024-01-27T12:34:56.789"))
                .addProperty("path", new StringSchema()
                    .description("요청 경로")
                    .example("/users/me"))
            );

        Server prodServer = new Server()
            .url("https://imymemine.kr/server")
            .description("운영 서버 (Production)");

        Server devServer = new Server()
            .url(devServerUrl)  // 환경변수에서 읽어옴
            .description("개발 서버 (Development)");

        Server localServer = new Server()
            .url("http://localhost:8080")
            .description("로컬 서버 (Local)");

        return new OpenAPI()
            .info(apiInfo())
            .addSecurityItem(securityRequirement)
            .components(components)
            .servers(List.of(prodServer, devServer, localServer))
            .addTagsItem(new Tag().name("1. Health").description("서버 상태 확인 API"))
            .addTagsItem(new Tag().name("2. Auth").description("OAuth 로그인, 토큰 관리, 로그아웃 API"))
            .addTagsItem(new Tag().name("3. User").description("프로필 조회/수정, 닉네임 검증, 프로필 이미지 업로드, 회원 탈퇴 API"))
            .addTagsItem(new Tag().name("4. Device").description("기기 등록/삭제, FCM 푸시 토큰 관리 API"))
            .addTagsItem(new Tag().name("5. Category").description("카테고리 조회 및 카테고리별 키워드 조회 API"))
            .addTagsItem(new Tag().name("6. Keyword").description("카테고리별 키워드 전체 조회 API"))
            .addTagsItem(new Tag().name("7. Study Card").description("학습 카드 생성/조회/수정/삭제 API"))
            .addTagsItem(new Tag().name("8. Study Attempt").description("학습 시도 생성/조회/삭제, 오디오 업로드 완료 처리 API"))
            .addTagsItem(new Tag().name("9. Study Audio").description("학습 오디오 업로드용 Presigned URL 발급 API"));
    }

    private Info apiInfo() {
        return new Info()
            .title("Mine API Documentation")
            .version("v1.0.0")
            .contact(new Contact()
                .name("IMYME TEAM")
                .email("support@imymemine.kr")); // TODO : 문의 메일 만들고 수정하기
    }
}
