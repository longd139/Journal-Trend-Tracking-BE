package com.sra.journal_tracking.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.sra.journal_tracking.dto.bookmark.BookmarkRequest;
import com.sra.journal_tracking.dto.bookmark.BookmarkResponse;
import com.sra.journal_tracking.entity.jpa.Bookmark;
import com.sra.journal_tracking.entity.jpa.BookmarkCollection;
import com.sra.journal_tracking.entity.jpa.Keyword;
import com.sra.journal_tracking.entity.jpa.ResearchPaper;
import com.sra.journal_tracking.entity.jpa.Role;
import com.sra.journal_tracking.entity.jpa.SystemConfig;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.repository.jpa.BookmarkCollectionRepository;
import com.sra.journal_tracking.repository.jpa.BookmarkRepository;
import com.sra.journal_tracking.repository.jpa.KeywordRepository;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.repository.jpa.SystemConfigRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BookmarkServiceImpl Unit Tests")
class BookmarkServiceImplTest {

    @Mock
    private BookmarkRepository bookmarkRepository;

    @Mock
    private BookmarkCollectionRepository collectionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ResearchPaperRepository researchPaperRepository;

    @Mock
    private KeywordRepository keywordRepository;

    @Mock
    private SystemConfigRepository systemConfigRepository;

    @InjectMocks
    private BookmarkServiceImpl bookmarkService;

    // --- Test constants ---
    private static final String USER_EMAIL = "test@example.com";
    private static final String OTHER_EMAIL = "other@example.com";

    private UUID userId;
    private UUID otherUserId;
    private UUID paperId;
    private UUID keywordId;
    private UUID collectionId;
    private UUID bookmarkId;

    private User user;
    private User otherUser;
    private ResearchPaper paper;
    private Keyword keyword;
    private BookmarkCollection collection;
    private Bookmark bookmark;
    private Role academicRole;
    private Role researcherRole;

    @BeforeEach
    void setUp() {
        // Generate stable UUIDs (same across calls for easy reference)
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        paperId = UUID.randomUUID();
        keywordId = UUID.randomUUID();
        collectionId = UUID.randomUUID();
        bookmarkId = UUID.randomUUID();

        // Roles
        academicRole = Role.builder()
                .roleId(UUID.randomUUID())
                .roleName("ACADEMIC_USER")
                .build();

        researcherRole = Role.builder()
                .roleId(UUID.randomUUID())
                .roleName("RESEARCHER")
                .build();

        // Users
        user = User.builder()
                .userId(userId)
                .email(USER_EMAIL)
                .fullName("Test User")
                .role(academicRole)
                .build();

        otherUser = User.builder()
                .userId(otherUserId)
                .email(OTHER_EMAIL)
                .fullName("Other User")
                .role(academicRole)
                .build();

        // Paper
        paper = ResearchPaper.builder()
                .paperId(paperId)
                .title("Deep Learning for NLP")
                .build();

        // Keyword
        keyword = Keyword.builder()
                .keywordId(keywordId)
                .keywordText("deep learning")
                .normalizedText("deep learning")
                .build();

        // Collection
        collection = BookmarkCollection.builder()
                .collectionId(collectionId)
                .user(user)
                .name("My Collection")
                .build();

        // Bookmark
        bookmark = Bookmark.builder()
                .bookmarkId(bookmarkId)
                .user(user)
                .paper(paper)
                .collection(collection)
                .notes("Interesting paper")
                .build();
    }

    // ============================================================
    // 1. addBookmark() tests
    // ============================================================
    @Nested
    @DisplayName("addBookmark")
    class AddBookmark {

