package com.sra.journal_tracking.dto.follow;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowResponse {

    private UUID followId;
    private UUID journalId;
    private String journalName;
    private UUID topicId;
    private String topicName;
    private UUID keywordId;
    private String keywordText;
    private Boolean notifyEnabled;
    private LocalDateTime createdAt;
}
