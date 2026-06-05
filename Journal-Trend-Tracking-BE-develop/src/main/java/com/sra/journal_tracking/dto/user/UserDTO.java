package com.sra.journal_tracking.dto.user;

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
public class UserDTO {
    private UUID userId;
    private String fullName;
    private String email;
    private String organization;
    private String avatarUrl;
    private String roleName;
    private Boolean status;
    private Integer remainingSearches;
    private Integer remainingViews;
    private LocalDateTime createdAt;
}