        @Test
        @DisplayName("✅ Should add bookmark for a paper successfully (no collection)")
        void shouldAddBookmarkForPaper() {
            BookmarkRequest request = BookmarkRequest.builder()
                    .paperId(paperId)
                    .build();

            // Bookmark saved without a collection
            Bookmark savedBookmark = Bookmark.builder()
                    .bookmarkId(bookmarkId)
                    .user(user)
                    .paper(paper)
                    .notes("Interesting paper")
                    .build();

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
            when(researchPaperRepository.existsById(paperId)).thenReturn(true);
            when(bookmarkRepository.findByUser_UserIdAndPaper_PaperId(userId, paperId))
                    .thenReturn(Optional.empty());
            when(researchPaperRepository.getReferenceById(paperId)).thenReturn(paper);
            when(bookmarkRepository.save(any(Bookmark.class))).thenReturn(savedBookmark);
            // Academic user: no config → default limit 5
            when(systemConfigRepository.findByConfigKey("academic_max_bookmarks"))
                    .thenReturn(Optional.empty());
            when(bookmarkRepository.countByUser_UserId(userId)).thenReturn(0L);

            BookmarkResponse response = bookmarkService.addBookmark(USER_EMAIL, request);

            assertNotNull(response);
            assertEquals(bookmarkId, response.getBookmarkId());
            assertEquals(paperId, response.getPaperId());
            assertEquals("Deep Learning for NLP", response.getPaperTitle());
            assertNull(response.getKeywordId());
            assertNull(response.getCollectionId());
            assertEquals("Interesting paper", response.getNotes());

            verify(bookmarkRepository).save(any(Bookmark.class));
        }

        @Test
        @DisplayName("✅ Should add bookmark for a keyword successfully")
        void shouldAddBookmarkForKeyword() {
            BookmarkRequest request = BookmarkRequest.builder()
                    .keywordId(keywordId)
                    .build();

            Bookmark keywordBookmark = Bookmark.builder()
                    .bookmarkId(bookmarkId)
                    .user(user)
                    .keyword(keyword)
                    .notes("Follow this topic")
                    .build();

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
            when(keywordRepository.existsById(keywordId)).thenReturn(true);
            when(bookmarkRepository.findByUser_UserIdAndKeyword_KeywordId(userId, keywordId))
                    .thenReturn(Optional.empty());
            when(keywordRepository.getReferenceById(keywordId)).thenReturn(keyword);
            when(bookmarkRepository.save(any(Bookmark.class))).thenReturn(keywordBookmark);
            when(systemConfigRepository.findByConfigKey("academic_max_bookmarks"))
                    .thenReturn(Optional.empty());
            when(bookmarkRepository.countByUser_UserId(userId)).thenReturn(0L);

            BookmarkResponse response = bookmarkService.addBookmark(USER_EMAIL, request);

            assertNotNull(response);
            assertEquals(keywordId, response.getKeywordId());
            assertEquals("deep learning", response.getKeywordText());
            assertNull(response.getPaperId());
            assertEquals("Follow this topic", response.getNotes());
        }

        @Test
        @DisplayName("✅ Should add bookmark with a collection")
        void shouldAddBookmarkWithCollection() {
            BookmarkRequest request = BookmarkRequest.builder()
                    .paperId(paperId)
                    .collectionId(collectionId)
                    .build();

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
            when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
            when(researchPaperRepository.existsById(paperId)).thenReturn(true);
            when(bookmarkRepository.findByUser_UserIdAndPaper_PaperId(userId, paperId))
                    .thenReturn(Optional.empty());
            when(researchPaperRepository.getReferenceById(paperId)).thenReturn(paper);
            when(bookmarkRepository.save(any(Bookmark.class))).thenReturn(bookmark);
            when(systemConfigRepository.findByConfigKey("academic_max_bookmarks"))
                    .thenReturn(Optional.empty());
            when(bookmarkRepository.countByUser_UserId(userId)).thenReturn(0L);

            BookmarkResponse response = bookmarkService.addBookmark(USER_EMAIL, request);

            assertEquals(collectionId, response.getCollectionId());
            assertEquals("My Collection", response.getCollectionName());
        }

