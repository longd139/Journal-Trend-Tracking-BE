package com.sra.journal_tracking.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private UUID notifId;
    private String type;
    private String title;
    private String message;
    private UUID relatedPaperId;
    private String relatedPaperTitle;
    private UUID relatedJournalId;
    private String relatedJournalName;
    private UUID relatedTopicId;
    private String relatedTopicName;
    private UUID relatedKeywordId;
    private String relatedKeywordText;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
