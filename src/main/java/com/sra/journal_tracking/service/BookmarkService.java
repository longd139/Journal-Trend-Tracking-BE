package com.sra.journal_tracking.service;

import java.util.List;
import java.util.UUID;

import com.sra.journal_tracking.dto.bookmark.BookmarkRequest;
import com.sra.journal_tracking.dto.bookmark.BookmarkResponse;

public interface BookmarkService {

    BookmarkResponse addBookmark(String email, BookmarkRequest request);

    List<BookmarkResponse> getMyBookmarks(String email, int page, int size);

    void deleteBookmark(String email, UUID bookmarkId);

    void deleteBookmarkByPaper(String email, UUID paperId);

    void deleteBookmarkByKeyword(String email, UUID keywordId);
}
