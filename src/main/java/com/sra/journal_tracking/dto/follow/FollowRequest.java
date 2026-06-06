package com.sra.journal_tracking.dto.follow;

import lombok.Data;

@Data
public class FollowRequest {

    private String type;

    private String targetId;
}