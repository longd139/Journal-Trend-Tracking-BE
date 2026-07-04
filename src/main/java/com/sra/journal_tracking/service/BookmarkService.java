package com.sra.journal_tracking.service;

import java.util.List;
import java.util.UUID;

import com.sra.journal_tracking.dto.bookmark.BookmarkRequest;
import com.sra.journal_tracking.dto.bookmark.BookmarkResponse;
import com.sra.journal_tracking.dto.bookmark.BulkBookmarkRequest;

public interface BookmarkService {

    BookmarkResponse addBookmark(String email, BookmarkRequest request);

    /** Bookmark multiple papers at once. Returns count of newly created bookmarks. */
    int bulkBookmark(String email, BulkBookmarkRequest request);

    List<BookmarkResponse> getMyBookmarks(String email, int page, int size, UUID collectionId);

    void deleteBookmark(String email, UUID bookmarkId);

    void deleteBookmarkByPaper(String email, UUID paperId);

    void deleteBookmarkByKeyword(String email, UUID keywordId);

    /** Delete multiple bookmarks by their IDs. Returns count of deleted rows. */
    int bulkDeleteBookmarks(String email, List<UUID> bookmarkIds);
}
