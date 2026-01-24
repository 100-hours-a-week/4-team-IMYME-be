package com.imyme.mine.domain.user.controller;

import com.imyme.mine.domain.user.service.UserService;
import com.imyme.mine.global.security.UserPrincipal;
import com.imyme.mine.global.security.annotation.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 관리 컨트롤러
 * - 회원 탈퇴 등 사용자 관련 엔드포인트
 */
@Slf4j
@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 회원 탈퇴
     * DELETE /users/me
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdrawUser(@CurrentUser UserPrincipal userPrincipal) {
        log.info("회원 탈퇴 요청: userId={}", userPrincipal.getId());

        userService.withdrawUser(userPrincipal.getId());

        log.info("회원 탈퇴 완료: userId={}", userPrincipal.getId());
    }
}