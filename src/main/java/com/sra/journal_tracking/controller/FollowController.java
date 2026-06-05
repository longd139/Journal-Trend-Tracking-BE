package com.sra.journal_tracking.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.sra.journal_tracking.dto.follow.FollowRequest;
import com.sra.journal_tracking.service.FollowService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @PostMapping
    public ResponseEntity<?> createFollow(
            @RequestBody FollowRequest request) {

        return ResponseEntity.ok(
                followService.createFollow(request));
    }

    @GetMapping
    public ResponseEntity<?> getFollows() {

        return ResponseEntity.ok(
                followService.getFollows());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFollow(
            @PathVariable UUID id) {

        followService.deleteFollow(id);

        return ResponseEntity.ok(
                "Follow deleted successfully");
    }
}