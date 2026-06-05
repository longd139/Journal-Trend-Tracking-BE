package com.sra.journal_tracking.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.sra.journal_tracking.dto.bookmark.BookmarkRequest;
import com.sra.journal_tracking.service.BookmarkService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @PostMapping
    public ResponseEntity<?> createBookmark(
            @RequestBody BookmarkRequest request) {

        return ResponseEntity.ok(
                bookmarkService.createBookmark(request));
    }

    @GetMapping
    public ResponseEntity<?> getBookmarks() {

        return ResponseEntity.ok(
                bookmarkService.getBookmarks());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> updateNote(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {

        return ResponseEntity.ok(
                bookmarkService.updateNote(
                        id,
                        body.get("note")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBookmark(
            @PathVariable UUID id) {

        bookmarkService.deleteBookmark(id);

        return ResponseEntity.ok(
                "Bookmark deleted successfully");
    }
}