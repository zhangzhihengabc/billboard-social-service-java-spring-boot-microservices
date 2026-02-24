package com.billboard.social.graph.service;

import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.graph.dto.request.SocialRequests.ReactionRequest;
import com.billboard.social.graph.dto.response.SocialResponses.ReactionResponse;
import com.billboard.social.graph.dto.response.SocialResponses.ReactionStatsResponse;
import com.billboard.social.graph.entity.Reaction;
import com.billboard.social.graph.entity.enums.ContentType;
import com.billboard.social.graph.entity.enums.ReactionType;
import com.billboard.social.graph.event.SocialEventPublisher;
import com.billboard.social.graph.repository.ReactionRepository;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReactionServiceTest {

    @Mock
    private ReactionRepository reactionRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private SocialEventPublisher eventPublisher;

    @InjectMocks
    private ReactionService reactionService;

    // Test constants
    private static final Long USER_ID = 1L;
    private static final UUID CONTENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Long CONTENT_OWNER_ID = 3L;
    private static final UUID REACTION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private Reaction testReaction;
    private UserSummary testUserSummary;

    @BeforeEach
    void setUp() {
        testReaction = Reaction.builder()
                .id(REACTION_ID)
                .userId(USER_ID)
                .contentType(ContentType.POST)
                .contentId(CONTENT_ID)
                .contentOwnerId(CONTENT_OWNER_ID)
                .reactionType(ReactionType.LIKE)
                .build();
        testReaction.setCreatedAt(LocalDateTime.now());

        testUserSummary = UserSummary.builder()
                .id(USER_ID)
                .username("testuser")
                .email("test@gmail.com")
                .build();
    }

    // ==================== REACT ====================

    @Nested
    @DisplayName("react")
    class ReactTests {

        @Test
        @DisplayName("Success - creates new reaction")
        void react_Success_CreatesNewReaction() {
            ReactionRequest request = ReactionRequest.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .contentOwnerId(CONTENT_OWNER_ID)
                    .reactionType(ReactionType.LIKE)
                    .build();

            when(reactionRepository.findByUserIdAndContentTypeAndContentId(USER_ID, ContentType.POST, CONTENT_ID))
                    .thenReturn(Optional.empty());
            when(reactionRepository.save(any(Reaction.class))).thenAnswer(invocation -> {
                Reaction saved = invocation.getArgument(0);
                saved.setId(REACTION_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            ReactionResponse response = reactionService.react(USER_ID, request);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(REACTION_ID);
            assertThat(response.getUserId()).isEqualTo(USER_ID);
            assertThat(response.getContentType()).isEqualTo(ContentType.POST);
            assertThat(response.getContentId()).isEqualTo(CONTENT_ID);
            assertThat(response.getReactionType()).isEqualTo(ReactionType.LIKE);
            verify(eventPublisher).publishReactionAdded(any(Reaction.class));
        }

        @Test
        @DisplayName("Success - updates existing reaction (changes type)")
        void react_Success_UpdatesExistingReaction() {
            ReactionRequest request = ReactionRequest.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .reactionType(ReactionType.LOVE)  // Changing from LIKE to LOVE
                    .build();

            Reaction existingReaction = Reaction.builder()
                    .id(REACTION_ID)
                    .userId(USER_ID)
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .reactionType(ReactionType.LIKE)
                    .build();
            existingReaction.setCreatedAt(LocalDateTime.now());

            when(reactionRepository.findByUserIdAndContentTypeAndContentId(USER_ID, ContentType.POST, CONTENT_ID))
                    .thenReturn(Optional.of(existingReaction));
            when(reactionRepository.save(any(Reaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            ReactionResponse response = reactionService.react(USER_ID, request);

            assertThat(response).isNotNull();
            assertThat(response.getReactionType()).isEqualTo(ReactionType.LOVE);
            verify(eventPublisher, never()).publishReactionAdded(any());  // No event for update
        }

        @Test
        @DisplayName("Null contentId - throws ValidationException")
        void react_NullContentId() {
            ReactionRequest request = ReactionRequest.builder()
                    .contentType(ContentType.POST)
                    .reactionType(ReactionType.LIKE)
                    .build();

            assertThatThrownBy(() -> reactionService.react(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Content ID is required");

            verifyNoInteractions(reactionRepository);
        }

        @Test
        @DisplayName("Null contentType - throws ValidationException")
        void react_NullContentType() {
            ReactionRequest request = ReactionRequest.builder()
                    .contentId(CONTENT_ID)
                    .reactionType(ReactionType.LIKE)
                    .build();

            assertThatThrownBy(() -> reactionService.react(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Content type is required");

            verifyNoInteractions(reactionRepository);
        }

        @Test
        @DisplayName("Null reactionType - throws ValidationException")
        void react_NullReactionType() {
            ReactionRequest request = ReactionRequest.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .build();

            assertThatThrownBy(() -> reactionService.react(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Reaction type is required");

            verifyNoInteractions(reactionRepository);
        }

        @Test
        @DisplayName("Race condition - DataIntegrityViolationException")
        void react_RaceCondition() {
            ReactionRequest request = ReactionRequest.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .reactionType(ReactionType.LIKE)
                    .build();

            when(reactionRepository.findByUserIdAndContentTypeAndContentId(USER_ID, ContentType.POST, CONTENT_ID))
                    .thenReturn(Optional.empty());
            when(reactionRepository.save(any(Reaction.class)))
                    .thenThrow(new DataIntegrityViolationException("Duplicate key"));

            assertThatThrownBy(() -> reactionService.react(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Reaction already exists");

            verify(eventPublisher, never()).publishReactionAdded(any());
        }

        @Test
        @DisplayName("Success - COMMENT content type")
        void react_CommentContentType() {
            ReactionRequest request = ReactionRequest.builder()
                    .contentType(ContentType.COMMENT)
                    .contentId(CONTENT_ID)
                    .reactionType(ReactionType.LIKE)
                    .build();

            testReaction.setContentType(ContentType.COMMENT);

            when(reactionRepository.findByUserIdAndContentTypeAndContentId(USER_ID, ContentType.COMMENT, CONTENT_ID))
                    .thenReturn(Optional.empty());
            when(reactionRepository.save(any(Reaction.class))).thenAnswer(invocation -> {
                Reaction saved = invocation.getArgument(0);
                saved.setId(REACTION_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            ReactionResponse response = reactionService.react(USER_ID, request);

            assertThat(response.getContentType()).isEqualTo(ContentType.COMMENT);
        }

        @Test
        @DisplayName("Success - different reaction types")
        void react_DifferentReactionTypes() {
            ReactionRequest request = ReactionRequest.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .reactionType(ReactionType.HAHA)
                    .build();

            when(reactionRepository.findByUserIdAndContentTypeAndContentId(USER_ID, ContentType.POST, CONTENT_ID))
                    .thenReturn(Optional.empty());
            when(reactionRepository.save(any(Reaction.class))).thenAnswer(invocation -> {
                Reaction saved = invocation.getArgument(0);
                saved.setId(REACTION_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            ReactionResponse response = reactionService.react(USER_ID, request);

            assertThat(response.getReactionType()).isEqualTo(ReactionType.HAHA);
        }
    }

    // ==================== REMOVE REACTION ====================

    @Nested
    @DisplayName("removeReaction")
    class RemoveReactionTests {

        @Test
        @DisplayName("Success - removes reaction")
        void removeReaction_Success() {
            when(reactionRepository.findByUserIdAndContentTypeAndContentId(USER_ID, ContentType.POST, CONTENT_ID))
                    .thenReturn(Optional.of(testReaction));
            doNothing().when(reactionRepository).delete(testReaction);

            reactionService.removeReaction(USER_ID, ContentType.POST, CONTENT_ID);

            verify(reactionRepository).delete(testReaction);
            verify(eventPublisher).publishReactionRemoved(testReaction);
        }

        @Test
        @DisplayName("Reaction not found - throws ValidationException")
        void removeReaction_NotFound() {
            when(reactionRepository.findByUserIdAndContentTypeAndContentId(USER_ID, ContentType.POST, CONTENT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> reactionService.removeReaction(USER_ID, ContentType.POST, CONTENT_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Reaction not found");

            verify(reactionRepository, never()).delete(any());
            verify(eventPublisher, never()).publishReactionRemoved(any());
        }

        @Test
        @DisplayName("Success - COMMENT content type")
        void removeReaction_CommentContentType() {
            testReaction.setContentType(ContentType.COMMENT);

            when(reactionRepository.findByUserIdAndContentTypeAndContentId(USER_ID, ContentType.COMMENT, CONTENT_ID))
                    .thenReturn(Optional.of(testReaction));
            doNothing().when(reactionRepository).delete(testReaction);

            reactionService.removeReaction(USER_ID, ContentType.COMMENT, CONTENT_ID);

            verify(reactionRepository).delete(testReaction);
        }
    }

    // ==================== GET REACTIONS ====================

    @Nested
    @DisplayName("getReactions")
    class GetReactionsTests {

        @Test
        @DisplayName("Success - returns paginated reactions")
        void getReactions_Success() {
            Page<Reaction> page = new PageImpl<>(
                    List.of(testReaction),
                    PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")),
                    1
            );

            when(reactionRepository.findByContentTypeAndContentId(eq(ContentType.POST), eq(CONTENT_ID), any(Pageable.class)))
                    .thenReturn(page);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            PageResponse<ReactionResponse> response = reactionService.getReactions(ContentType.POST, CONTENT_ID, 0, 20);

            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getContent().get(0).getReactionType()).isEqualTo(ReactionType.LIKE);
            assertThat(response.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("Success - empty list")
        void getReactions_Empty() {
            Page<Reaction> emptyPage = new PageImpl<>(
                    Collections.emptyList(),
                    PageRequest.of(0, 20),
                    0
            );

            when(reactionRepository.findByContentTypeAndContentId(eq(ContentType.POST), eq(CONTENT_ID), any(Pageable.class)))
                    .thenReturn(emptyPage);

            PageResponse<ReactionResponse> response = reactionService.getReactions(ContentType.POST, CONTENT_ID, 0, 20);

            assertThat(response.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Success - custom pagination")
        void getReactions_CustomPagination() {
            Page<Reaction> page = new PageImpl<>(
                    Collections.emptyList(),
                    PageRequest.of(5, 50),
                    0
            );

            when(reactionRepository.findByContentTypeAndContentId(eq(ContentType.POST), eq(CONTENT_ID), any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<ReactionResponse> response = reactionService.getReactions(ContentType.POST, CONTENT_ID, 5, 50);

            assertThat(response.getPage()).isEqualTo(5);
            assertThat(response.getSize()).isEqualTo(50);
        }

        @Test
        @DisplayName("Success - multiple reactions")
        void getReactions_MultipleReactions() {
            Long userId2 = 10L;
            Reaction reaction2 = Reaction.builder()
                    .id(UUID.randomUUID())
                    .userId(userId2)
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .reactionType(ReactionType.LOVE)
                    .build();
            reaction2.setCreatedAt(LocalDateTime.now());

            Page<Reaction> page = new PageImpl<>(
                    List.of(testReaction, reaction2),
                    PageRequest.of(0, 20),
                    2
            );

            when(reactionRepository.findByContentTypeAndContentId(eq(ContentType.POST), eq(CONTENT_ID), any(Pageable.class)))
                    .thenReturn(page);
            when(userServiceClient.getUserSummary(any(Long.class))).thenReturn(testUserSummary);

            PageResponse<ReactionResponse> response = reactionService.getReactions(ContentType.POST, CONTENT_ID, 0, 20);

            assertThat(response.getContent()).hasSize(2);
        }
    }

    // ==================== GET REACTIONS BY TYPE ====================

    @Nested
    @DisplayName("getReactionsByType")
    class GetReactionsByTypeTests {

        @Test
        @DisplayName("Success - returns reactions filtered by LIKE type")
        void getReactionsByType_SuccessLike() {
            Page<Reaction> page = new PageImpl<>(
                    List.of(testReaction),
                    PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")),
                    1
            );

            when(reactionRepository.findByContentAndReactionType(
                    eq(ContentType.POST), eq(CONTENT_ID), eq(ReactionType.LIKE), any(Pageable.class)))
                    .thenReturn(page);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            PageResponse<ReactionResponse> response = reactionService.getReactionsByType(
                    ContentType.POST, CONTENT_ID, ReactionType.LIKE, 0, 20);

            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getContent().get(0).getReactionType()).isEqualTo(ReactionType.LIKE);
        }

        @Test
        @DisplayName("Success - returns reactions filtered by LOVE type")
        void getReactionsByType_SuccessLove() {
            testReaction.setReactionType(ReactionType.LOVE);
            Page<Reaction> page = new PageImpl<>(
                    List.of(testReaction),
                    PageRequest.of(0, 20),
                    1
            );

            when(reactionRepository.findByContentAndReactionType(
                    eq(ContentType.POST), eq(CONTENT_ID), eq(ReactionType.LOVE), any(Pageable.class)))
                    .thenReturn(page);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            PageResponse<ReactionResponse> response = reactionService.getReactionsByType(
                    ContentType.POST, CONTENT_ID, ReactionType.LOVE, 0, 20);

            assertThat(response.getContent().get(0).getReactionType()).isEqualTo(ReactionType.LOVE);
        }

        @Test
        @DisplayName("Success - empty list")
        void getReactionsByType_Empty() {
            Page<Reaction> emptyPage = new PageImpl<>(
                    Collections.emptyList(),
                    PageRequest.of(0, 20),
                    0
            );

            when(reactionRepository.findByContentAndReactionType(
                    eq(ContentType.POST), eq(CONTENT_ID), eq(ReactionType.LIKE), any(Pageable.class)))
                    .thenReturn(emptyPage);

            PageResponse<ReactionResponse> response = reactionService.getReactionsByType(
                    ContentType.POST, CONTENT_ID, ReactionType.LIKE, 0, 20);

            assertThat(response.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Success - custom pagination")
        void getReactionsByType_CustomPagination() {
            Page<Reaction> page = new PageImpl<>(
                    Collections.emptyList(),
                    PageRequest.of(3, 25),
                    0
            );

            when(reactionRepository.findByContentAndReactionType(
                    eq(ContentType.POST), eq(CONTENT_ID), eq(ReactionType.LIKE), any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<ReactionResponse> response = reactionService.getReactionsByType(
                    ContentType.POST, CONTENT_ID, ReactionType.LIKE, 3, 25);

            assertThat(response.getPage()).isEqualTo(3);
            assertThat(response.getSize()).isEqualTo(25);
        }
    }

    // ==================== GET REACTION STATS ====================

    @Nested
    @DisplayName("getReactionStats")
    class GetReactionStatsTests {

        @Test
        @DisplayName("Success - with user reaction")
        void getReactionStats_WithUserReaction() {
            when(reactionRepository.countByContentTypeAndContentId(ContentType.POST, CONTENT_ID)).thenReturn(15L);

            List<Object[]> countByType = new ArrayList<>();
            countByType.add(new Object[]{ReactionType.LIKE, 10L});
            countByType.add(new Object[]{ReactionType.LOVE, 5L});
            when(reactionRepository.countByContentGroupedByReactionType(ContentType.POST, CONTENT_ID))
                    .thenReturn(countByType);

            when(reactionRepository.findByUserIdAndContentTypeAndContentId(USER_ID, ContentType.POST, CONTENT_ID))
                    .thenReturn(Optional.of(testReaction));

            ReactionStatsResponse response = reactionService.getReactionStats(USER_ID, ContentType.POST, CONTENT_ID);

            assertThat(response.getContentType()).isEqualTo(ContentType.POST);
            assertThat(response.getContentId()).isEqualTo(CONTENT_ID);
            assertThat(response.getTotalCount()).isEqualTo(15L);
            assertThat(response.getCountByType()).containsEntry(ReactionType.LIKE, 10L);
            assertThat(response.getCountByType()).containsEntry(ReactionType.LOVE, 5L);
            assertThat(response.getUserReacted()).isTrue();
            assertThat(response.getUserReactionType()).isEqualTo(ReactionType.LIKE);
        }

        @Test
        @DisplayName("Success - without user reaction (user has not reacted)")
        void getReactionStats_WithoutUserReaction() {
            when(reactionRepository.countByContentTypeAndContentId(ContentType.POST, CONTENT_ID)).thenReturn(10L);

            List<Object[]> countByType = new ArrayList<>();
            countByType.add(new Object[]{ReactionType.LIKE, 10L});
            when(reactionRepository.countByContentGroupedByReactionType(ContentType.POST, CONTENT_ID))
                    .thenReturn(countByType);

            when(reactionRepository.findByUserIdAndContentTypeAndContentId(USER_ID, ContentType.POST, CONTENT_ID))
                    .thenReturn(Optional.empty());

            ReactionStatsResponse response = reactionService.getReactionStats(USER_ID, ContentType.POST, CONTENT_ID);

            assertThat(response.getUserReacted()).isFalse();
            assertThat(response.getUserReactionType()).isNull();
        }

        @Test
        @DisplayName("Success - userId is null (anonymous user)")
        void getReactionStats_UserIdIsNull() {
            when(reactionRepository.countByContentTypeAndContentId(ContentType.POST, CONTENT_ID)).thenReturn(5L);

            List<Object[]> countByType = new ArrayList<>();
            countByType.add(new Object[]{ReactionType.LIKE, 5L});
            when(reactionRepository.countByContentGroupedByReactionType(ContentType.POST, CONTENT_ID))
                    .thenReturn(countByType);

            ReactionStatsResponse response = reactionService.getReactionStats(null, ContentType.POST, CONTENT_ID);

            assertThat(response.getTotalCount()).isEqualTo(5L);
            assertThat(response.getUserReacted()).isFalse();
            assertThat(response.getUserReactionType()).isNull();
            // Verify user reaction lookup was NOT called
            verify(reactionRepository, never()).findByUserIdAndContentTypeAndContentId(any(), any(), any());
        }

        @Test
        @DisplayName("Success - zero reactions")
        void getReactionStats_ZeroReactions() {
            when(reactionRepository.countByContentTypeAndContentId(ContentType.POST, CONTENT_ID)).thenReturn(0L);
            when(reactionRepository.countByContentGroupedByReactionType(ContentType.POST, CONTENT_ID))
                    .thenReturn(Collections.emptyList());
            when(reactionRepository.findByUserIdAndContentTypeAndContentId(USER_ID, ContentType.POST, CONTENT_ID))
                    .thenReturn(Optional.empty());

            ReactionStatsResponse response = reactionService.getReactionStats(USER_ID, ContentType.POST, CONTENT_ID);

            assertThat(response.getTotalCount()).isEqualTo(0L);
            assertThat(response.getCountByType()).isEmpty();
        }

        @Test
        @DisplayName("Success - multiple reaction types")
        void getReactionStats_MultipleReactionTypes() {
            when(reactionRepository.countByContentTypeAndContentId(ContentType.POST, CONTENT_ID)).thenReturn(100L);

            List<Object[]> countByType = new ArrayList<>();
            countByType.add(new Object[]{ReactionType.LIKE, 50L});
            countByType.add(new Object[]{ReactionType.LOVE, 30L});
            countByType.add(new Object[]{ReactionType.HAHA, 15L});
            countByType.add(new Object[]{ReactionType.WOW, 5L});
            when(reactionRepository.countByContentGroupedByReactionType(ContentType.POST, CONTENT_ID))
                    .thenReturn(countByType);

            when(reactionRepository.findByUserIdAndContentTypeAndContentId(USER_ID, ContentType.POST, CONTENT_ID))
                    .thenReturn(Optional.empty());

            ReactionStatsResponse response = reactionService.getReactionStats(USER_ID, ContentType.POST, CONTENT_ID);

            assertThat(response.getCountByType()).hasSize(4);
            assertThat(response.getCountByType().get(ReactionType.LIKE)).isEqualTo(50L);
            assertThat(response.getCountByType().get(ReactionType.LOVE)).isEqualTo(30L);
            assertThat(response.getCountByType().get(ReactionType.HAHA)).isEqualTo(15L);
            assertThat(response.getCountByType().get(ReactionType.WOW)).isEqualTo(5L);
        }

        @Test
        @DisplayName("Success - COMMENT content type")
        void getReactionStats_CommentContentType() {
            when(reactionRepository.countByContentTypeAndContentId(ContentType.COMMENT, CONTENT_ID)).thenReturn(3L);

            List<Object[]> countByType = new ArrayList<>();
            countByType.add(new Object[]{ReactionType.LIKE, 3L});
            when(reactionRepository.countByContentGroupedByReactionType(ContentType.COMMENT, CONTENT_ID))
                    .thenReturn(countByType);

            when(reactionRepository.findByUserIdAndContentTypeAndContentId(USER_ID, ContentType.COMMENT, CONTENT_ID))
                    .thenReturn(Optional.empty());

            ReactionStatsResponse response = reactionService.getReactionStats(USER_ID, ContentType.COMMENT, CONTENT_ID);

            assertThat(response.getContentType()).isEqualTo(ContentType.COMMENT);
        }
    }

    // ==================== HAS USER REACTED ====================

    @Nested
    @DisplayName("hasUserReacted")
    class HasUserReactedTests {

        @Test
        @DisplayName("Returns true when user has reacted")
        void hasUserReacted_ReturnsTrue() {
            when(reactionRepository.existsByUserIdAndContentTypeAndContentId(USER_ID, ContentType.POST, CONTENT_ID))
                    .thenReturn(true);

            boolean result = reactionService.hasUserReacted(USER_ID, ContentType.POST, CONTENT_ID);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Returns false when user has not reacted")
        void hasUserReacted_ReturnsFalse() {
            when(reactionRepository.existsByUserIdAndContentTypeAndContentId(USER_ID, ContentType.POST, CONTENT_ID))
                    .thenReturn(false);

            boolean result = reactionService.hasUserReacted(USER_ID, ContentType.POST, CONTENT_ID);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Success - COMMENT content type")
        void hasUserReacted_CommentContentType() {
            when(reactionRepository.existsByUserIdAndContentTypeAndContentId(USER_ID, ContentType.COMMENT, CONTENT_ID))
                    .thenReturn(true);

            boolean result = reactionService.hasUserReacted(USER_ID, ContentType.COMMENT, CONTENT_ID);

            assertThat(result).isTrue();
        }
    }

    // ==================== FETCH USER SUMMARY WITH FALLBACK ====================

    @Nested
    @DisplayName("fetchUserSummaryWithFallback (via getReactions)")
    class FetchUserSummaryWithFallbackTests {

        private void setupReactionsPage() {
            Page<Reaction> page = new PageImpl<>(List.of(testReaction), PageRequest.of(0, 20), 1);
            when(reactionRepository.findByContentTypeAndContentId(eq(ContentType.POST), eq(CONTENT_ID), any(Pageable.class)))
                    .thenReturn(page);
        }

        @Test
        @DisplayName("Success - returns user summary")
        void fetchUserSummaryWithFallback_Success() {
            setupReactionsPage();
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            PageResponse<ReactionResponse> response = reactionService.getReactions(ContentType.POST, CONTENT_ID, 0, 20);

            assertThat(response.getContent().get(0).getUser()).isNotNull();
            assertThat(response.getContent().get(0).getUser().getUsername()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("Returns null - uses fallback")
        void fetchUserSummaryWithFallback_ReturnsNull() {
            setupReactionsPage();
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(null);

            PageResponse<ReactionResponse> response = reactionService.getReactions(ContentType.POST, CONTENT_ID, 0, 20);

            assertThat(response.getContent().get(0).getUser()).isNotNull();
            assertThat(response.getContent().get(0).getUser().getId()).isEqualTo(USER_ID);
            assertThat(response.getContent().get(0).getUser().getUsername()).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("FeignException.NotFound - uses fallback")
        void fetchUserSummaryWithFallback_FeignNotFound() {
            setupReactionsPage();
            Request feignRequest = Request.create(Request.HttpMethod.GET, "/users",
                    Collections.emptyMap(), null, new RequestTemplate());
            when(userServiceClient.getUserSummary(USER_ID))
                    .thenThrow(new FeignException.NotFound("Not found", feignRequest, null, null));

            PageResponse<ReactionResponse> response = reactionService.getReactions(ContentType.POST, CONTENT_ID, 0, 20);

            assertThat(response.getContent().get(0).getUser().getUsername()).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("FeignException (other) - uses fallback")
        void fetchUserSummaryWithFallback_FeignOther() {
            setupReactionsPage();
            Request feignRequest = Request.create(Request.HttpMethod.GET, "/users",
                    Collections.emptyMap(), null, new RequestTemplate());
            when(userServiceClient.getUserSummary(USER_ID))
                    .thenThrow(new FeignException.ServiceUnavailable("Service unavailable", feignRequest, null, null));

            PageResponse<ReactionResponse> response = reactionService.getReactions(ContentType.POST, CONTENT_ID, 0, 20);

            assertThat(response.getContent().get(0).getUser().getUsername()).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("Generic Exception - uses fallback")
        void fetchUserSummaryWithFallback_GenericException() {
            setupReactionsPage();
            when(userServiceClient.getUserSummary(USER_ID))
                    .thenThrow(new RuntimeException("Connection failed"));

            PageResponse<ReactionResponse> response = reactionService.getReactions(ContentType.POST, CONTENT_ID, 0, 20);

            assertThat(response.getContent().get(0).getUser().getUsername()).isEqualTo("Unknown");
        }
    }

    // ==================== MAP TO REACTION RESPONSE ====================

    @Nested
    @DisplayName("mapToReactionResponse")
    class MapToReactionResponseTests {

        @Test
        @DisplayName("Maps all fields correctly")
        void mapToReactionResponse_AllFields() {
            Page<Reaction> page = new PageImpl<>(List.of(testReaction), PageRequest.of(0, 20), 1);
            when(reactionRepository.findByContentTypeAndContentId(eq(ContentType.POST), eq(CONTENT_ID), any(Pageable.class)))
                    .thenReturn(page);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            PageResponse<ReactionResponse> response = reactionService.getReactions(ContentType.POST, CONTENT_ID, 0, 20);

            ReactionResponse reactionResponse = response.getContent().get(0);
            assertThat(reactionResponse.getId()).isEqualTo(REACTION_ID);
            assertThat(reactionResponse.getUserId()).isEqualTo(USER_ID);
            assertThat(reactionResponse.getContentType()).isEqualTo(ContentType.POST);
            assertThat(reactionResponse.getContentId()).isEqualTo(CONTENT_ID);
            assertThat(reactionResponse.getReactionType()).isEqualTo(ReactionType.LIKE);
            assertThat(reactionResponse.getCreatedAt()).isNotNull();
            assertThat(reactionResponse.getUser()).isNotNull();
        }
    }
}