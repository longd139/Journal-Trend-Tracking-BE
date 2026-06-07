package com.sra.journal_tracking.service.impl;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sra.journal_tracking.dto.bookmark.BookmarkRequest;
import com.sra.journal_tracking.dto.bookmark.BookmarkResponse;
import com.sra.journal_tracking.entity.jpa.Bookmark;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.repository.jpa.BookmarkRepository;
import com.sra.journal_tracking.repository.jpa.KeywordRepository;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.repository.jpa.SystemConfigRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import com.sra.journal_tracking.service.BookmarkService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookmarkServiceImpl implements BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final UserRepository userRepository;
    private final ResearchPaperRepository researchPaperRepository;
    private final KeywordRepository keywordRepository;
    private final SystemConfigRepository systemConfigRepository;

    @Override
    @Transactional
    public BookmarkResponse addBookmark(String email, BookmarkRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Validate XOR: exactly one target must be set
        if (request.getPaperId() == null && request.getKeywordId() == null) {
            throw new AppException(ErrorCode.BOOKMARK_INVALID_TARGET);
        }
        if (request.getPaperId() != null && request.getKeywordId() != null) {
            throw new AppException(ErrorCode.BOOKMARK_INVALID_TARGET);
        }

        // Validate target exists
        if (request.getPaperId() != null) {
            if (!researchPaperRepository.existsById(request.getPaperId())) {
                throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            // Check duplicate
            if (bookmarkRepository.findByUser_UserIdAndPaper_PaperId(user.getUserId(), request.getPaperId()).isPresent()) {
                throw new AppException(ErrorCode.BOOKMARK_ALREADY_EXISTS);
            }
        } else {
            if (!keywordRepository.existsById(request.getKeywordId())) {
                throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            // Check duplicate
            if (bookmarkRepository.findByUser_UserIdAndKeyword_KeywordId(user.getUserId(), request.getKeywordId()).isPresent()) {
                throw new AppException(ErrorCode.BOOKMARK_ALREADY_EXISTS);
            }
        }

        // Check limit for Academic Users
        checkBookmarkLimit(user);

        Bookmark bookmark = Bookmark.builder()
                .user(user)
                .paper(request.getPaperId() != null ? researchPaperRepository.getReferenceById(request.getPaperId()) : null)
                .keyword(request.getKeywordId() != null ? keywordRepository.getReferenceById(request.getKeywordId()) : null)
                .notes(request.getNotes())
                .build();

        bookmark = bookmarkRepository.save(bookmark);
        return mapToResponse(bookmark);
    }

    @Override
    public List<BookmarkResponse> getMyBookmarks(String email, int page, int size) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        return bookmarkRepository
                .findByUser_UserId(user.getUserId(),
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteBookmark(String email, UUID bookmarkId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Bookmark bookmark = bookmarkRepository.findById(bookmarkId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKMARK_NOT_FOUND));

        // Verify ownership
        if (!bookmark.getUser().getUserId().equals(user.getUserId())) {
            throw new AppException(ErrorCode.BOOKMARK_NOT_FOUND);
        }

        bookmarkRepository.delete(bookmark);
    }

    @Override
    @Transactional
    public void deleteBookmarkByPaper(String email, UUID paperId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        bookmarkRepository.deleteByUser_UserIdAndPaper_PaperId(user.getUserId(), paperId);
    }

    @Override
    @Transactional
    public void deleteBookmarkByKeyword(String email, UUID keywordId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        bookmarkRepository.deleteByUser_UserIdAndKeyword_KeywordId(user.getUserId(), keywordId);
    }

    private void checkBookmarkLimit(User user) {
        // Researchers have unlimited bookmarks
        if (user.getRole() != null && "researcher".equalsIgnoreCase(user.getRole().getRoleName())) {
            return;
        }

        long count = bookmarkRepository.countByUser_UserId(user.getUserId());
        int limit = systemConfigRepository.findByConfigKey("academic_max_bookmarks")
                .map(config -> {
                    try {
                        return Integer.parseInt(config.getConfigValue());
                    } catch (NumberFormatException e) {
                        return 50;
                    }
                })
                .orElse(50);

        if (count >= limit) {
            throw new AppException(ErrorCode.BOOKMARK_LIMIT_EXCEEDED);
        }
    }

    private BookmarkResponse mapToResponse(Bookmark bookmark) {
        return BookmarkResponse.builder()
                .bookmarkId(bookmark.getBookmarkId())
                .paperId(bookmark.getPaper() != null ? bookmark.getPaper().getPaperId() : null)
                .paperTitle(bookmark.getPaper() != null ? bookmark.getPaper().getTitle() : null)
                .keywordId(bookmark.getKeyword() != null ? bookmark.getKeyword().getKeywordId() : null)
                .keywordText(bookmark.getKeyword() != null ? bookmark.getKeyword().getKeywordText() : null)
                .notes(bookmark.getNotes())
                .createdAt(bookmark.getCreatedAt())
                .build();
    }
}
