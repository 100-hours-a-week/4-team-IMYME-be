package com.imyme.mine.domain.auth.repository;

import com.imyme.mine.domain.auth.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Device 레포지토리
 * - 기기 정보 관리 및 Soft Delete 지원
 */
@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    // deviceUuid로 기기 조회 (Soft Delete 필터 적용)
    Optional<Device> findByDeviceUuid(String deviceUuid);

    // userId로 모든 기기 Soft Delete
    @Modifying
    @Query("UPDATE Device d SET d.deletedAt = CURRENT_TIMESTAMP WHERE d.lastUser.id = :userId AND d.deletedAt IS NULL")
    int softDeleteAllByUserId(@Param("userId") Long userId);

    // deviceUuid로 기기 Soft Delete
    @Modifying
    @Query("UPDATE Device d SET d.deletedAt = CURRENT_TIMESTAMP WHERE d.deviceUuid = :deviceUuid AND d.deletedAt IS NULL")
    int softDeleteByDeviceUuid(@Param("deviceUuid") String deviceUuid);

    // 기기 삭제용 : userId와 deviceUuid로 기기 Soft Delete
    @Modifying
    @Query("UPDATE Device d SET d.deletedAt = CURRENT_TIMESTAMP WHERE d.lastUser.id = :userId AND d.deviceUuid = :deviceUuid AND d.deletedAt IS NULL")
    void softDeleteByUserIdAndDeviceUuid(@Param("userId") Long userId, @Param("deviceUuid") String deviceUuid);

    // 회원 탈퇴용 : 기기를 삭제하는 게 아니라 '주인 없음' 상태로 만듦
    @Modifying
    @Query("UPDATE Device d SET d.lastUser = null WHERE d.lastUser.id = :userId")
    void unlinkAllByUserId(@Param("userId") Long userId);
}
