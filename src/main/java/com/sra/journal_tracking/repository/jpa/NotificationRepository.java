package com.sra.journal_tracking.repository.jpa;

import com.sra.journal_tracking.entity.jpa.Notification;
import com.sra.journal_tracking.entity.jpa.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** Đếm số notification chưa đọc của user (dùng cho badge). */
    long countByUser_UserIdAndIsReadFalse(UUID userId);

    /** Lấy danh sách notification của user, mới nhất trước. */
    Page<Notification> findByUser_UserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** Lấy danh sách notification chưa đọc của user. */
    Page<Notification> findByUser_UserIdAndIsReadFalseOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** Đánh dấu tất cả notification của user là đã đọc. */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.userId = :userId AND n.isRead = false")
    int markAllAsRead(@Param("userId") UUID userId);

    /**
     * Batch delete notifications by their IDs, scoped to a specific user (ownership check).
     * Returns the number of deleted rows.
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.notifId IN :ids AND n.user.userId = :userId")
    int deleteByNotifIdInAndUser_UserId(@Param("ids") List<UUID> ids, @Param("userId") UUID userId);

    /**
     * Đếm số notification cùng type của user kể từ 1 thời điểm (dùng để dedup trong tháng).
     */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user.userId = :userId AND n.type = :type AND n.createdAt >= :since")
    long countByUserAndTypeSince(@Param("userId") UUID userId, @Param("type") NotificationType type, @Param("since") LocalDateTime since);

    // ── Admin notification queries ──

    /** Lấy danh sách notification của user, lọc theo type. */
    Page<Notification> findByUser_UserIdAndTypeOrderByCreatedAtDesc(UUID userId, NotificationType type, Pageable pageable);

    /** Đếm số notification chưa đọc của user, theo type (dùng cho admin badge breakdown). */
    long countByUser_UserIdAndIsReadFalseAndType(UUID userId, NotificationType type);
}
