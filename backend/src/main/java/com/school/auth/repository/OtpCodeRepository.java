package com.school.auth.repository;

import com.school.auth.domain.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    Optional<OtpCode> findFirstByPhoneOrderByCreatedAtDescIdDesc(String phone);

    @Modifying
    @Transactional
    @Query("delete from OtpCode c where c.phone = :phone")
    int deleteByPhone(@Param("phone") String phone);

    @Modifying
    @Transactional
    @Query("delete from OtpCode c where c.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") OffsetDateTime cutoff);
}
