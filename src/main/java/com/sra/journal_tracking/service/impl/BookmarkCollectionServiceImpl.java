package com.sra.journal_tracking.service.impl;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sra.journal_tracking.dto.collection.BookmarkCollectionRequest;
import com.sra.journal_tracking.dto.collection.BookmarkCollectionResponse;
import com.sra.journal_tracking.entity.jpa.BookmarkCollection;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.repository.jpa.BookmarkCollectionRepository;
import com.sra.journal_tracking.repository.jpa.BookmarkRepository;
import com.sra.journal_tracking.repository.jpa.SystemConfigRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import com.sra.journal_tracking.service.BookmarkCollectionService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookmarkCollectionServiceImpl implements BookmarkCollectionService {

    private final BookmarkCollectionRepository collectionRepository;
    private final BookmarkRepository bookmarkRepository;
    private final UserRepository userRepository;
    private final SystemConfigRepository systemConfigRepository;

    @Override
    @Transactional
    public BookmarkCollectionResponse createCollection(String email, BookmarkCollectionRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Check duplicate name for this user
        if (collectionRepository.findByUser_UserIdAndNameIgnoreCase(user.getUserId(), request.getName()).isPresent()) {
            throw new AppException(ErrorCode.COLLECTION_NAME_EXISTS);
        }

        // Check limit for Academic Users
        checkCollectionLimit(user);

        BookmarkCollection collection = BookmarkCollection.builder()
                .user(user)
                .name(request.getName())
                .description(request.getDescription())
                .build();

        collection = collectionRepository.save(collection);
        return mapToResponse(collection);
    }

    @Override
    @Transactional
    public BookmarkCollectionResponse updateCollection(String email, UUID collectionId, BookmarkCollectionRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        BookmarkCollection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new AppException(ErrorCode.COLLECTION_NOT_FOUND));

        // Verify ownership
        if (!collection.getUser().getUserId().equals(user.getUserId())) {
            throw new AppException(ErrorCode.COLLECTION_NOT_FOUND);
        }

        // Check duplicate name (excluding current collection)
        collectionRepository.findByUser_UserIdAndNameIgnoreCase(user.getUserId(), request.getName())
                .ifPresent(existing -> {
                    if (!existing.getCollectionId().equals(collectionId)) {
                        throw new AppException(ErrorCode.COLLECTION_NAME_EXISTS);
                    }
                });

        collection.setName(request.getName());
        collection.setDescription(request.getDescription());
        collection = collectionRepository.save(collection);
        return mapToResponse(collection);
    }

    @Override
    @Transactional
    public void deleteCollection(String email, UUID collectionId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        BookmarkCollection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new AppException(ErrorCode.COLLECTION_NOT_FOUND));

        // Verify ownership
        if (!collection.getUser().getUserId().equals(user.getUserId())) {
            throw new AppException(ErrorCode.COLLECTION_NOT_FOUND);
        }

        // Cascade delete: all bookmarks in this collection will be deleted automatically
        collectionRepository.delete(collection);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookmarkCollectionResponse> getMyCollections(String email, int page, int size) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        return collectionRepository
                .findByUser_UserId(user.getUserId(),
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt")))
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public BookmarkCollectionResponse getCollectionDetail(String email, UUID collectionId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        BookmarkCollection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new AppException(ErrorCode.COLLECTION_NOT_FOUND));

        // Verify ownership
        if (!collection.getUser().getUserId().equals(user.getUserId())) {
            throw new AppException(ErrorCode.COLLECTION_NOT_FOUND);
        }

        return mapToResponse(collection);
    }

    private void checkCollectionLimit(User user) {
        // Researchers have unlimited collections
        if (user.getRole() != null && "researcher".equalsIgnoreCase(user.getRole().getRoleName())) {
            return;
        }

        long count = collectionRepository.countByUser_UserId(user.getUserId());
        int limit = systemConfigRepository.findByConfigKey("academic_max_collections")
                .map(config -> {
                    try {
                        return Integer.parseInt(config.getConfigValue());
                    } catch (NumberFormatException e) {
                        return 1;
                    }
                })
                .orElse(1);

        if (count >= limit) {
            throw new AppException(ErrorCode.COLLECTION_LIMIT_EXCEEDED);
        }
    }

    private BookmarkCollectionResponse mapToResponse(BookmarkCollection collection) {
        long bookmarkCount = bookmarkRepository.countByCollection_CollectionId(collection.getCollectionId());
        return BookmarkCollectionResponse.builder()
                .collectionId(collection.getCollectionId())
                .name(collection.getName())
                .description(collection.getDescription())
                .bookmarkCount(bookmarkCount)
                .createdAt(collection.getCreatedAt())
                .updatedAt(collection.getUpdatedAt())
                .build();
    }
}
