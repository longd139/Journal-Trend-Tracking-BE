package com.sra.journal_tracking.service;

import java.util.List;
import java.util.UUID;

import com.sra.journal_tracking.dto.bookmark.BookmarkedPaperResponse;
import com.sra.journal_tracking.dto.bookmark.BookmarkRequest;
import com.sra.journal_tracking.dto.bookmark.BookmarkResponse;

public interface BookmarkService {

    BookmarkResponse addBookmark(String email, BookmarkRequest request);

    List<BookmarkResponse> getMyBookmarks(String email, int page, int size, UUID collectionId);

    void deleteBookmark(String email, UUID bookmarkId);

    void deleteBookmarkByPaper(String email, UUID paperId);

    void deleteBookmarkByKeyword(String email, UUID keywordId);

    /**
     * Get the 5 most recently bookmarked papers for the dashboard "Tài liệu đã lưu" card.
     * Only returns paper bookmarks (not keyword bookmarks).
     *
     * @param email the authenticated user's email
     * @return up to 5 BookmarkedPaperResponse, ordered by bookmarkedAt descending
     */
    List<BookmarkedPaperResponse> getRecentBookmarkedPapers(String email);
}