        @Test
        @DisplayName("❌ Should throw when user not found")
        void shouldThrowWhenUserNotFound() {
            BookmarkRequest request = BookmarkRequest.builder()
                    .paperId(paperId)
                    .build();

            when(userRepository.findByEmail("ghost@example.com"))
                    .thenReturn(Optional.empty());

            AppException ex = assertThrows(AppException.class,
                    () -> bookmarkService.addBookmark("ghost@example.com", request));
            assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("❌ Should throw when collection not found")
        void shouldThrowWhenCollectionNotFound() {
            UUID fakeCollectionId = UUID.randomUUID();
            BookmarkRequest request = BookmarkRequest.builder()
                    .paperId(paperId)
                    .collectionId(fakeCollectionId)
                    .build();

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
            when(collectionRepository.findById(fakeCollectionId)).thenReturn(Optional.empty());

            AppException ex = assertThrows(AppException.class,
                    () -> bookmarkService.addBookmark(USER_EMAIL, request));
            assertEquals(ErrorCode.COLLECTION_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("❌ Should throw when collection belongs to another user")
        void shouldThrowWhenCollectionBelongsToOtherUser() {
            UUID otherCollectionId = UUID.randomUUID();
            BookmarkCollection otherCollection = BookmarkCollection.builder()
                    .collectionId(otherCollectionId)
                    .user(otherUser)
                    .name("Other's Collection")
                    .build();

            BookmarkRequest request = BookmarkRequest.builder()
                    .paperId(paperId)
                    .collectionId(otherCollectionId)
                    .build();

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
            when(collectionRepository.findById(otherCollectionId)).thenReturn(Optional.of(otherCollection));

            AppException ex = assertThrows(AppException.class,
                    () -> bookmarkService.addBookmark(USER_EMAIL, request));
            assertEquals(ErrorCode.COLLECTION_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("❌ Should throw when both paperId and keywordId are null")
        void shouldThrowWhenNoTarget() {
            BookmarkRequest request = BookmarkRequest.builder().build(); // neither set

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));

            AppException ex = assertThrows(AppException.class,
                    () -> bookmarkService.addBookmark(USER_EMAIL, request));
            assertEquals(ErrorCode.BOOKMARK_INVALID_TARGET, ex.getErrorCode());
        }

        @Test
        @DisplayName("❌ Should throw when both paperId and keywordId are set")
        void shouldThrowWhenBothTargetsSet() {
            BookmarkRequest request = BookmarkRequest.builder()
                    .paperId(paperId)
                    .keywordId(keywordId)
                    .build();

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));

            AppException ex = assertThrows(AppException.class,
                    () -> bookmarkService.addBookmark(USER_EMAIL, request));
            assertEquals(ErrorCode.BOOKMARK_INVALID_TARGET, ex.getErrorCode());
        }

        @Test
        @DisplayName("❌ Should throw when paper does not exist")
        void shouldThrowWhenPaperNotFound() {
            UUID fakePaperId = UUID.randomUUID();
            BookmarkRequest request = BookmarkRequest.builder()
                    .paperId(fakePaperId)
                    .build();

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
            when(researchPaperRepository.existsById(fakePaperId)).thenReturn(false);

            AppException ex = assertThrows(AppException.class,
                    () -> bookmarkService.addBookmark(USER_EMAIL, request));
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("❌ Should throw when keyword does not exist")
        void shouldThrowWhenKeywordNotFound() {
            UUID fakeKeywordId = UUID.randomUUID();
            BookmarkRequest request = BookmarkRequest.builder()
                    .keywordId(fakeKeywordId)
                    .build();

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
            when(keywordRepository.existsById(fakeKeywordId)).thenReturn(false);

            AppException ex = assertThrows(AppException.class,
                    () -> bookmarkService.addBookmark(USER_EMAIL, request));
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("❌ Should throw when bookmark already exists (duplicate)")
        void shouldThrowWhenDuplicateBookmark() {
            BookmarkRequest request = BookmarkRequest.builder()
                    .paperId(paperId)
                    .build();

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
            when(researchPaperRepository.existsById(paperId)).thenReturn(true);
            when(bookmarkRepository.findByUser_UserIdAndPaper_PaperId(userId, paperId))
                    .thenReturn(Optional.of(bookmark));

            AppException ex = assertThrows(AppException.class,
                    () -> bookmarkService.addBookmark(USER_EMAIL, request));
            assertEquals(ErrorCode.BOOKMARK_ALREADY_EXISTS, ex.getErrorCode());

            verify(bookmarkRepository, never()).save(any());
        }

        @Test
        @DisplayName("❌ Should throw when academic user exceeds bookmark limit")
        void shouldThrowWhenLimitExceeded() {
            BookmarkRequest request = BookmarkRequest.builder()
                    .paperId(paperId)
                    .build();

            // Academic user with role name "academic_user" (not "researcher")
            Role acadRole = Role.builder().roleId(UUID.randomUUID()).roleName("academic_user").build();
            User acadUser = User.builder()
                    .userId(userId)
                    .email(USER_EMAIL)
                    .fullName("Test User")
                    .role(acadRole)
                    .build();

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(acadUser));
            when(researchPaperRepository.existsById(paperId)).thenReturn(true);
            when(bookmarkRepository.findByUser_UserIdAndPaper_PaperId(userId, paperId))
                    .thenReturn(Optional.empty());
            when(bookmarkRepository.countByUser_UserId(userId)).thenReturn(5L); // at limit
            SystemConfig limitConfig = SystemConfig.builder()
                    .configKey("academic_max_bookmarks")
                    .configValue("5")
                    .build();
            when(systemConfigRepository.findByConfigKey("academic_max_bookmarks"))
                    .thenReturn(Optional.of(limitConfig));

            AppException ex = assertThrows(AppException.class,
                    () -> bookmarkService.addBookmark(USER_EMAIL, request));
            assertEquals(ErrorCode.BOOKMARK_LIMIT_EXCEEDED, ex.getErrorCode());
        }

        @Test
        @DisplayName("✅ Researcher should bypass bookmark limit")
        void shouldAllowResearcherUnlimitedBookmarks() {
            BookmarkRequest request = BookmarkRequest.builder()
                    .paperId(paperId)
                    .build();

            User researcherUser = User.builder()
                    .userId(userId)
                    .email("researcher@example.com")
                    .fullName("Researcher User")
                    .role(Role.builder().roleId(UUID.randomUUID()).roleName("researcher").build())
                    .build();

            when(userRepository.findByEmail("researcher@example.com"))
                    .thenReturn(Optional.of(researcherUser));
            when(researchPaperRepository.existsById(paperId)).thenReturn(true);
            when(bookmarkRepository.findByUser_UserIdAndPaper_PaperId(userId, paperId))
                    .thenReturn(Optional.empty());
            when(researchPaperRepository.getReferenceById(paperId)).thenReturn(paper);
            when(bookmarkRepository.save(any(Bookmark.class))).thenReturn(bookmark);
            // count and config should NOT be checked for researcher
            when(bookmarkRepository.countByUser_UserId(userId)).thenReturn(999L); // would fail if checked

            BookmarkResponse response = bookmarkService.addBookmark("researcher@example.com", request);

            assertNotNull(response);
            // Verify limit check was skipped for researcher (count was never called
            // because role check short-circuits)
            verify(systemConfigRepository, never()).findByConfigKey("academic_max_bookmarks");
        }
    }

    // ============================================================
    // 2. getMyBookmarks() tests
    // ============================================================
    @Nested
    @DisplayName("getMyBookmarks")
    class GetMyBookmarks {

        @Test
        @DisplayName("✅ Should return paginated bookmarks (no collection filter)")
        void shouldGetAllBookmarks() {
            List<Bookmark> bookmarkList = List.of(bookmark);
            Page<Bookmark> page = new PageImpl<>(bookmarkList,
                    PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")),
                    1);

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
            when(bookmarkRepository.findByUser_UserId(eq(userId), any(Pageable.class)))
                    .thenReturn(page);

            List<BookmarkResponse> responses = bookmarkService.getMyBookmarks(USER_EMAIL, 0, 20, null);

            assertNotNull(responses);
            assertEquals(1, responses.size());
            assertEquals(bookmarkId, responses.get(0).getBookmarkId());
            assertEquals(paperId, responses.get(0).getPaperId());
        }

        @Test
        @DisplayName("✅ Should return bookmarks filtered by collection")
        void shouldGetBookmarksByCollection() {
            List<Bookmark> bookmarkList = List.of(bookmark);
            Page<Bookmark> page = new PageImpl<>(bookmarkList,
                    PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")),
                    1);

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
            when(bookmarkRepository.findByUser_UserIdAndCollection_CollectionId(
                    eq(userId), eq(collectionId), any(Pageable.class)))
                    .thenReturn(page);

            List<BookmarkResponse> responses = bookmarkService.getMyBookmarks(
                    USER_EMAIL, 0, 20, collectionId);

            assertNotNull(responses);
            assertEquals(1, responses.size());
            assertEquals(collectionId, responses.get(0).getCollectionId());
            assertEquals("My Collection", responses.get(0).getCollectionName());
        }

        @Test
        @DisplayName("✅ Should return empty list when no bookmarks exist")
        void shouldReturnEmptyList() {
            Page<Bookmark> emptyPage = new PageImpl<>(List.of(),
                    PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")),
                    0);

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
            when(bookmarkRepository.findByUser_UserId(eq(userId), any(Pageable.class)))
                    .thenReturn(emptyPage);

            List<BookmarkResponse> responses = bookmarkService.getMyBookmarks(USER_EMAIL, 0, 20, null);

            assertNotNull(responses);
            assertTrue(responses.isEmpty());
        }

        @Test
        @DisplayName("❌ Should throw when user not found")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            AppException ex = assertThrows(AppException.class,
                    () -> bookmarkService.getMyBookmarks("ghost@example.com", 0, 20, null));
            assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
        }
    }

