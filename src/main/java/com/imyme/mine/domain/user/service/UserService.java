package com.imyme.mine.domain.user.service;

import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.repository.DeviceRepository;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.auth.repository.UserSessionRepository;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 관리 서비스
 * - 회원 탈퇴 등 사용자 관련 비즈니스 로직
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final DeviceRepository deviceRepository;

    /**
     * 회원 탈퇴 처리
     * - User Soft Delete (deleted_at 설정)
     * - 관련 세션 Hard Delete
     * - 기기와 사용자 연결 해제 (lastUser = NULL)
     */
    @Transactional
    public void withdrawUser(Long userId) {
        log.info("회원 탈퇴 시작: userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.ALREADY_DELETED);
        }

        int deletedSessions = userSessionRepository.deleteAllByUserId(userId);
        log.info("삭제된 세션 수: {}", deletedSessions);

        deviceRepository.unlinkAllByUserId(userId);
        log.info("기기 연결 해제 완료");

        userRepository.delete(user);
        log.info("회원 탈퇴 완료: userId={}", userId);
    }
}
