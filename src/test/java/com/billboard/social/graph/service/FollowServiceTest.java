package com.billboard.social.graph.service;

import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.exception.ResourceNotFoundException;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.graph.dto.request.SocialRequests.FollowRequest;
import com.billboard.social.graph.dto.request.SocialRequests.UpdateFollowRequest;
import com.billboard.social.graph.dto.response.SocialResponses.FollowResponse;
import com.billboard.social.graph.dto.response.SocialResponses.FollowStatsResponse;
import com.billboard.social.graph.entity.Follow;
import com.billboard.social.graph.event.SocialEventPublisher;
import com.billboard.social.graph.repository.BlockRepository;
import com.billboard.social.graph.repository.FollowRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock
    private FollowRepository followRepository;

    @Mock
    private BlockRepository blockRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private SocialEventPublisher eventPublisher;

    @InjectMocks
    private FollowService followService;

    // Test constants
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TARGET_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID FOLLOW_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private Follow testFollow;
    private UserSummary testUserSummary;

    @BeforeEach
    void setUp() {
        // Set maxFollowing value via reflection
        ReflectionTestUtils.setField(followService, "maxFollowing", 5000);

        testFollow = Follow.builder()
                .id(FOLLOW_ID)
                .followerId(USER_ID)
                .followingId(TARGET_USER_ID)
                .notificationsEnabled(true)
                .isCloseFriend(false)
                .isMuted(false)
                .build();
        testFollow.setCreatedAt(LocalDateTime.now());

        testUserSummary = UserSummary.builder()
                .id(TARGET_USER_ID)
                .username("targetuser")
                .displayName("Target User")
                .avatarUrl("https://example.com/avatar.jpg")
                .isVerified(false)
                .build();
    }

    // ==================== FOLLOW ====================

    @Nested
    @DisplayName("follow")
    class FollowTests {

        @Test
        @DisplayName("Success - follow with default values")
        void follow_SuccessWithDefaults() {
            FollowRequest request = FollowRequest.builder()
                    .userId(TARGET_USER_ID)
                    .build();

            when(blockRepository.isBlockedEitherWay(USER_ID, TARGET_USER_ID)).thenReturn(false);
            when(followRepository.existsByFollowerIdAndFollowingId(USER_ID, TARGET_USER_ID)).thenReturn(false);
            when(followRepository.countByFollowerId(USER_ID)).thenReturn(100L);
            when(followRepository.save(any(Follow.class))).thenAnswer(invocation -> {
                Follow saved = invocation.getArgument(0);
                // Verify defaults
                assertThat(saved.getNotificationsEnabled()).isTrue();
                assertThat(saved.getIsCloseFriend()).isFalse();
                saved.setId(FOLLOW_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(TARGET_USER_ID)).thenReturn(testUserSummary);

            FollowResponse response = followService.follow(USER_ID, request);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(FOLLOW_ID);
            assertThat(response.getFollowerId()).isEqualTo(USER_ID);
            assertThat(response.getFollowingId()).isEqualTo(TARGET_USER_ID);
            assertThat(response.getNotificationsEnabled()).isTrue();
            assertThat(response.getIsCloseFriend()).isFalse();
            verify(eventPublisher).publishFollowed(any(Follow.class));
        }

        @Test
        @DisplayName("Success - follow with explicit values")
        void follow_SuccessWithExplicitValues() {
            FollowRequest request = FollowRequest.builder()
                    .userId(TARGET_USER_ID)
                    .notificationsEnabled(false)
                    .isCloseFriend(true)
                    .build();

            when(blockRepository.isBlockedEitherWay(USER_ID, TARGET_USER_ID)).thenReturn(false);
            when(followRepository.existsByFollowerIdAndFollowingId(USER_ID, TARGET_USER_ID)).thenReturn(false);
            when(followRepository.countByFollowerId(USER_ID)).thenReturn(100L);
            when(followRepository.save(any(Follow.class))).thenAnswer(invocation -> {
                Follow saved = invocation.getArgument(0);
                assertThat(saved.getNotificationsEnabled()).isFalse();
                assertThat(saved.getIsCloseFriend()).isTrue();
                saved.setId(FOLLOW_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(TARGET_USER_ID)).thenReturn(testUserSummary);

            FollowResponse response = followService.follow(USER_ID, request);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Success - notificationsEnabled explicit true")
        void follow_NotificationsEnabledExplicitTrue() {
            FollowRequest request = FollowRequest.builder()
                    .userId(TARGET_USER_ID)
                    .notificationsEnabled(true)
                    .build();

            when(blockRepository.isBlockedEitherWay(USER_ID, TARGET_USER_ID)).thenReturn(false);
            when(followRepository.existsByFollowerIdAndFollowingId(USER_ID, TARGET_USER_ID)).thenReturn(false);
            when(followRepository.countByFollowerId(USER_ID)).thenReturn(0L);
            when(followRepository.save(any(Follow.class))).thenAnswer(invocation -> {
                Follow saved = invocation.getArgument(0);
                assertThat(saved.getNotificationsEnabled()).isTrue();
                saved.setId(FOLLOW_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(TARGET_USER_ID)).thenReturn(testUserSummary);

            followService.follow(USER_ID, request);

            verify(followRepository).save(any(Follow.class));
        }

        @Test
        @DisplayName("Success - isCloseFriend explicit false")
        void follow_IsCloseFriendExplicitFalse() {
            FollowRequest request = FollowRequest.builder()
                    .userId(TARGET_USER_ID)
                    .isCloseFriend(false)
                    .build();

            when(blockRepository.isBlockedEitherWay(USER_ID, TARGET_USER_ID)).thenReturn(false);
            when(followRepository.existsByFollowerIdAndFollowingId(USER_ID, TARGET_USER_ID)).thenReturn(false);
            when(followRepository.countByFollowerId(USER_ID)).thenReturn(0L);
            when(followRepository.save(any(Follow.class))).thenAnswer(invocation -> {
                Follow saved = invocation.getArgument(0);
                assertThat(saved.getIsCloseFriend()).isFalse();
                saved.setId(FOLLOW_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(TARGET_USER_ID)).thenReturn(testUserSummary);

            followService.follow(USER_ID, request);

            verify(followRepository).save(any(Follow.class));
        }

        @Test
        @DisplayName("Cannot follow yourself - throws ValidationException")
        void follow_CannotFollowYourself() {
            FollowRequest request = FollowRequest.builder()
                    .userId(USER_ID)
                    .build();

            assertThatThrownBy(() -> followService.follow(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Cannot follow yourself");

            verifyNoInteractions(followRepository);
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Blocked either way - throws ValidationException")
        void follow_BlockedEitherWay() {
            FollowRequest request = FollowRequest.builder()
                    .userId(TARGET_USER_ID)
                    .build();

            when(blockRepository.isBlockedEitherWay(USER_ID, TARGET_USER_ID)).thenReturn(true);

            assertThatThrownBy(() -> followService.follow(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Cannot follow this user");

            verify(followRepository, never()).save(any());
        }

        @Test
        @DisplayName("Already following - throws ValidationException")
        void follow_AlreadyFollowing() {
            FollowRequest request = FollowRequest.builder()
                    .userId(TARGET_USER_ID)
                    .build();

            when(blockRepository.isBlockedEitherWay(USER_ID, TARGET_USER_ID)).thenReturn(false);
            when(followRepository.existsByFollowerIdAndFollowingId(USER_ID, TARGET_USER_ID)).thenReturn(true);

            assertThatThrownBy(() -> followService.follow(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Already following this user");

            verify(followRepository, never()).save(any());
        }

        @Test
        @DisplayName("Max following limit reached - throws ValidationException")
        void follow_MaxLimitReached() {
            FollowRequest request = FollowRequest.builder()
                    .userId(TARGET_USER_ID)
                    .build();

            when(blockRepository.isBlockedEitherWay(USER_ID, TARGET_USER_ID)).thenReturn(false);
            when(followRepository.existsByFollowerIdAndFollowingId(USER_ID, TARGET_USER_ID)).thenReturn(false);
            when(followRepository.countByFollowerId(USER_ID)).thenReturn(5000L);

            assertThatThrownBy(() -> followService.follow(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Maximum following limit reached");

            verify(followRepository, never()).save(any());
        }

        @Test
        @DisplayName("At boundary - one below max limit succeeds")
        void follow_OneBelowMaxLimit() {
            FollowRequest request = FollowRequest.builder()
                    .userId(TARGET_USER_ID)
                    .build();

            when(blockRepository.isBlockedEitherWay(USER_ID, TARGET_USER_ID)).thenReturn(false);
            when(followRepository.existsByFollowerIdAndFollowingId(USER_ID, TARGET_USER_ID)).thenReturn(false);
            when(followRepository.countByFollowerId(USER_ID)).thenReturn(4999L);
            when(followRepository.save(any(Follow.class))).thenAnswer(invocation -> {
                Follow saved = invocation.getArgument(0);
                saved.setId(FOLLOW_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(TARGET_USER_ID)).thenReturn(testUserSummary);

            FollowResponse response = followService.follow(USER_ID, request);

            assertThat(response).isNotNull();
        }
    }

    // ==================== UNFOLLOW ====================

    @Nested
    @DisplayName("unfollow")
    class UnfollowTests {

        @Test
        @DisplayName("Success - unfollow user")
        void unfollow_Success() {
            when(followRepository.existsByFollowerIdAndFollowingId(USER_ID, TARGET_USER_ID)).thenReturn(true);
            doNothing().when(followRepository).hardDelete(USER_ID, TARGET_USER_ID);

            followService.unfollow(USER_ID, TARGET_USER_ID);

            verify(followRepository).hardDelete(USER_ID, TARGET_USER_ID);
            verify(eventPublisher).publishUnfollowed(USER_ID, TARGET_USER_ID);
        }

        @Test
        @DisplayName("Follow not found - throws ResourceNotFoundException")
        void unfollow_NotFound() {
            when(followRepository.existsByFollowerIdAndFollowingId(USER_ID, TARGET_USER_ID)).thenReturn(false);

            assertThatThrownBy(() -> followService.unfollow(USER_ID, TARGET_USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Follow relationship not found");

            verify(followRepository, never()).hardDelete(any(), any());
            verify(eventPublisher, never()).publishUnfollowed(any(), any());
        }
    }

    // ==================== UPDATE FOLLOW ====================

    @Nested
    @DisplayName("updateFollow")
    class UpdateFollowTests {

        @Test
        @DisplayName("Success - update all fields")
        void updateFollow_AllFields() {
            UpdateFollowRequest request = UpdateFollowRequest.builder()
                    .notificationsEnabled(false)
                    .isCloseFriend(true)
                    .isMuted(true)
                    .build();

            when(followRepository.findByFollowerIdAndFollowingId(USER_ID, TARGET_USER_ID))
                    .thenReturn(Optional.of(testFollow));
            when(followRepository.save(any(Follow.class))).thenReturn(testFollow);
            when(userServiceClient.getUserSummary(TARGET_USER_ID)).thenReturn(testUserSummary);

            FollowResponse response = followService.updateFollow(USER_ID, TARGET_USER_ID, request);

            assertThat(response).isNotNull();
            verify(followRepository).save(argThat(follow -> {
                assertThat(follow.getNotificationsEnabled()).isFalse();
                assertThat(follow.getIsCloseFriend()).isTrue();
                assertThat(follow.getIsMuted()).isTrue();
                return true;
            }));
        }

        @Test
        @DisplayName("Success - update only notificationsEnabled")
        void updateFollow_OnlyNotificationsEnabled() {
            UpdateFollowRequest request = UpdateFollowRequest.builder()
                    .notificationsEnabled(false)
                    .build();

            when(followRepository.findByFollowerIdAndFollowingId(USER_ID, TARGET_USER_ID))
                    .thenReturn(Optional.of(testFollow));
            when(followRepository.save(any(Follow.class))).thenReturn(testFollow);
            when(userServiceClient.getUserSummary(TARGET_USER_ID)).thenReturn(testUserSummary);

            followService.updateFollow(USER_ID, TARGET_USER_ID, request);

            verify(followRepository).save(argThat(follow -> {
                assertThat(follow.getNotificationsEnabled()).isFalse();
                // Other fields unchanged
                assertThat(follow.getIsCloseFriend()).isFalse();
                assertThat(follow.getIsMuted()).isFalse();
                return true;
            }));
        }

        @Test
        @DisplayName("Success - update only isCloseFriend")
        void updateFollow_OnlyIsCloseFriend() {
            UpdateFollowRequest request = UpdateFollowRequest.builder()
                    .isCloseFriend(true)
                    .build();

            when(followRepository.findByFollowerIdAndFollowingId(USER_ID, TARGET_USER_ID))
                    .thenReturn(Optional.of(testFollow));
            when(followRepository.save(any(Follow.class))).thenReturn(testFollow);
            when(userServiceClient.getUserSummary(TARGET_USER_ID)).thenReturn(testUserSummary);

            followService.updateFollow(USER_ID, TARGET_USER_ID, request);

            verify(followRepository).save(argThat(follow -> {
                assertThat(follow.getIsCloseFriend()).isTrue();
                // Other fields unchanged
                assertThat(follow.getNotificationsEnabled()).isTrue();
                assertThat(follow.getIsMuted()).isFalse();
                return true;
            }));
        }

        @Test
        @DisplayName("Success - update only isMuted")
        void updateFollow_OnlyIsMuted() {
            UpdateFollowRequest request = UpdateFollowRequest.builder()
                    .isMuted(true)
                    .build();

            when(followRepository.findByFollowerIdAndFollowingId(USER_ID, TARGET_USER_ID))
                    .thenReturn(Optional.of(testFollow));
            when(followRepository.save(any(Follow.class))).thenReturn(testFollow);
            when(userServiceClient.getUserSummary(TARGET_USER_ID)).thenReturn(testUserSummary);

            followService.updateFollow(USER_ID, TARGET_USER_ID, request);

            verify(followRepository).save(argThat(follow -> {
                assertThat(follow.getIsMuted()).isTrue();
                // Other fields unchanged
                assertThat(follow.getNotificationsEnabled()).isTrue();
                assertThat(follow.getIsCloseFriend()).isFalse();
                return true;
            }));
        }

        @Test
        @DisplayName("Success - no updates (all null)")
        void updateFollow_NoUpdates() {
            UpdateFollowRequest request = UpdateFollowRequest.builder().build();

            when(followRepository.findByFollowerIdAndFollowingId(USER_ID, TARGET_USER_ID))
                    .thenReturn(Optional.of(testFollow));
            when(followRepository.save(any(Follow.class))).thenReturn(testFollow);
            when(userServiceClient.getUserSummary(TARGET_USER_ID)).thenReturn(testUserSummary);

            followService.updateFollow(USER_ID, TARGET_USER_ID, request);

            verify(followRepository).save(argThat(follow -> {
                // All fields unchanged
                assertThat(follow.getNotificationsEnabled()).isTrue();
                assertThat(follow.getIsCloseFriend()).isFalse();
                assertThat(follow.getIsMuted()).isFalse();
                return true;
            }));
        }

        @Test
        @DisplayName("Follow not found - throws ResourceNotFoundException")
        void updateFollow_NotFound() {
            UpdateFollowRequest request = UpdateFollowRequest.builder()
                    .notificationsEnabled(false)
                    .build();

            when(followRepository.findByFollowerIdAndFollowingId(USER_ID, TARGET_USER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> followService.updateFollow(USER_ID, TARGET_USER_ID, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Follow relationship not found");

            verify(followRepository, never()).save(any());
        }
    }

    // ==================== GET FOLLOWERS ====================

    @Nested
    @DisplayName("getFollowers")
    class GetFollowersTests {

        @Test
        @DisplayName("Success - returns paginated followers")
        void getFollowers_Success() {
            // Create a follow where TARGET_USER_ID is following USER_ID
            Follow followerRelation = Follow.builder()
                    .id(FOLLOW_ID)
                    .followerId(TARGET_USER_ID)
                    .followingId(USER_ID)
                    .notificationsEnabled(true)
                    .isCloseFriend(false)
                    .isMuted(false)
                    .build();
            followerRelation.setCreatedAt(LocalDateTime.now());

            Page<Follow> page = new PageImpl<>(
                    List.of(followerRelation),
                    PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")),
                    1
            );

            UserSummary followerSummary = UserSummary.builder()
                    .id(TARGET_USER_ID)
                    .username("follower")
                    .build();

            when(followRepository.findByFollowingId(eq(USER_ID), any(Pageable.class))).thenReturn(page);
            when(userServiceClient.getUserSummary(TARGET_USER_ID)).thenReturn(followerSummary);

            PageResponse<FollowResponse> response = followService.getFollowers(USER_ID, 0, 20);

            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getTotalElements()).isEqualTo(1);
            // The user in response should be the follower
            assertThat(response.getContent().get(0).getUser().getId()).isEqualTo(TARGET_USER_ID);
        }

        @Test
        @DisplayName("Success - empty list")
        void getFollowers_Empty() {
            Page<Follow> emptyPage = new PageImpl<>(
                    Collections.emptyList(),
                    PageRequest.of(0, 20),
                    0
            );

            when(followRepository.findByFollowingId(eq(USER_ID), any(Pageable.class))).thenReturn(emptyPage);

            PageResponse<FollowResponse> response = followService.getFollowers(USER_ID, 0, 20);

            assertThat(response.getContent()).isEmpty();
            assertThat(response.getTotalElements()).isEqualTo(0);
        }

        @Test
        @DisplayName("Success - multiple followers")
        void getFollowers_Multiple() {
            UUID follower2Id = UUID.randomUUID();
            Follow follow1 = Follow.builder()
                    .id(FOLLOW_ID)
                    .followerId(TARGET_USER_ID)
                    .followingId(USER_ID)
                    .build();
            follow1.setCreatedAt(LocalDateTime.now());

            Follow follow2 = Follow.builder()
                    .id(UUID.randomUUID())
                    .followerId(follower2Id)
                    .followingId(USER_ID)
                    .build();
            follow2.setCreatedAt(LocalDateTime.now());

            Page<Follow> page = new PageImpl<>(List.of(follow1, follow2), PageRequest.of(0, 20), 2);

            UserSummary summary1 = UserSummary.builder().id(TARGET_USER_ID).username("follower1").build();
            UserSummary summary2 = UserSummary.builder().id(follower2Id).username("follower2").build();

            when(followRepository.findByFollowingId(eq(USER_ID), any(Pageable.class))).thenReturn(page);
            when(userServiceClient.getUserSummary(TARGET_USER_ID)).thenReturn(summary1);
            when(userServiceClient.getUserSummary(follower2Id)).thenReturn(summary2);

            PageResponse<FollowResponse> response = followService.getFollowers(USER_ID, 0, 20);

            assertThat(response.getContent()).hasSize(2);
        }
    }

    // ==================== GET FOLLOWING ====================

    @Nested
    @DisplayName("getFollowing")
    class GetFollowingTests {

        @Test
        @DisplayName("Success - returns paginated following")
        void getFollowing_Success() {
            Page<Follow> page = new PageImpl<>(
                    List.of(testFollow),
                    PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")),
                    1
            );

            when(followRepository.findByFollowerId(eq(USER_ID), any(Pageable.class))).thenReturn(page);
            when(userServiceClient.getUserSummary(TARGET_USER_ID)).thenReturn(testUserSummary);

            PageResponse<FollowResponse> response = followService.getFollowing(USER_ID, 0, 20);

            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getTotalElements()).isEqualTo(1);
            // The user in response should be the following user
            assertThat(response.getContent().get(0).getUser().getId()).isEqualTo(TARGET_USER_ID);
        }

        @Test
        @DisplayName("Success - empty list")
        void getFollowing_Empty() {
            Page<Follow> emptyPage = new PageImpl<>(
                    Collections.emptyList(),
                    PageRequest.of(0, 20),
                    0
            );

            when(followRepository.findByFollowerId(eq(USER_ID), any(Pageable.class))).thenReturn(emptyPage);

            PageResponse<FollowResponse> response = followService.getFollowing(USER_ID, 0, 20);

            assertThat(response.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Success - custom pagination")
        void getFollowing_CustomPagination() {
            Page<Follow> page = new PageImpl<>(
                    Collections.emptyList(),
                    PageRequest.of(5, 50),
                    0
            );

            when(followRepository.findByFollowerId(eq(USER_ID), any(Pageable.class))).thenReturn(page);

            PageResponse<FollowResponse> response = followService.getFollowing(USER_ID, 5, 50);

            assertThat(response.getPage()).isEqualTo(5);
            assertThat(response.getSize()).isEqualTo(50);
        }
    }

    // ==================== GET CLOSE FRIENDS ====================

    @Nested
    @DisplayName("getCloseFriends")
    class GetCloseFriendsTests {

        @Test
        @DisplayName("Success - returns paginated close friends")
        void getCloseFriends_Success() {
            testFollow.setIsCloseFriend(true);
            Page<Follow> page = new PageImpl<>(
                    List.of(testFollow),
                    PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")),
                    1
            );

            when(followRepository.findByFollowerIdAndIsCloseFriendTrue(eq(USER_ID), any(Pageable.class)))
                    .thenReturn(page);
            when(userServiceClient.getUserSummary(TARGET_USER_ID)).thenReturn(testUserSummary);

            PageResponse<FollowResponse> response = followService.getCloseFriends(USER_ID, 0, 20);

            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getContent().get(0).getIsCloseFriend()).isTrue();
        }

        @Test
        @DisplayName("Success - empty list")
        void getCloseFriends_Empty() {
            Page<Follow> emptyPage = new PageImpl<>(
                    Collections.emptyList(),
                    PageRequest.of(0, 20),
                    0
            );

            when(followRepository.findByFollowerIdAndIsCloseFriendTrue(eq(USER_ID), any(Pageable.class)))
                    .thenReturn(emptyPage);

            PageResponse<FollowResponse> response = followService.getCloseFriends(USER_ID, 0, 20);

            assertThat(response.getContent()).isEmpty();
        }
    }

    // ==================== GET FOLLOW STATS ====================

    @Nested
    @DisplayName("getFollowStats")
    class GetFollowStatsTests {

        @Test
        @DisplayName("Success - with different requesterId")
        void getFollowStats_WithDifferentRequesterId() {
            when(followRepository.countByFollowingId(TARGET_USER_ID)).thenReturn(150L);
            when(followRepository.countByFollowerId(TARGET_USER_ID)).thenReturn(200L);
            when(followRepository.existsByFollowerIdAndFollowingId(USER_ID, TARGET_USER_ID)).thenReturn(true);
            when(followRepository.existsByFollowerIdAndFollowingId(TARGET_USER_ID, USER_ID)).thenReturn(false);

            FollowStatsResponse response = followService.getFollowStats(TARGET_USER_ID, USER_ID);

            assertThat(response.getUserId()).isEqualTo(TARGET_USER_ID);
            assertThat(response.getFollowersCount()).isEqualTo(150L);
            assertThat(response.getFollowingCount()).isEqualTo(200L);
            assertThat(response.getIsFollowing()).isTrue();
            assertThat(response.getIsFollowedBy()).isFalse();
        }

        @Test
        @DisplayName("Success - requesterId equals userId (own stats)")
        void getFollowStats_OwnStats() {
            when(followRepository.countByFollowingId(USER_ID)).thenReturn(50L);
            when(followRepository.countByFollowerId(USER_ID)).thenReturn(75L);

            FollowStatsResponse response = followService.getFollowStats(USER_ID, USER_ID);

            assertThat(response.getUserId()).isEqualTo(USER_ID);
            assertThat(response.getFollowersCount()).isEqualTo(50L);
            assertThat(response.getFollowingCount()).isEqualTo(75L);
            // Should not check follow relationships for self
            assertThat(response.getIsFollowing()).isFalse();
            assertThat(response.getIsFollowedBy()).isFalse();
            // Verify relationship checks not called
            verify(followRepository, never()).existsByFollowerIdAndFollowingId(any(), any());
        }

        @Test
        @DisplayName("Success - null requesterId")
        void getFollowStats_NullRequesterId() {
            when(followRepository.countByFollowingId(TARGET_USER_ID)).thenReturn(100L);
            when(followRepository.countByFollowerId(TARGET_USER_ID)).thenReturn(120L);

            FollowStatsResponse response = followService.getFollowStats(TARGET_USER_ID, null);

            assertThat(response.getUserId()).isEqualTo(TARGET_USER_ID);
            assertThat(response.getFollowersCount()).isEqualTo(100L);
            assertThat(response.getFollowingCount()).isEqualTo(120L);
            // Should not check follow relationships when no requester
            assertThat(response.getIsFollowing()).isFalse();
            assertThat(response.getIsFollowedBy()).isFalse();
            verify(followRepository, never()).existsByFollowerIdAndFollowingId(any(), any());
        }

        @Test
        @DisplayName("Success - mutual follow")
        void getFollowStats_MutualFollow() {
            when(followRepository.countByFollowingId(TARGET_USER_ID)).thenReturn(100L);
            when(followRepository.countByFollowerId(TARGET_USER_ID)).thenReturn(100L);
            when(followRepository.existsByFollowerIdAndFollowingId(USER_ID, TARGET_USER_ID)).thenReturn(true);
            when(followRepository.existsByFollowerIdAndFollowingId(TARGET_USER_ID, USER_ID)).thenReturn(true);

            FollowStatsResponse response = followService.getFollowStats(TARGET_USER_ID, USER_ID);

            assertThat(response.getIsFollowing()).isTrue();
            assertThat(response.getIsFollowedBy()).isTrue();
        }

        @Test
        @DisplayName("Success - not following, is followed by")
        void getFollowStats_NotFollowingButFollowedBy() {
            when(followRepository.countByFollowingId(TARGET_USER_ID)).thenReturn(100L);
            when(followRepository.countByFollowerId(TARGET_USER_ID)).thenReturn(100L);
            when(followRepository.existsByFollowerIdAndFollowingId(USER_ID, TARGET_USER_ID)).thenReturn(false);
            when(followRepository.existsByFollowerIdAndFollowingId(TARGET_USER_ID, USER_ID)).thenReturn(true);

            FollowStatsResponse response = followService.getFollowStats(TARGET_USER_ID, USER_ID);

            assertThat(response.getIsFollowing()).isFalse();
            assertThat(response.getIsFollowedBy()).isTrue();
        }

        @Test
        @DisplayName("Success - no relationship")
        void getFollowStats_NoRelationship() {
            when(followRepository.countByFollowingId(TARGET_USER_ID)).thenReturn(0L);
            when(followRepository.countByFollowerId(TARGET_USER_ID)).thenReturn(0L);
            when(followRepository.existsByFollowerIdAndFollowingId(USER_ID, TARGET_USER_ID)).thenReturn(false);
            when(followRepository.existsByFollowerIdAndFollowingId(TARGET_USER_ID, USER_ID)).thenReturn(false);

            FollowStatsResponse response = followService.getFollowStats(TARGET_USER_ID, USER_ID);

            assertThat(response.getIsFollowing()).isFalse();
            assertThat(response.getIsFollowedBy()).isFalse();
        }
    }

    // ==================== GET FOLLOWING IDS ====================

    @Nested
    @DisplayName("getFollowingIds")
    class GetFollowingIdsTests {

        @Test
        @DisplayName("Success - returns following user IDs")
        void getFollowingIds_Success() {
            UUID user2 = UUID.randomUUID();
            UUID user3 = UUID.randomUUID();
            List<UUID> ids = List.of(TARGET_USER_ID, user2, user3);

            when(followRepository.findFollowingIdsByFollowerId(USER_ID)).thenReturn(ids);

            List<UUID> result = followService.getFollowingIds(USER_ID);

            assertThat(result).hasSize(3);
            assertThat(result).containsExactly(TARGET_USER_ID, user2, user3);
        }

        @Test
        @DisplayName("Success - empty list")
        void getFollowingIds_Empty() {
            when(followRepository.findFollowingIdsByFollowerId(USER_ID)).thenReturn(Collections.emptyList());

            List<UUID> result = followService.getFollowingIds(USER_ID);

            assertThat(result).isEmpty();
        }
    }

    // ==================== IS FOLLOWING ====================

    @Nested
    @DisplayName("isFollowing")
    class IsFollowingTests {

        @Test
        @DisplayName("Returns true when following")
        void isFollowing_ReturnsTrue() {
            when(followRepository.existsByFollowerIdAndFollowingId(USER_ID, TARGET_USER_ID)).thenReturn(true);

            boolean result = followService.isFollowing(USER_ID, TARGET_USER_ID);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Returns false when not following")
        void isFollowing_ReturnsFalse() {
            when(followRepository.existsByFollowerIdAndFollowingId(USER_ID, TARGET_USER_ID)).thenReturn(false);

            boolean result = followService.isFollowing(USER_ID, TARGET_USER_ID);

            assertThat(result).isFalse();
        }
    }

    // ==================== FETCH USER SUMMARY ====================

    @Nested
    @DisplayName("fetchUserSummary")
    class FetchUserSummaryTests {

        @Test
        @DisplayName("Success - returns user summary")
        void fetchUserSummary_Success() {
            when(followRepository.findByFollowerIdAndFollowingId(USER_ID, TARGET_USER_ID))
                    .thenReturn(Optional.of(testFollow));
            when(followRepository.save(any(Follow.class))).thenReturn(testFollow);
            when(userServiceClient.getUserSummary(TARGET_USER_ID)).thenReturn(testUserSummary);

            FollowResponse response = followService.updateFollow(USER_ID, TARGET_USER_ID,
                    UpdateFollowRequest.builder().build());

            assertThat(response.getUser()).isNotNull();
            assertThat(response.getUser().getUsername()).isEqualTo("targetuser");
            assertThat(response.getUser().getDisplayName()).isEqualTo("Target User");
        }

        @Test
        @DisplayName("Exception - returns fallback")
        void fetchUserSummary_ExceptionFallback() {
            when(followRepository.findByFollowerIdAndFollowingId(USER_ID, TARGET_USER_ID))
                    .thenReturn(Optional.of(testFollow));
            when(followRepository.save(any(Follow.class))).thenReturn(testFollow);
            when(userServiceClient.getUserSummary(TARGET_USER_ID))
                    .thenThrow(new RuntimeException("Service unavailable"));

            FollowResponse response = followService.updateFollow(USER_ID, TARGET_USER_ID,
                    UpdateFollowRequest.builder().build());

            assertThat(response.getUser()).isNotNull();
            assertThat(response.getUser().getId()).isEqualTo(TARGET_USER_ID);
            assertThat(response.getUser().getUsername()).isEqualTo("Unknown");
            assertThat(response.getUser().getDisplayName()).isEqualTo("Unknown User");
            assertThat(response.getUser().getAvatarUrl()).isNull();
            assertThat(response.getUser().getIsVerified()).isFalse();
        }
    }

    // ==================== MAP TO FOLLOW RESPONSE ====================

    @Nested
    @DisplayName("mapToFollowResponse")
    class MapToFollowResponseTests {

        @Test
        @DisplayName("Maps all fields correctly")
        void mapToFollowResponse_AllFields() {
            testFollow.setNotificationsEnabled(false);
            testFollow.setIsCloseFriend(true);
            testFollow.setIsMuted(true);

            Page<Follow> page = new PageImpl<>(List.of(testFollow), PageRequest.of(0, 20), 1);
            when(followRepository.findByFollowerId(eq(USER_ID), any(Pageable.class))).thenReturn(page);
            when(userServiceClient.getUserSummary(TARGET_USER_ID)).thenReturn(testUserSummary);

            PageResponse<FollowResponse> response = followService.getFollowing(USER_ID, 0, 20);

            FollowResponse followResponse = response.getContent().get(0);
            assertThat(followResponse.getId()).isEqualTo(FOLLOW_ID);
            assertThat(followResponse.getFollowerId()).isEqualTo(USER_ID);
            assertThat(followResponse.getFollowingId()).isEqualTo(TARGET_USER_ID);
            assertThat(followResponse.getNotificationsEnabled()).isFalse();
            assertThat(followResponse.getIsCloseFriend()).isTrue();
            assertThat(followResponse.getIsMuted()).isTrue();
            assertThat(followResponse.getCreatedAt()).isNotNull();
            assertThat(followResponse.getUser()).isNotNull();
        }
    }

    // ==================== MAP TO FOLLOWER RESPONSE ====================

    @Nested
    @DisplayName("mapToFollowerResponse")
    class MapToFollowerResponseTests {

        @Test
        @DisplayName("Maps follower correctly - fetches follower user details")
        void mapToFollowerResponse_FetchesFollowerDetails() {
            // Create a follow where TARGET_USER_ID is following USER_ID
            Follow followerRelation = Follow.builder()
                    .id(FOLLOW_ID)
                    .followerId(TARGET_USER_ID)
                    .followingId(USER_ID)
                    .notificationsEnabled(true)
                    .isCloseFriend(false)
                    .isMuted(false)
                    .build();
            followerRelation.setCreatedAt(LocalDateTime.now());

            Page<Follow> page = new PageImpl<>(List.of(followerRelation), PageRequest.of(0, 20), 1);

            // Should fetch the FOLLOWER's details (TARGET_USER_ID)
            when(followRepository.findByFollowingId(eq(USER_ID), any(Pageable.class))).thenReturn(page);
            when(userServiceClient.getUserSummary(TARGET_USER_ID)).thenReturn(testUserSummary);

            PageResponse<FollowResponse> response = followService.getFollowers(USER_ID, 0, 20);

            FollowResponse followResponse = response.getContent().get(0);
            // User should be the follower
            assertThat(followResponse.getUser().getId()).isEqualTo(TARGET_USER_ID);
            verify(userServiceClient).getUserSummary(TARGET_USER_ID);
        }
    }
}