    // ============================================================
    // 3. deleteBookmark() tests
    // ============================================================
    @Nested
    @DisplayName("deleteBookmark (by bookmarkId)")
    class DeleteBookmark {

        @Test
        @DisplayName("✅ Should delete bookmark (owner deletes own bookmark)")
        void shouldDeleteOwnBookmark() {
            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
            when(bookmarkRepository.findById(bookmarkId)).thenReturn(Optional.of(bookmark));

            bookmarkService.deleteBookmark(USER_EMAIL, bookmarkId);

            verify(bookmarkRepository).delete(bookmark);
        }

        @Test
        @DisplayName("❌ Should throw when bookmark not found")
        void shouldThrowWhenBookmarkNotFound() {
            UUID fakeBookmarkId = UUID.randomUUID();
            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
            when(bookmarkRepository.findById(fakeBookmarkId)).thenReturn(Optional.empty());

            AppException ex = assertThrows(AppException.class,
                    () -> bookmarkService.deleteBookmark(USER_EMAIL, fakeBookmarkId));
            assertEquals(ErrorCode.BOOKMARK_NOT_FOUND, ex.getErrorCode());

            verify(bookmarkRepository, never()).delete(any());
        }

        @Test
        @DisplayName("❌ Should throw when non-owner tries to delete")
        void shouldThrowWhenNonOwnerDeletes() {
            when(userRepository.findByEmail(OTHER_EMAIL)).thenReturn(Optional.of(otherUser));
            when(bookmarkRepository.findById(bookmarkId)).thenReturn(Optional.of(bookmark));

            AppException ex = assertThrows(AppException.class,
                    () -> bookmarkService.deleteBookmark(OTHER_EMAIL, bookmarkId));
            assertEquals(ErrorCode.BOOKMARK_NOT_FOUND, ex.getErrorCode());

            verify(bookmarkRepository, never()).delete(any());
        }

