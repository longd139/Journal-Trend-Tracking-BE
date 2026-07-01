
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

import com.sra.journal_tracking.dto.bookmark.BookmarkedPaperResponse;
import com.sra.journal_tracking.dto.bookmark.BookmarkRequest;
import com.sra.journal_tracking.dto.bookmark.BookmarkResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.BookmarkService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;

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

    @Operation(
            summary = "Get recent bookmarked papers",
            description = "Returns the 5 most recently bookmarked papers for the current user. "
                    + "Used by the dashboard \"Tài liệu đã lưu\" card for quick access to saved papers. "
                    + "Only returns paper bookmarks, not keyword bookmarks."
    )
    @GetMapping("/recent-papers")
    public ResponseEntity<AppResponse<List<BookmarkedPaperResponse>>> getRecentBookmarkedPapers(
            Authentication authentication) {
        List<BookmarkedPaperResponse> papers = bookmarkService.getRecentBookmarkedPapers(authentication.getName());
        return ResponseEntity.ok(AppResponse.success("Recent bookmarked papers retrieved", papers));
    }
}
