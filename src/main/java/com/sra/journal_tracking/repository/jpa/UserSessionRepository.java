package com.sra.journal_tracking.repository.jpa;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sra.journal_tracking.entity.jpa.UserSession;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
    Optional<UserSession> findByTokenHash(String tokenHash);
    Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);
    void deleteByUser_UserId(UUID userId);
}