        @Test
        @DisplayName("❌ Should throw when user not found")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            AppException ex = assertThrows(AppException.class,
                    () -> bookmarkService.deleteBookmark("ghost@example.com", bookmarkId));
            assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
        }
    }

    // ============================================================
    // 4. deleteBookmarkByPaper() tests
    // ============================================================
    @Nested
    @DisplayName("deleteBookmarkByPaper")
    class DeleteBookmarkByPaper {

        @Test
        @DisplayName("✅ Should delete bookmark by paper ID")
        void shouldDeleteByPaper() {
            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));

            bookmarkService.deleteBookmarkByPaper(USER_EMAIL, paperId);

            verify(bookmarkRepository).deleteByUser_UserIdAndPaper_PaperId(userId, paperId);
        }

        @Test
        @DisplayName("❌ Should throw when user not found")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            AppException ex = assertThrows(AppException.class,
                    () -> bookmarkService.deleteBookmarkByPaper("ghost@example.com", paperId));
            assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
        }
    }

    // ============================================================
    // 5. deleteBookmarkByKeyword() tests
    // ============================================================
    @Nested
    @DisplayName("deleteBookmarkByKeyword")
    class DeleteBookmarkByKeyword {

        @Test
        @DisplayName("✅ Should delete bookmark by keyword ID")
        void shouldDeleteByKeyword() {
            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));

            bookmarkService.deleteBookmarkByKeyword(USER_EMAIL, keywordId);

            verify(bookmarkRepository).deleteByUser_UserIdAndKeyword_KeywordId(userId, keywordId);
        }

        @Test
        @DisplayName("❌ Should throw when user not found")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            AppException ex = assertThrows(AppException.class,
                    () -> bookmarkService.deleteBookmarkByKeyword("ghost@example.com", keywordId));
            assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
        }
    }
}
