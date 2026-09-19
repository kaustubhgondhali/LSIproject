package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.DeviceVerificationOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface DeviceVerificationOtpRepository extends JpaRepository<DeviceVerificationOtp, Long> {

    Optional<DeviceVerificationOtp> findFirstByUserIdAndPurposeAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            Long userId, String purpose, Instant now);

    void deleteByUserIdAndPurpose(Long userId, String purpose);
}

