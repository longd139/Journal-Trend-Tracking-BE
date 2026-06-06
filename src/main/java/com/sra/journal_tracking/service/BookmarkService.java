package com.sra.journal_tracking.service;

import java.util.List;
import java.util.UUID;

import com.sra.journal_tracking.dto.bookmark.BookmarkRequest;
import com.sra.journal_tracking.entity.jpa.Bookmark;

public interface BookmarkService {

    Bookmark createBookmark(BookmarkRequest request);

    List<Bookmark> getBookmarks();

    Bookmark updateNote(UUID bookmarkId, String note);

    void deleteBookmark(UUID bookmarkId);
}