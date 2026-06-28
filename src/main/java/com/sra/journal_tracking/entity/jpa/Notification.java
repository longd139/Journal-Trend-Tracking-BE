package com.sra.journal_tracking.entity.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "NOTIFICATION")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"user", "relatedPaper", "relatedJournal", "relatedTopic", "relatedKeyword"})
@ToString(exclude = {"user", "relatedPaper", "relatedJournal", "relatedTopic", "relatedKeyword"})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "NotifID", updatable = false, nullable = false)
    private UUID notifId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserID", nullable = false)
    private User user;

    @Convert(converter = NotificationTypeConverter.class)
    @Column(name = "Type", nullable = false, length = 20)
    private NotificationType type;

    @Column(name = "Title", nullable = false, length = 300)
    private String title;

    @Column(name = "Message", columnDefinition = "NVARCHAR(MAX)")
    private String message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RelatedPaperID")
    private ResearchPaper relatedPaper;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RelatedJournalID")
    private Journal relatedJournal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RelatedTopicID")
    private ResearchTopic relatedTopic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RelatedKeywordID")
    private Keyword relatedKeyword;

    @Column(name = "IsRead", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
