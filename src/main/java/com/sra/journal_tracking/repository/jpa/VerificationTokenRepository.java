package com.sra.journal_tracking.repository.jpa;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sra.journal_tracking.entity.jpa.VerificationToken;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findByToken(String token);

    @Modifying
    @Query("UPDATE VerificationToken t SET t.isUsed = true WHERE t.user.userId = :userId AND t.tokenType = :tokenType AND t.isUsed = false")
    void invalidatePreviousTokens(@Param("userId") UUID userId, @Param("tokenType") VerificationToken.TokenType tokenType);
}
