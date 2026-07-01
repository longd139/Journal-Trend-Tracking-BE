package com.sra.journal_tracking.repository.jpa;

import com.sra.journal_tracking.entity.jpa.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
