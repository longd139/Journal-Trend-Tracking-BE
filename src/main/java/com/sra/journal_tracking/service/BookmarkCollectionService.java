package com.sra.journal_tracking.service;

import java.util.List;
import java.util.UUID;

import com.sra.journal_tracking.dto.collection.BookmarkCollectionRequest;
import com.sra.journal_tracking.dto.collection.BookmarkCollectionResponse;

public interface BookmarkCollectionService {

    BookmarkCollectionResponse createCollection(String email, BookmarkCollectionRequest request);

    BookmarkCollectionResponse updateCollection(String email, UUID collectionId, BookmarkCollectionRequest request);

    void deleteCollection(String email, UUID collectionId);

    List<BookmarkCollectionResponse> getMyCollections(String email, int page, int size);

    BookmarkCollectionResponse getCollectionDetail(String email, UUID collectionId);
}
