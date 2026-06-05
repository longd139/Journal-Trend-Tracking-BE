package com.sra.journal_tracking.dto.bookmark;

import lombok.Data;

@Data
public class BookmarkRequest {

    private String type;

    private String targetId;

    private String note;
}