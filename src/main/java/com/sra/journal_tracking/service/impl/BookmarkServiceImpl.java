package com.sra.journal_tracking.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.sra.journal_tracking.dto.bookmark.BookmarkRequest;
import com.sra.journal_tracking.entity.jpa.Bookmark;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.repository.jpa.BookmarkRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import com.sra.journal_tracking.service.BookmarkService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookmarkServiceImpl implements BookmarkService {

    private static final int MAX_BOOKMARK = 20;

    private final BookmarkRepository bookmarkRepository;
    private final UserRepository userRepository;

    private User getCurrentUser() {

        String email = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new AppException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    public Bookmark createBookmark(BookmarkRequest request) {

        User user = getCurrentUser();

        if (user.getRole() != null &&
            "ACADEMIC_USER".equalsIgnoreCase(
                    user.getRole().getRoleName())) {

            long count =
                    bookmarkRepository.countByUser(user);

            if (count >= MAX_BOOKMARK) {
                throw new AppException(
                        ErrorCode.BOOKMARK_LIMIT_EXCEEDED);
            }
        }

        Bookmark bookmark = Bookmark.builder()
                .user(user)
                .type(request.getType())
                .targetId(request.getTargetId())
                .note(request.getNote())
                .build();

        return bookmarkRepository.save(bookmark);
    }

    @Override
    public List<Bookmark> getBookmarks() {
        return bookmarkRepository.findByUser(getCurrentUser());
    }

    @Override
    public Bookmark updateNote(
            UUID bookmarkId,
            String note) {

        Bookmark bookmark =
                bookmarkRepository.findById(bookmarkId)
                        .orElseThrow(() ->
                                new AppException(
                                        ErrorCode.BOOKMARK_NOT_FOUND));

        bookmark.setNote(note);

        return bookmarkRepository.save(bookmark);
    }

    @Override
    public void deleteBookmark(UUID bookmarkId) {

        Bookmark bookmark =
                bookmarkRepository.findById(bookmarkId)
                        .orElseThrow(() ->
                                new AppException(
                                        ErrorCode.BOOKMARK_NOT_FOUND));

        bookmarkRepository.delete(bookmark);
    }
}