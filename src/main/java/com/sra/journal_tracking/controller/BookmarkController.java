
package com.sra.journal_tracking.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sra.journal_tracking.dto.bookmark.BookmarkRequest;
import com.sra.journal_tracking.dto.bookmark.BookmarkResponse;
import com.sra.journal_tracking.dto.bookmark.BulkBookmarkRequest;
import com.sra.journal_tracking.dto.common.BulkDeleteRequest;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.BookmarkService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;

@RestController
@RequestMapping("/api/v1/bookmarks")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @Operation(summary = "Add bookmark", description = "Bookmark a paper or keyword, optionally into a collection. Only one target (paperId or keywordId) allowed per request.")
    @PostMapping
    public ResponseEntity<AppResponse<BookmarkResponse>> addBookmark(
            Authentication authentication,
            @Valid @RequestBody BookmarkRequest request) {
        BookmarkResponse response = bookmarkService.addBookmark(authentication.getName(), request);
        return ResponseEntity.ok(AppResponse.success("Bookmark added successfully", response));
    }

    @Operation(summary = "Bulk bookmark papers", description = "Bookmark multiple papers at once. Already-bookmarked papers are skipped (idempotent).")
    @PostMapping("/bulk")
    public ResponseEntity<AppResponse<java.util.Map<String, Integer>>> bulkBookmark(
            Authentication authentication,
            @Valid @RequestBody BulkBookmarkRequest request) {
        int created = bookmarkService.bulkBookmark(authentication.getName(), request);
        return ResponseEntity.ok(AppResponse.success(created + " bookmarks created",
                java.util.Map.of("created", created)));
    }

    @Operation(summary = "Export bookmarks", description = "Export all bookmarked papers as CSV or JSON.")
    @GetMapping("/export")
    public ResponseEntity<?> exportBookmarks(
            Authentication authentication,
            @RequestParam(defaultValue = "json") String format) {
        List<BookmarkResponse> bookmarks = bookmarkService.getMyBookmarks(
                authentication.getName(), 0, 500, null);

        if ("csv".equalsIgnoreCase(format)) {
            String csv = toCsv(bookmarks);
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=bookmarks.csv")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csv);
        }
        return ResponseEntity.ok(AppResponse.success("Bookmarks exported", bookmarks));
    }

    @Operation(summary = "Bulk delete bookmarks", description = "Delete multiple bookmarks by their IDs. Ownership-verified.")
    @DeleteMapping("/bulk")
    public ResponseEntity<AppResponse<java.util.Map<String, Integer>>> bulkDeleteBookmarks(
            Authentication authentication,
            @Valid @RequestBody BulkDeleteRequest request) {
        int deleted = bookmarkService.bulkDeleteBookmarks(authentication.getName(), request.getIds());
        return ResponseEntity.ok(AppResponse.success(deleted + " bookmarks removed",
                java.util.Map.of("deleted", deleted)));
    }

    @Operation(summary = "Get my bookmarks", description = "List all bookmarks for the current user with pagination. Optionally filter by collection.")
    @GetMapping
    public ResponseEntity<AppResponse<List<BookmarkResponse>>> getMyBookmarks(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID collectionId) {
        List<BookmarkResponse> bookmarks = bookmarkService.getMyBookmarks(authentication.getName(), page, size, collectionId);
        return ResponseEntity.ok(AppResponse.success("Bookmarks retrieved", bookmarks));
    }

    @Operation(summary = "Delete bookmark by ID", description = "Delete a bookmark by its ID. Only the owner can delete.")
    @DeleteMapping("/{bookmarkId}")
    public ResponseEntity<AppResponse<Void>> deleteBookmark(
            Authentication authentication,
            @PathVariable UUID bookmarkId) {
        bookmarkService.deleteBookmark(authentication.getName(), bookmarkId);
        return ResponseEntity.ok(AppResponse.success("Bookmark removed successfully"));
    }

    @Operation(summary = "Delete bookmark by paper", description = "Delete a bookmark by paper ID. Convenience endpoint.")
    @DeleteMapping("/paper/{paperId}")
    public ResponseEntity<AppResponse<Void>> deleteBookmarkByPaper(
            Authentication authentication,
            @PathVariable UUID paperId) {
        bookmarkService.deleteBookmarkByPaper(authentication.getName(), paperId);
        return ResponseEntity.ok(AppResponse.success("Bookmark removed successfully"));
    }

    @Operation(summary = "Delete bookmark by keyword", description = "Delete a bookmark by keyword ID. Convenience endpoint.")
    @DeleteMapping("/keyword/{keywordId}")
    public ResponseEntity<AppResponse<Void>> deleteBookmarkByKeyword(
            Authentication authentication,
            @PathVariable UUID keywordId) {
        bookmarkService.deleteBookmarkByKeyword(authentication.getName(), keywordId);
        return ResponseEntity.ok(AppResponse.success("Bookmark removed successfully"));
    }

    // ── CSV export helper ──

    private String toCsv(List<BookmarkResponse> bookmarks) {
        StringBuilder sb = new StringBuilder();
        sb.append("Title,Journal,Year,Citations,DOI,Keywords\n");
        for (BookmarkResponse bm : bookmarks) {
            sb.append(escapeCsv(bm.getPaperTitle())).append(',')
              .append(escapeCsv("")).append(',') // journal not in response
              .append(escapeCsv("")).append(',') // year not in response
              .append(escapeCsv("")).append(',') // citations not in response
              .append(escapeCsv("")).append(',') // doi not in response
              .append(escapeCsv(bm.getKeywordText() != null ? bm.getKeywordText() : ""))
              .append('\n');
        }
        return sb.toString();
    }

    private String escapeCsv(String value) {
        if (value == null || value.isEmpty()) return "\"\"";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return "\"" + value + "\"";
    }
}
