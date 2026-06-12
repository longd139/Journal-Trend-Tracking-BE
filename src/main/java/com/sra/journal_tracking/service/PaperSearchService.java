package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.paper.*;
import java.util.UUID;

/**
 * Service interface for paper search and filtering functionality
 * Implements UC-06, UC-07, UC-08
 */
public interface PaperSearchService {
    
    /**
     * UC-06: Basic search by keyword/title/abstract
     * Academic User: limited to N searches/month
     * Researcher: unlimited
     * 
     * @param request Search request with query, optional author/journal filters
     * @param userEmail Current user's email
     * @return Paginated search results with paper details
     * @throws UsageLimitExceededException if Academic User hit limit
     */
    PaperSearchResultDTO searchPapers(
        PaperSearchRequestDTO request,
        String userEmail
    );
    
    /**
     * UC-06 variant: Search papers by author name
     * 
     * @param request Request with authorName field set
     * @param userEmail Current user's email
     * @return Paginated search results
     */
    PaperSearchResultDTO searchByAuthor(
        PaperSearchRequestDTO request,
        String userEmail
    );
    
    /**
     * UC-06 variant: Search papers by journal
     * 
     * @param request Request with journalId field set
     * @param userEmail Current user's email
     * @return Paginated search results
     */
    PaperSearchResultDTO searchByJournal(
        PaperSearchRequestDTO request,
        String userEmail
    );
    
    /**
     * UC-07: Advanced filtering (Researcher only)
     * NO usage limit checks
     * 
     * @param filterRequest Filter request with year range, field, open access, citations
     * @param userEmail Current user's email (must be RESEARCHER)
     * @return Paginated filtered results
     * @throws UnauthorizedAccessException if user is not RESEARCHER
     */
    PaperSearchResultDTO advancedFilter(
        PaperAdvancedFilterRequestDTO filterRequest,
        String userEmail
    );
    
    /**
     * UC-08: Get full paper details with metadata
     * Academic User: increments ViewCount, checks limit
     * Researcher: unlimited
     * 
     * @param paperId Paper UUID to retrieve
     * @param userEmail Current user's email
     * @return Complete paper metadata including authors, keywords, sourceUrl
     * @throws PaperNotFoundException if paper doesn't exist
     * @throws UsageLimitExceededException if Academic User hit view limit
     */
    PaperDetailResponseDTO getPaperDetails(
        UUID paperId,
        String userEmail
    );
    
    /**
     * Helper: Check usage and increment counter
     * For Academic Users: check if limit exceeded, then increment
     * For Researchers: no operation needed
     * 
     * @param userId User UUID
     * @param usageType 'search' or 'view'
     * @throws UsageLimitExceededException if limit exceeded
     */
    void checkAndIncrementUsage(
        UUID userId,
        String usageType
    );
    
    /**
     * Helper: Get remaining searches/views for current user
     * 
     * @param userEmail Current user's email
     * @return Usage info with remaining counts and reset date
     */
    UsageLimitResponseDTO getRemainingUsage(String userEmail);
}