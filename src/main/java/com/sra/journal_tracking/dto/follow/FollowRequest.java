package com.sra.journal_tracking.dto.follow;

import java.util.UUID;

import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowRequest {

    private UUID journalId;
    private UUID topicId;
    private UUID keywordId;

    @Builder.Default
    private Boolean notifyEnabled = true;

    @AssertTrue(message = "Exactly one of journalId, topicId, or keywordId must be provided")
    public boolean isExactlyOneTarget() {
        int count = 0;
        if (journalId != null) count++;
        if (topicId != null) count++;
        if (keywordId != null) count++;
        return count == 1;
    }
}
