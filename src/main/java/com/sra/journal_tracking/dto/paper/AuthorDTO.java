package com.sra.journal_tracking.dto.paper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthorDTO {
    
    /**
     * Author's full name
     */
    private String fullName;
    
    /**
     * Author's affiliation (institution/organization)
     */
    private String affiliation;
    
    /**
     * H-Index score
     */
    private Integer hIndex;
    
    /**
     * Total citation count
     */
    private Integer totalCitations;
    
    /**
     * Position in author list (1-based)
     * 1 = first author, 2 = second, etc.
     */
    private Integer authorOrder;
    
    /**
     * Is this the corresponding author?
     */
    private Boolean isCorresponding;
}