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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sra.journal_tracking.dto.collection.BookmarkCollectionRequest;
import com.sra.journal_tracking.dto.collection.BookmarkCollectionResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.BookmarkCollectionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/bookmark-collections")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class BookmarkCollectionController {

    private final BookmarkCollectionService collectionService;

    @Operation(summary = "Create collection", description = "Create a new bookmark collection to organize saved papers.")
    @PostMapping
    public ResponseEntity<AppResponse<BookmarkCollectionResponse>> createCollection(
            Authentication authentication,
            @Valid @RequestBody BookmarkCollectionRequest request) {
        BookmarkCollectionResponse response = collectionService.createCollection(authentication.getName(), request);
        return ResponseEntity.ok(AppResponse.success("Collection created successfully", response));
    }

    @Operation(summary = "Get my collections", description = "List all bookmark collections for the current user with pagination.")
    @GetMapping
    public ResponseEntity<AppResponse<List<BookmarkCollectionResponse>>> getMyCollections(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<BookmarkCollectionResponse> collections = collectionService.getMyCollections(authentication.getName(), page, size);
        return ResponseEntity.ok(AppResponse.success("Collections retrieved", collections));
    }

    @Operation(summary = "Get collection detail", description = "Get a single bookmark collection by ID. Only the owner can view.")
    @GetMapping("/{collectionId}")
    public ResponseEntity<AppResponse<BookmarkCollectionResponse>> getCollectionDetail(
            Authentication authentication,
            @PathVariable UUID collectionId) {
        BookmarkCollectionResponse response = collectionService.getCollectionDetail(authentication.getName(), collectionId);
        return ResponseEntity.ok(AppResponse.success("Collection retrieved", response));
    }

    @Operation(summary = "Update collection", description = "Update the name or description of a bookmark collection.")
    @PutMapping("/{collectionId}")
    public ResponseEntity<AppResponse<BookmarkCollectionResponse>> updateCollection(
            Authentication authentication,
            @PathVariable UUID collectionId,
            @Valid @RequestBody BookmarkCollectionRequest request) {
        BookmarkCollectionResponse response = collectionService.updateCollection(authentication.getName(), collectionId, request);
        return ResponseEntity.ok(AppResponse.success("Collection updated successfully", response));
    }

    @Operation(summary = "Delete collection", description = "Delete a bookmark collection and all bookmarks inside it.")
    @DeleteMapping("/{collectionId}")
    public ResponseEntity<AppResponse<Void>> deleteCollection(
            Authentication authentication,
            @PathVariable UUID collectionId) {
        collectionService.deleteCollection(authentication.getName(), collectionId);
        return ResponseEntity.ok(AppResponse.success("Collection and its bookmarks removed successfully"));
    }
}
