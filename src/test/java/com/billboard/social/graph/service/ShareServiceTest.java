package com.billboard.social.graph.service;

import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.graph.dto.request.SocialRequests.ShareRequest;
import com.billboard.social.graph.dto.response.SocialResponses.ShareResponse;
import com.billboard.social.graph.entity.Share;
import com.billboard.social.graph.entity.enums.ContentType;
import com.billboard.social.graph.event.SocialEventPublisher;
import com.billboard.social.graph.repository.BlockRepository;
import com.billboard.social.graph.repository.ShareRepository;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShareServiceTest {

    @Mock
    private ShareRepository shareRepository;

    @Mock
    private BlockRepository blockRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private SocialEventPublisher eventPublisher;

    @InjectMocks
    private ShareService shareService;

    // Test constants
    private static final Long USER_ID = 1L;
    private static final UUID CONTENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Long CONTENT_OWNER_ID = 3L;
    private static final Long TARGET_USER_ID = 4L;
    private static final UUID SHARE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private Share testShare;
    private UserSummary testUserSummary;

    @BeforeEach
    void setUp() {
        testShare = Share.builder()
                .id(SHARE_ID)
                .userId(USER_ID)
                .contentType(ContentType.POST)
                .contentId(CONTENT_ID)
                .contentOwnerId(CONTENT_OWNER_ID)
                .targetUserId(null)
                .message("Check this out!")
                .shareToFeed(true)
                .shareToStory(false)
                .isPrivateShare(false)
                .build();
        testShare.setCreatedAt(LocalDateTime.now());

        testUserSummary = UserSummary.builder()
                .id(USER_ID)
                .username("testuser")
                .email("test@gmail.com")
                .build();
    }

    // ==================== SHARE ====================

    @Nested
    @DisplayName("share")
    class ShareTests {

        @Test
        @DisplayName("Success - with all defaults (shareToFeed/shareToStory/isPrivateShare null)")
        void share_SuccessWithDefaults() {
            ShareRequest request = ShareRequest.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .contentOwnerId(CONTENT_OWNER_ID)
                    .message("Check this out!")
                    // shareToFeed, shareToStory, isPrivateShare all null - uses defaults
                    .build();

            when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> {
                Share saved = invocation.getArgument(0);
                saved.setId(SHARE_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            ShareResponse response = shareService.share(USER_ID, request);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(SHARE_ID);
            assertThat(response.getUserId()).isEqualTo(USER_ID);
            assertThat(response.getContentType()).isEqualTo(ContentType.POST);
            assertThat(response.getContentId()).isEqualTo(CONTENT_ID);
            assertThat(response.getMessage()).isEqualTo("Check this out!");
            // Verify defaults
            assertThat(response.getShareToFeed()).isTrue();      // Default: true
            assertThat(response.getShareToStory()).isFalse();    // Default: false
            assertThat(response.getIsPrivateShare()).isFalse();  // Default: false
            verify(eventPublisher).publishContentShared(any(Share.class));
        }

        @Test
        @DisplayName("Success - with explicit shareToFeed true")
        void share_SuccessExplicitShareToFeedTrue() {
            ShareRequest request = ShareRequest.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .shareToFeed(true)
                    .build();

            when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> {
                Share saved = invocation.getArgument(0);
                saved.setId(SHARE_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            ShareResponse response = shareService.share(USER_ID, request);

            assertThat(response.getShareToFeed()).isTrue();
        }

        @Test
        @DisplayName("Success - with explicit shareToFeed false")
        void share_SuccessExplicitShareToFeedFalse() {
            ShareRequest request = ShareRequest.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .shareToFeed(false)
                    .build();

            when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> {
                Share saved = invocation.getArgument(0);
                saved.setId(SHARE_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            ShareResponse response = shareService.share(USER_ID, request);

            assertThat(response.getShareToFeed()).isFalse();
        }

        @Test
        @DisplayName("Success - with explicit shareToStory true")
        void share_SuccessExplicitShareToStoryTrue() {
            ShareRequest request = ShareRequest.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .shareToStory(true)
                    .build();

            when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> {
                Share saved = invocation.getArgument(0);
                saved.setId(SHARE_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            ShareResponse response = shareService.share(USER_ID, request);

            assertThat(response.getShareToStory()).isTrue();
        }

        @Test
        @DisplayName("Success - with explicit shareToStory false")
        void share_SuccessExplicitShareToStoryFalse() {
            ShareRequest request = ShareRequest.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .shareToStory(false)
                    .build();

            when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> {
                Share saved = invocation.getArgument(0);
                saved.setId(SHARE_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            ShareResponse response = shareService.share(USER_ID, request);

            assertThat(response.getShareToStory()).isFalse();
        }

        @Test
        @DisplayName("Success - with explicit isPrivateShare true")
        void share_SuccessExplicitIsPrivateShareTrue() {
            ShareRequest request = ShareRequest.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .targetUserId(TARGET_USER_ID)
                    .isPrivateShare(true)
                    .build();

            when(blockRepository.isBlockedEitherWay(USER_ID, TARGET_USER_ID)).thenReturn(false);
            when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> {
                Share saved = invocation.getArgument(0);
                saved.setId(SHARE_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            ShareResponse response = shareService.share(USER_ID, request);

            assertThat(response.getIsPrivateShare()).isTrue();
            assertThat(response.getTargetUserId()).isEqualTo(TARGET_USER_ID);
        }

        @Test
        @DisplayName("Success - with explicit isPrivateShare false")
        void share_SuccessExplicitIsPrivateShareFalse() {
            ShareRequest request = ShareRequest.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .isPrivateShare(false)
                    .build();

            when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> {
                Share saved = invocation.getArgument(0);
                saved.setId(SHARE_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            ShareResponse response = shareService.share(USER_ID, request);

            assertThat(response.getIsPrivateShare()).isFalse();
        }

        @Test
        @DisplayName("Success - with target user (not blocked)")
        void share_SuccessWithTargetUser() {
            ShareRequest request = ShareRequest.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .targetUserId(TARGET_USER_ID)
                    .message("Hey, check this!")
                    .build();

            when(blockRepository.isBlockedEitherWay(USER_ID, TARGET_USER_ID)).thenReturn(false);
            when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> {
                Share saved = invocation.getArgument(0);
                saved.setId(SHARE_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            ShareResponse response = shareService.share(USER_ID, request);

            assertThat(response.getTargetUserId()).isEqualTo(TARGET_USER_ID);
            verify(blockRepository).isBlockedEitherWay(USER_ID, TARGET_USER_ID);
        }

        @Test
        @DisplayName("Success - without target user (targetUserId null, block check skipped)")
        void share_SuccessWithoutTargetUser() {
            ShareRequest request = ShareRequest.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .targetUserId(null)  // Explicitly null
                    .build();

            when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> {
                Share saved = invocation.getArgument(0);
                saved.setId(SHARE_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            ShareResponse response = shareService.share(USER_ID, request);

            assertThat(response.getTargetUserId()).isNull();
            // Block check should NOT be called when targetUserId is null
            verify(blockRepository, never()).isBlockedEitherWay(any(), any());
        }

        @Test
        @DisplayName("Success - without message")
        void share_SuccessWithoutMessage() {
            ShareRequest request = ShareRequest.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .build();

            when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> {
                Share saved = invocation.getArgument(0);
                saved.setId(SHARE_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            ShareResponse response = shareService.share(USER_ID, request);

            assertThat(response.getMessage()).isNull();
        }

        @Test
        @DisplayName("Success - COMMENT content type")
        void share_CommentContentType() {
            ShareRequest request = ShareRequest.builder()
                    .contentType(ContentType.COMMENT)
                    .contentId(CONTENT_ID)
                    .build();

            when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> {
                Share saved = invocation.getArgument(0);
                saved.setId(SHARE_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            ShareResponse response = shareService.share(USER_ID, request);

            assertThat(response.getContentType()).isEqualTo(ContentType.COMMENT);
        }

        @Test
        @DisplayName("Null contentId - throws ValidationException")
        void share_NullContentId() {
            ShareRequest request = ShareRequest.builder()
                    .contentType(ContentType.POST)
                    // contentId is null
                    .build();

            assertThatThrownBy(() -> shareService.share(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Content ID is required");

            verifyNoInteractions(shareRepository);
        }

        @Test
        @DisplayName("Null contentType - throws ValidationException")
        void share_NullContentType() {
            ShareRequest request = ShareRequest.builder()
                    .contentId(CONTENT_ID)
                    // contentType is null
                    .build();

            assertThatThrownBy(() -> shareService.share(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Content type is required");

            verifyNoInteractions(shareRepository);
        }

        @Test
        @DisplayName("Target user blocked - throws ValidationException")
        void share_TargetUserBlocked() {
            ShareRequest request = ShareRequest.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .targetUserId(TARGET_USER_ID)
                    .build();

            when(blockRepository.isBlockedEitherWay(USER_ID, TARGET_USER_ID)).thenReturn(true);

            assertThatThrownBy(() -> shareService.share(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Cannot share to this user");

            verify(shareRepository, never()).save(any());
            verify(eventPublisher, never()).publishContentShared(any());
        }
    }

    // ==================== GET SHARES BY CONTENT ====================

    @Nested
    @DisplayName("getSharesByContent")
    class GetSharesByContentTests {

        @Test
        @DisplayName("Success - returns paginated shares")
        void getSharesByContent_Success() {
            Page<Share> page = new PageImpl<>(
                    List.of(testShare),
                    PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")),
                    1
            );

            when(shareRepository.findByContentTypeAndContentId(eq(ContentType.POST), eq(CONTENT_ID), any(Pageable.class)))
                    .thenReturn(page);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            PageResponse<ShareResponse> response = shareService.getSharesByContent(ContentType.POST, CONTENT_ID, 0, 20);

            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getContent().get(0).getContentType()).isEqualTo(ContentType.POST);
            assertThat(response.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("Success - empty list")
        void getSharesByContent_Empty() {
            Page<Share> emptyPage = new PageImpl<>(
                    Collections.emptyList(),
                    PageRequest.of(0, 20),
                    0
            );

            when(shareRepository.findByContentTypeAndContentId(eq(ContentType.POST), eq(CONTENT_ID), any(Pageable.class)))
                    .thenReturn(emptyPage);

            PageResponse<ShareResponse> response = shareService.getSharesByContent(ContentType.POST, CONTENT_ID, 0, 20);

            assertThat(response.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Success - custom pagination")
        void getSharesByContent_CustomPagination() {
            Page<Share> page = new PageImpl<>(
                    Collections.emptyList(),
                    PageRequest.of(5, 50),
                    0
            );

            when(shareRepository.findByContentTypeAndContentId(eq(ContentType.POST), eq(CONTENT_ID), any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<ShareResponse> response = shareService.getSharesByContent(ContentType.POST, CONTENT_ID, 5, 50);

            assertThat(response.getPage()).isEqualTo(5);
            assertThat(response.getSize()).isEqualTo(50);
        }

        @Test
        @DisplayName("Success - multiple shares")
        void getSharesByContent_MultipleShares() {
            Long userId2 = 10L;
            Share share2 = Share.builder()
                    .id(UUID.randomUUID())
                    .userId(userId2)
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .shareToFeed(true)
                    .shareToStory(false)
                    .isPrivateShare(false)
                    .build();
            share2.setCreatedAt(LocalDateTime.now());

            Page<Share> page = new PageImpl<>(
                    List.of(testShare, share2),
                    PageRequest.of(0, 20),
                    2
            );

            when(shareRepository.findByContentTypeAndContentId(eq(ContentType.POST), eq(CONTENT_ID), any(Pageable.class)))
                    .thenReturn(page);
            when(userServiceClient.getUserSummary(any(Long.class))).thenReturn(testUserSummary);

            PageResponse<ShareResponse> response = shareService.getSharesByContent(ContentType.POST, CONTENT_ID, 0, 20);

            assertThat(response.getContent()).hasSize(2);
        }

        @Test
        @DisplayName("Success - COMMENT content type")
        void getSharesByContent_CommentContentType() {
            testShare.setContentType(ContentType.COMMENT);
            Page<Share> page = new PageImpl<>(List.of(testShare), PageRequest.of(0, 20), 1);

            when(shareRepository.findByContentTypeAndContentId(eq(ContentType.COMMENT), eq(CONTENT_ID), any(Pageable.class)))
                    .thenReturn(page);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            PageResponse<ShareResponse> response = shareService.getSharesByContent(ContentType.COMMENT, CONTENT_ID, 0, 20);

            assertThat(response.getContent().get(0).getContentType()).isEqualTo(ContentType.COMMENT);
        }
    }

    // ==================== GET SHARES BY USER ====================

    @Nested
    @DisplayName("getSharesByUser")
    class GetSharesByUserTests {

        @Test
        @DisplayName("Success - returns paginated user shares")
        void getSharesByUser_Success() {
            Page<Share> page = new PageImpl<>(
                    List.of(testShare),
                    PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")),
                    1
            );

            when(shareRepository.findByUserId(eq(USER_ID), any(Pageable.class))).thenReturn(page);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            PageResponse<ShareResponse> response = shareService.getSharesByUser(USER_ID, 0, 20);

            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getContent().get(0).getUserId()).isEqualTo(USER_ID);
            assertThat(response.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("Success - empty list")
        void getSharesByUser_Empty() {
            Page<Share> emptyPage = new PageImpl<>(
                    Collections.emptyList(),
                    PageRequest.of(0, 20),
                    0
            );

            when(shareRepository.findByUserId(eq(USER_ID), any(Pageable.class))).thenReturn(emptyPage);

            PageResponse<ShareResponse> response = shareService.getSharesByUser(USER_ID, 0, 20);

            assertThat(response.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Success - custom pagination")
        void getSharesByUser_CustomPagination() {
            Page<Share> page = new PageImpl<>(
                    Collections.emptyList(),
                    PageRequest.of(3, 25),
                    0
            );

            when(shareRepository.findByUserId(eq(USER_ID), any(Pageable.class))).thenReturn(page);

            PageResponse<ShareResponse> response = shareService.getSharesByUser(USER_ID, 3, 25);

            assertThat(response.getPage()).isEqualTo(3);
            assertThat(response.getSize()).isEqualTo(25);
        }

        @Test
        @DisplayName("Success - multiple shares by user")
        void getSharesByUser_MultipleShares() {
            Share share2 = Share.builder()
                    .id(UUID.randomUUID())
                    .userId(USER_ID)
                    .contentType(ContentType.COMMENT)
                    .contentId(UUID.randomUUID())
                    .shareToFeed(true)
                    .shareToStory(false)
                    .isPrivateShare(false)
                    .build();
            share2.setCreatedAt(LocalDateTime.now());

            Page<Share> page = new PageImpl<>(
                    List.of(testShare, share2),
                    PageRequest.of(0, 20),
                    2
            );

            when(shareRepository.findByUserId(eq(USER_ID), any(Pageable.class))).thenReturn(page);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            PageResponse<ShareResponse> response = shareService.getSharesByUser(USER_ID, 0, 20);

            assertThat(response.getContent()).hasSize(2);
            assertThat(response.getContent()).allMatch(s -> s.getUserId().equals(USER_ID));
        }
    }

    // ==================== GET SHARE COUNT ====================

    @Nested
    @DisplayName("getShareCount")
    class GetShareCountTests {

        @Test
        @DisplayName("Success - returns count")
        void getShareCount_Success() {
            when(shareRepository.countByContentTypeAndContentId(ContentType.POST, CONTENT_ID)).thenReturn(25L);

            long result = shareService.getShareCount(ContentType.POST, CONTENT_ID);

            assertThat(result).isEqualTo(25L);
        }

        @Test
        @DisplayName("Success - returns zero")
        void getShareCount_Zero() {
            when(shareRepository.countByContentTypeAndContentId(ContentType.POST, CONTENT_ID)).thenReturn(0L);

            long result = shareService.getShareCount(ContentType.POST, CONTENT_ID);

            assertThat(result).isEqualTo(0L);
        }

        @Test
        @DisplayName("Success - large count")
        void getShareCount_LargeCount() {
            when(shareRepository.countByContentTypeAndContentId(ContentType.POST, CONTENT_ID)).thenReturn(10000L);

            long result = shareService.getShareCount(ContentType.POST, CONTENT_ID);

            assertThat(result).isEqualTo(10000L);
        }

        @Test
        @DisplayName("Success - COMMENT content type")
        void getShareCount_CommentContentType() {
            when(shareRepository.countByContentTypeAndContentId(ContentType.COMMENT, CONTENT_ID)).thenReturn(5L);

            long result = shareService.getShareCount(ContentType.COMMENT, CONTENT_ID);

            assertThat(result).isEqualTo(5L);
        }
    }

    // ==================== FETCH USER SUMMARY WITH FALLBACK ====================

    @Nested
    @DisplayName("fetchUserSummaryWithFallback (via getSharesByContent)")
    class FetchUserSummaryWithFallbackTests {

        private void setupSharesPage() {
            Page<Share> page = new PageImpl<>(List.of(testShare), PageRequest.of(0, 20), 1);
            when(shareRepository.findByContentTypeAndContentId(eq(ContentType.POST), eq(CONTENT_ID), any(Pageable.class)))
                    .thenReturn(page);
        }

        @Test
        @DisplayName("Success - returns user summary")
        void fetchUserSummaryWithFallback_Success() {
            setupSharesPage();
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            PageResponse<ShareResponse> response = shareService.getSharesByContent(ContentType.POST, CONTENT_ID, 0, 20);

            assertThat(response.getContent().get(0).getUser()).isNotNull();
            assertThat(response.getContent().get(0).getUser().getUsername()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("Returns null - uses fallback")
        void fetchUserSummaryWithFallback_ReturnsNull() {
            setupSharesPage();
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(null);

            PageResponse<ShareResponse> response = shareService.getSharesByContent(ContentType.POST, CONTENT_ID, 0, 20);

            assertThat(response.getContent().get(0).getUser()).isNotNull();
            assertThat(response.getContent().get(0).getUser().getId()).isEqualTo(USER_ID);
            assertThat(response.getContent().get(0).getUser().getUsername()).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("FeignException.NotFound - uses fallback")
        void fetchUserSummaryWithFallback_FeignNotFound() {
            setupSharesPage();
            Request feignRequest = Request.create(Request.HttpMethod.GET, "/users",
                    Collections.emptyMap(), null, new RequestTemplate());
            when(userServiceClient.getUserSummary(USER_ID))
                    .thenThrow(new FeignException.NotFound("Not found", feignRequest, null, null));

            PageResponse<ShareResponse> response = shareService.getSharesByContent(ContentType.POST, CONTENT_ID, 0, 20);

            assertThat(response.getContent().get(0).getUser().getUsername()).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("FeignException (other) - uses fallback")
        void fetchUserSummaryWithFallback_FeignOther() {
            setupSharesPage();
            Request feignRequest = Request.create(Request.HttpMethod.GET, "/users",
                    Collections.emptyMap(), null, new RequestTemplate());
            when(userServiceClient.getUserSummary(USER_ID))
                    .thenThrow(new FeignException.ServiceUnavailable("Service unavailable", feignRequest, null, null));

            PageResponse<ShareResponse> response = shareService.getSharesByContent(ContentType.POST, CONTENT_ID, 0, 20);

            assertThat(response.getContent().get(0).getUser().getUsername()).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("Generic Exception - uses fallback")
        void fetchUserSummaryWithFallback_GenericException() {
            setupSharesPage();
            when(userServiceClient.getUserSummary(USER_ID))
                    .thenThrow(new RuntimeException("Connection failed"));

            PageResponse<ShareResponse> response = shareService.getSharesByContent(ContentType.POST, CONTENT_ID, 0, 20);

            assertThat(response.getContent().get(0).getUser().getUsername()).isEqualTo("Unknown");
        }
    }

    // ==================== MAP TO SHARE RESPONSE ====================

    @Nested
    @DisplayName("mapToShareResponse")
    class MapToShareResponseTests {

        @Test
        @DisplayName("Maps all fields correctly")
        void mapToShareResponse_AllFields() {
            testShare.setTargetUserId(TARGET_USER_ID);
            testShare.setMessage("Test message");
            testShare.setShareToFeed(true);
            testShare.setShareToStory(true);
            testShare.setIsPrivateShare(true);

            Page<Share> page = new PageImpl<>(List.of(testShare), PageRequest.of(0, 20), 1);
            when(shareRepository.findByContentTypeAndContentId(eq(ContentType.POST), eq(CONTENT_ID), any(Pageable.class)))
                    .thenReturn(page);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            PageResponse<ShareResponse> response = shareService.getSharesByContent(ContentType.POST, CONTENT_ID, 0, 20);

            ShareResponse shareResponse = response.getContent().get(0);
            assertThat(shareResponse.getId()).isEqualTo(SHARE_ID);
            assertThat(shareResponse.getUserId()).isEqualTo(USER_ID);
            assertThat(shareResponse.getContentType()).isEqualTo(ContentType.POST);
            assertThat(shareResponse.getContentId()).isEqualTo(CONTENT_ID);
            assertThat(shareResponse.getTargetUserId()).isEqualTo(TARGET_USER_ID);
            assertThat(shareResponse.getMessage()).isEqualTo("Test message");
            assertThat(shareResponse.getShareToFeed()).isTrue();
            assertThat(shareResponse.getShareToStory()).isTrue();
            assertThat(shareResponse.getIsPrivateShare()).isTrue();
            assertThat(shareResponse.getCreatedAt()).isNotNull();
            assertThat(shareResponse.getUser()).isNotNull();
        }

        @Test
        @DisplayName("Maps with null optional fields")
        void mapToShareResponse_NullOptionalFields() {
            testShare.setTargetUserId(null);
            testShare.setMessage(null);

            Page<Share> page = new PageImpl<>(List.of(testShare), PageRequest.of(0, 20), 1);
            when(shareRepository.findByContentTypeAndContentId(eq(ContentType.POST), eq(CONTENT_ID), any(Pageable.class)))
                    .thenReturn(page);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            PageResponse<ShareResponse> response = shareService.getSharesByContent(ContentType.POST, CONTENT_ID, 0, 20);

            ShareResponse shareResponse = response.getContent().get(0);
            assertThat(shareResponse.getTargetUserId()).isNull();
            assertThat(shareResponse.getMessage()).isNull();
        }
    }
}