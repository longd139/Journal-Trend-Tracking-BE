package com.sra.journal_tracking.repository.jpa;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sra.journal_tracking.entity.jpa.UserUsage;

@Repository
public interface UserUsageRepository extends JpaRepository<UserUsage, UUID> {

    Optional<UserUsage> findByUser_UserIdAndUsageMonth(UUID userId, String month);

    List<UserUsage> findByUser_UserIdInAndUsageMonth(Collection<UUID> userIds, String usageMonth);

    @Modifying
    @Query("DELETE FROM UserUsage u WHERE u.usageMonth < :usageMonth")
    void deleteByUsageMonthBefore(@Param("usageMonth") String usageMonth);

    @Modifying
    @Query("UPDATE UserUsage u SET u.searchCount = u.searchCount + 1 WHERE u.user.userId = :userId AND u.usageMonth = :month")
    void incrementSearchCount(@Param("userId") UUID userId, @Param("month") String month);

    @Modifying
    @Query("UPDATE UserUsage u SET u.viewCount = u.viewCount + 1 WHERE u.user.userId = :userId AND u.usageMonth = :month")
    void incrementViewCount(@Param("userId") UUID userId, @Param("month") String month);
}
