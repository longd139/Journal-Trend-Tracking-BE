package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.report.AuthorImpactReportResponse;
import com.sra.journal_tracking.dto.report.JournalQualityReportResponse;
import com.sra.journal_tracking.dto.report.KeywordTrendReportResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Report generation API — provides analytical reports for keywords, authors, and journals.
 * All endpoints are public (no authentication required).
 */
@RestController
@RequestMapping("/api/public/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Analytical report generation APIs for keywords, authors, and journals")
public class ReportController {

    private final ReportService reportService;

    @Operation(
            summary = "Generate keyword trend report",
            description = "Analyzes whether a research topic is trending up or down. "
                    + "Returns total papers, year-over-year growth rate, trend status (Bùng nổ/Ổn định/Bão hòa), "
                    + "insight text, top 3 related keywords, and yearly breakdown for charts."
    )
    @ApiResponse(responseCode = "200", description = "Keyword trend report generated successfully")
    @GetMapping("/keyword-trend")
    public ResponseEntity<AppResponse<KeywordTrendReportResponse>> getKeywordTrendReport(
            @Parameter(description = "The keyword to analyze", required = true, example = "machine learning")
            @RequestParam String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        KeywordTrendReportResponse report = reportService.getKeywordTrendReport(keyword);
        return ResponseEntity.ok(AppResponse.success("Keyword trend report generated successfully", report));
    }

    @Operation(
            summary = "Generate author impact report",
            description = "Assesses whether an author is a top expert in their field. "
                    + "Returns total papers, h-index, activity status (Đang sung sức/Đã dừng nghiên cứu), "
                    + "insight text, top research field, and collaboration network."
    )
    @ApiResponse(responseCode = "200", description = "Author impact report generated successfully")
    @ApiResponse(responseCode = "404", description = "Author not found")
    @GetMapping("/author-impact")
    public ResponseEntity<AppResponse<AuthorImpactReportResponse>> getAuthorImpactReport(
            @Parameter(description = "The author name to analyze", required = true, example = "Yoshua Bengio")
            @RequestParam String authorName) {
        if (authorName == null || authorName.trim().isEmpty()) {
            throw new AppException(ErrorCode.AUTHOR_NOT_FOUND);
        }
        AuthorImpactReportResponse report = reportService.getAuthorImpactReport(authorName);
        return ResponseEntity.ok(AppResponse.success("Author impact report generated successfully", report));
    }

    @Operation(
            summary = "Generate journal quality report",
            description = "Evaluates a journal's prestige and submission suitability. "
                    + "Returns quartile ranking, impact factor, editorial taste (recent keyword preferences), "
                    + "insight text, total papers, and total citations."
    )
    @ApiResponse(responseCode = "200", description = "Journal quality report generated successfully")
    @GetMapping("/journal-quality")
    public ResponseEntity<AppResponse<JournalQualityReportResponse>> getJournalQualityReport(
            @Parameter(description = "The journal name to analyze", required = true, example = "Nature")
            @RequestParam String journalName) {
        if (journalName == null || journalName.trim().isEmpty()) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        JournalQualityReportResponse report = reportService.getJournalQualityReport(journalName);
        return ResponseEntity.ok(AppResponse.success("Journal quality report generated successfully", report));
    }
}
