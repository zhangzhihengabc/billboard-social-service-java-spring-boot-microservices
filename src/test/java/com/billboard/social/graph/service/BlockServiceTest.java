package com.billboard.social.graph.service;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.graph.dto.request.SocialRequests.BlockRequest;
import com.billboard.social.graph.dto.response.SocialResponses.BlockResponse;
import com.billboard.social.graph.entity.Block;
import com.billboard.social.graph.entity.Follow;
import com.billboard.social.graph.entity.Friendship;
import com.billboard.social.graph.event.SocialEventPublisher;
import com.billboard.social.graph.repository.BlockRepository;
import com.billboard.social.graph.repository.FollowRepository;
import com.billboard.social.graph.repository.FriendshipRepository;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlockServiceTest {

    @Mock
    private BlockRepository blockRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private FollowRepository followRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private SocialEventPublisher eventPublisher;

    @InjectMocks
    private BlockService blockService;

    // Test constants
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BLOCKED_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BLOCK_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private Block testBlock;
    private UserSummary testUserSummary;

    @BeforeEach
    void setUp() {
        // Set maxBlocked value via reflection
        ReflectionTestUtils.setField(blockService, "maxBlocked", 1000);

        testBlock = Block.builder()
                .id(BLOCK_ID)
                .blockerId(USER_ID)
                .blockedId(BLOCKED_USER_ID)
                .reason("Spam")
                .blockMessages(true)
                .blockPosts(true)
                .blockComments(true)
                .build();
        testBlock.setCreatedAt(LocalDateTime.now());

        testUserSummary = UserSummary.builder()
                .id(BLOCKED_USER_ID)
                .username("blockeduser")
                .email("test@gmail.com")
                .build();
    }

    // ==================== BLOCK USER ====================

    @Nested
    @DisplayName("blockUser")
    class BlockUserTests {

        @Test
        @DisplayName("Success - block user with all fields")
        void blockUser_Success() {
            BlockRequest request = BlockRequest.builder()
                    .userId(BLOCKED_USER_ID)
                    .reason("Spam")
                    .blockMessages(true)
                    .blockPosts(true)
                    .blockComments(true)
                    .build();

            when(blockRepository.existsByBlockerIdAndBlockedId(USER_ID, BLOCKED_USER_ID)).thenReturn(false);
            when(blockRepository.countByBlockerId(USER_ID)).thenReturn(10L);
            when(friendshipRepository.findBetweenUsers(USER_ID, BLOCKED_USER_ID)).thenReturn(Optional.empty());
            when(followRepository.findByFollowerIdAndFollowingId(USER_ID, BLOCKED_USER_ID)).thenReturn(Optional.empty());
            when(followRepository.findByFollowerIdAndFollowingId(BLOCKED_USER_ID, USER_ID)).thenReturn(Optional.empty());
            when(blockRepository.save(any(Block.class))).thenAnswer(invocation -> {
                Block saved = invocation.getArgument(0);
                saved.setId(BLOCK_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(BLOCKED_USER_ID)).thenReturn(testUserSummary);

            BlockResponse response = blockService.blockUser(USER_ID, request);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(BLOCK_ID);
            assertThat(response.getBlockedId()).isEqualTo(BLOCKED_USER_ID);
            assertThat(response.getReason()).isEqualTo("Spam");
            verify(eventPublisher).publishUserBlocked(any(Block.class));
        }

        @Test
        @DisplayName("Success - block with default values (null booleans)")
        void blockUser_WithDefaultValues() {
            BlockRequest request = BlockRequest.builder()
                    .userId(BLOCKED_USER_ID)
                    .build();

            when(blockRepository.existsByBlockerIdAndBlockedId(USER_ID, BLOCKED_USER_ID)).thenReturn(false);
            when(blockRepository.countByBlockerId(USER_ID)).thenReturn(0L);
            when(friendshipRepository.findBetweenUsers(USER_ID, BLOCKED_USER_ID)).thenReturn(Optional.empty());
            when(followRepository.findByFollowerIdAndFollowingId(USER_ID, BLOCKED_USER_ID)).thenReturn(Optional.empty());
            when(followRepository.findByFollowerIdAndFollowingId(BLOCKED_USER_ID, USER_ID)).thenReturn(Optional.empty());
            when(blockRepository.save(any(Block.class))).thenAnswer(invocation -> {
                Block saved = invocation.getArgument(0);
                // Verify defaults are applied
                assertThat(saved.getBlockMessages()).isTrue();
                assertThat(saved.getBlockPosts()).isTrue();
                assertThat(saved.getBlockComments()).isTrue();
                saved.setId(BLOCK_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(BLOCKED_USER_ID)).thenReturn(testUserSummary);

            BlockResponse response = blockService.blockUser(USER_ID, request);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Success - block with explicit false values")
        void blockUser_WithExplicitFalseValues() {
            BlockRequest request = BlockRequest.builder()
                    .userId(BLOCKED_USER_ID)
                    .blockMessages(false)
                    .blockPosts(false)
                    .blockComments(false)
                    .build();

            when(blockRepository.existsByBlockerIdAndBlockedId(USER_ID, BLOCKED_USER_ID)).thenReturn(false);
            when(blockRepository.countByBlockerId(USER_ID)).thenReturn(0L);
            when(friendshipRepository.findBetweenUsers(USER_ID, BLOCKED_USER_ID)).thenReturn(Optional.empty());
            when(followRepository.findByFollowerIdAndFollowingId(USER_ID, BLOCKED_USER_ID)).thenReturn(Optional.empty());
            when(followRepository.findByFollowerIdAndFollowingId(BLOCKED_USER_ID, USER_ID)).thenReturn(Optional.empty());
            when(blockRepository.save(any(Block.class))).thenAnswer(invocation -> {
                Block saved = invocation.getArgument(0);
                assertThat(saved.getBlockMessages()).isFalse();
                assertThat(saved.getBlockPosts()).isFalse();
                assertThat(saved.getBlockComments()).isFalse();
                saved.setId(BLOCK_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(BLOCKED_USER_ID)).thenReturn(testUserSummary);

            blockService.blockUser(USER_ID, request);

            verify(blockRepository).save(any(Block.class));
        }

        @Test
        @DisplayName("Success - removes existing friendship")
        void blockUser_RemovesExistingFriendship() {
            BlockRequest request = BlockRequest.builder()
                    .userId(BLOCKED_USER_ID)
                    .build();

            Friendship existingFriendship = Friendship.builder()
                    .id(UUID.randomUUID())
                    .requesterId(USER_ID)
                    .addresseeId(BLOCKED_USER_ID)
                    .build();

            when(blockRepository.existsByBlockerIdAndBlockedId(USER_ID, BLOCKED_USER_ID)).thenReturn(false);
            when(blockRepository.countByBlockerId(USER_ID)).thenReturn(0L);
            when(friendshipRepository.findBetweenUsers(USER_ID, BLOCKED_USER_ID))
                    .thenReturn(Optional.of(existingFriendship));
            when(followRepository.findByFollowerIdAndFollowingId(USER_ID, BLOCKED_USER_ID)).thenReturn(Optional.empty());
            when(followRepository.findByFollowerIdAndFollowingId(BLOCKED_USER_ID, USER_ID)).thenReturn(Optional.empty());
            when(blockRepository.save(any(Block.class))).thenAnswer(invocation -> {
                Block saved = invocation.getArgument(0);
                saved.setId(BLOCK_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(BLOCKED_USER_ID)).thenReturn(testUserSummary);

            blockService.blockUser(USER_ID, request);

            verify(friendshipRepository).delete(existingFriendship);
        }

        @Test
        @DisplayName("Success - removes both follows (blocker->blocked and blocked->blocker)")
        void blockUser_RemovesBothFollows() {
            BlockRequest request = BlockRequest.builder()
                    .userId(BLOCKED_USER_ID)
                    .build();

            Follow follow1 = Follow.builder()
                    .id(UUID.randomUUID())
                    .followerId(USER_ID)
                    .followingId(BLOCKED_USER_ID)
                    .build();

            Follow follow2 = Follow.builder()
                    .id(UUID.randomUUID())
                    .followerId(BLOCKED_USER_ID)
                    .followingId(USER_ID)
                    .build();

            when(blockRepository.existsByBlockerIdAndBlockedId(USER_ID, BLOCKED_USER_ID)).thenReturn(false);
            when(blockRepository.countByBlockerId(USER_ID)).thenReturn(0L);
            when(friendshipRepository.findBetweenUsers(USER_ID, BLOCKED_USER_ID)).thenReturn(Optional.empty());
            when(followRepository.findByFollowerIdAndFollowingId(USER_ID, BLOCKED_USER_ID))
                    .thenReturn(Optional.of(follow1));
            when(followRepository.findByFollowerIdAndFollowingId(BLOCKED_USER_ID, USER_ID))
                    .thenReturn(Optional.of(follow2));
            when(blockRepository.save(any(Block.class))).thenAnswer(invocation -> {
                Block saved = invocation.getArgument(0);
                saved.setId(BLOCK_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(BLOCKED_USER_ID)).thenReturn(testUserSummary);

            blockService.blockUser(USER_ID, request);

            verify(followRepository).delete(follow1);
            verify(followRepository).delete(follow2);
        }

        @Test
        @DisplayName("Null userId - throws ValidationException")
        void blockUser_NullUserId() {
            BlockRequest request = BlockRequest.builder().build();

            assertThatThrownBy(() -> blockService.blockUser(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("User ID is required");

            verifyNoInteractions(blockRepository);
        }

        @Test
        @DisplayName("Block yourself - throws ValidationException")
        void blockUser_BlockYourself() {
            BlockRequest request = BlockRequest.builder()
                    .userId(USER_ID)
                    .build();

            assertThatThrownBy(() -> blockService.blockUser(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Cannot block yourself");

            verify(blockRepository, never()).save(any());
        }

        @Test
        @DisplayName("Already blocked - throws ValidationException")
        void blockUser_AlreadyBlocked() {
            BlockRequest request = BlockRequest.builder()
                    .userId(BLOCKED_USER_ID)
                    .build();

            when(blockRepository.existsByBlockerIdAndBlockedId(USER_ID, BLOCKED_USER_ID)).thenReturn(true);

            assertThatThrownBy(() -> blockService.blockUser(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("User already blocked");

            verify(blockRepository, never()).save(any());
        }

        @Test
        @DisplayName("Max limit reached - throws ValidationException")
        void blockUser_MaxLimitReached() {
            BlockRequest request = BlockRequest.builder()
                    .userId(BLOCKED_USER_ID)
                    .build();

            when(blockRepository.existsByBlockerIdAndBlockedId(USER_ID, BLOCKED_USER_ID)).thenReturn(false);
            when(blockRepository.countByBlockerId(USER_ID)).thenReturn(1000L);

            assertThatThrownBy(() -> blockService.blockUser(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Maximum blocked users limit reached");

            verify(blockRepository, never()).save(any());
        }

        @Test
        @DisplayName("Race condition - DataIntegrityViolationException")
        void blockUser_RaceCondition() {
            BlockRequest request = BlockRequest.builder()
                    .userId(BLOCKED_USER_ID)
                    .build();

            when(blockRepository.existsByBlockerIdAndBlockedId(USER_ID, BLOCKED_USER_ID)).thenReturn(false);
            when(blockRepository.countByBlockerId(USER_ID)).thenReturn(0L);
            when(friendshipRepository.findBetweenUsers(USER_ID, BLOCKED_USER_ID)).thenReturn(Optional.empty());
            when(followRepository.findByFollowerIdAndFollowingId(USER_ID, BLOCKED_USER_ID)).thenReturn(Optional.empty());
            when(followRepository.findByFollowerIdAndFollowingId(BLOCKED_USER_ID, USER_ID)).thenReturn(Optional.empty());
            when(blockRepository.save(any(Block.class)))
                    .thenThrow(new DataIntegrityViolationException("Duplicate key"));

            assertThatThrownBy(() -> blockService.blockUser(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("User already blocked");

            verify(eventPublisher, never()).publishUserBlocked(any());
        }
    }

    // ==================== UNBLOCK USER ====================

    @Nested
    @DisplayName("unblockUser")
    class UnblockUserTests {

        @Test
        @DisplayName("Success - unblock user")
        void unblockUser_Success() {
            when(blockRepository.findByBlockerIdAndBlockedId(USER_ID, BLOCKED_USER_ID))
                    .thenReturn(Optional.of(testBlock));
            doNothing().when(blockRepository).delete(testBlock);

            blockService.unblockUser(USER_ID, BLOCKED_USER_ID);

            verify(blockRepository).delete(testBlock);
            verify(eventPublisher).publishUserUnblocked(USER_ID, BLOCKED_USER_ID);
        }

        @Test
        @DisplayName("Block not found - throws ValidationException")
        void unblockUser_NotFound() {
            when(blockRepository.findByBlockerIdAndBlockedId(USER_ID, BLOCKED_USER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> blockService.unblockUser(USER_ID, BLOCKED_USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Block relationship not found");

            verify(blockRepository, never()).delete(any(Block.class));
            verify(eventPublisher, never()).publishUserUnblocked(any(), any());
        }
    }

    // ==================== GET BLOCKED USERS ====================

    @Nested
    @DisplayName("getBlockedUsers")
    class GetBlockedUsersTests {

        @Test
        @DisplayName("Success - returns paginated blocked users")
        void getBlockedUsers_Success() {
            Page<Block> page = new PageImpl<>(
                    List.of(testBlock),
                    PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")),
                    1
            );

            when(blockRepository.findByBlockerId(eq(USER_ID), any(Pageable.class))).thenReturn(page);
            when(userServiceClient.getUserSummary(BLOCKED_USER_ID)).thenReturn(testUserSummary);

            PageResponse<BlockResponse> response = blockService.getBlockedUsers(USER_ID, 0, 20);

            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getTotalElements()).isEqualTo(1);
            assertThat(response.getContent().get(0).getBlockedId()).isEqualTo(BLOCKED_USER_ID);
        }

        @Test
        @DisplayName("Success - empty list")
        void getBlockedUsers_Empty() {
            Page<Block> emptyPage = new PageImpl<>(
                    Collections.emptyList(),
                    PageRequest.of(0, 20),
                    0
            );

            when(blockRepository.findByBlockerId(eq(USER_ID), any(Pageable.class))).thenReturn(emptyPage);

            PageResponse<BlockResponse> response = blockService.getBlockedUsers(USER_ID, 0, 20);

            assertThat(response.getContent()).isEmpty();
            assertThat(response.getTotalElements()).isEqualTo(0);
        }

        @Test
        @DisplayName("Success - multiple blocked users")
        void getBlockedUsers_Multiple() {
            UUID blockedUser2 = UUID.randomUUID();
            Block block2 = Block.builder()
                    .id(UUID.randomUUID())
                    .blockerId(USER_ID)
                    .blockedId(blockedUser2)
                    .build();
            block2.setCreatedAt(LocalDateTime.now());

            Page<Block> page = new PageImpl<>(
                    List.of(testBlock, block2),
                    PageRequest.of(0, 20),
                    2
            );

            UserSummary userSummary2 = UserSummary.builder()
                    .id(blockedUser2)
                    .username("user2")
                    .build();

            when(blockRepository.findByBlockerId(eq(USER_ID), any(Pageable.class))).thenReturn(page);
            when(userServiceClient.getUserSummary(BLOCKED_USER_ID)).thenReturn(testUserSummary);
            when(userServiceClient.getUserSummary(blockedUser2)).thenReturn(userSummary2);

            PageResponse<BlockResponse> response = blockService.getBlockedUsers(USER_ID, 0, 20);

            assertThat(response.getContent()).hasSize(2);
        }

        @Test
        @DisplayName("Custom pagination")
        void getBlockedUsers_CustomPagination() {
            Page<Block> page = new PageImpl<>(
                    Collections.emptyList(),
                    PageRequest.of(5, 50),
                    0
            );

            when(blockRepository.findByBlockerId(eq(USER_ID), any(Pageable.class))).thenReturn(page);

            PageResponse<BlockResponse> response = blockService.getBlockedUsers(USER_ID, 5, 50);

            assertThat(response.getPage()).isEqualTo(5);
            assertThat(response.getSize()).isEqualTo(50);
        }
    }

    // ==================== GET BLOCKED USER IDS ====================

    @Nested
    @DisplayName("getBlockedUserIds")
    class GetBlockedUserIdsTests {

        @Test
        @DisplayName("Success - returns user IDs")
        void getBlockedUserIds_Success() {
            UUID blockedUser2 = UUID.randomUUID();
            List<UUID> ids = List.of(BLOCKED_USER_ID, blockedUser2);

            when(blockRepository.findBlockedUserIds(USER_ID)).thenReturn(ids);

            List<UUID> result = blockService.getBlockedUserIds(USER_ID);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(BLOCKED_USER_ID, blockedUser2);
        }

        @Test
        @DisplayName("Success - empty list")
        void getBlockedUserIds_Empty() {
            when(blockRepository.findBlockedUserIds(USER_ID)).thenReturn(Collections.emptyList());

            List<UUID> result = blockService.getBlockedUserIds(USER_ID);

            assertThat(result).isEmpty();
        }
    }

    // ==================== IS BLOCKED ====================

    @Nested
    @DisplayName("isBlocked")
    class IsBlockedTests {

        @Test
        @DisplayName("Returns true when blocked")
        void isBlocked_ReturnsTrue() {
            when(blockRepository.existsByBlockerIdAndBlockedId(USER_ID, BLOCKED_USER_ID)).thenReturn(true);

            boolean result = blockService.isBlocked(USER_ID, BLOCKED_USER_ID);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Returns false when not blocked")
        void isBlocked_ReturnsFalse() {
            when(blockRepository.existsByBlockerIdAndBlockedId(USER_ID, BLOCKED_USER_ID)).thenReturn(false);

            boolean result = blockService.isBlocked(USER_ID, BLOCKED_USER_ID);

            assertThat(result).isFalse();
        }
    }

    // ==================== IS BLOCKED EITHER WAY ====================

    @Nested
    @DisplayName("isBlockedEitherWay")
    class IsBlockedEitherWayTests {

        @Test
        @DisplayName("Returns true when blocked either way")
        void isBlockedEitherWay_ReturnsTrue() {
            when(blockRepository.isBlockedEitherWay(USER_ID, BLOCKED_USER_ID)).thenReturn(true);

            boolean result = blockService.isBlockedEitherWay(USER_ID, BLOCKED_USER_ID);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Returns false when not blocked either way")
        void isBlockedEitherWay_ReturnsFalse() {
            when(blockRepository.isBlockedEitherWay(USER_ID, BLOCKED_USER_ID)).thenReturn(false);

            boolean result = blockService.isBlockedEitherWay(USER_ID, BLOCKED_USER_ID);

            assertThat(result).isFalse();
        }
    }

    // ==================== FETCH USER SUMMARY WITH FALLBACK ====================

    @Nested
    @DisplayName("fetchUserSummaryWithFallback")
    class FetchUserSummaryWithFallbackTests {

        @Test
        @DisplayName("Success - returns user summary")
        void fetchUserSummary_Success() {
            Page<Block> page = new PageImpl<>(List.of(testBlock), PageRequest.of(0, 20), 1);
            when(blockRepository.findByBlockerId(eq(USER_ID), any(Pageable.class))).thenReturn(page);
            when(userServiceClient.getUserSummary(BLOCKED_USER_ID)).thenReturn(testUserSummary);

            PageResponse<BlockResponse> response = blockService.getBlockedUsers(USER_ID, 0, 20);

            assertThat(response.getContent().get(0).getBlockedUser()).isNotNull();
            assertThat(response.getContent().get(0).getBlockedUser().getUsername()).isEqualTo("blockeduser");
        }

        @Test
        @DisplayName("Returns null - fallback to unknown")
        void fetchUserSummary_ReturnsNull() {
            Page<Block> page = new PageImpl<>(List.of(testBlock), PageRequest.of(0, 20), 1);
            when(blockRepository.findByBlockerId(eq(USER_ID), any(Pageable.class))).thenReturn(page);
            when(userServiceClient.getUserSummary(BLOCKED_USER_ID)).thenReturn(null);

            PageResponse<BlockResponse> response = blockService.getBlockedUsers(USER_ID, 0, 20);

            assertThat(response.getContent().get(0).getBlockedUser()).isNotNull();
            assertThat(response.getContent().get(0).getBlockedUser().getUsername()).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("FeignException.NotFound - fallback to unknown")
        void fetchUserSummary_FeignNotFound() {
            Page<Block> page = new PageImpl<>(List.of(testBlock), PageRequest.of(0, 20), 1);
            when(blockRepository.findByBlockerId(eq(USER_ID), any(Pageable.class))).thenReturn(page);

            Request request = Request.create(Request.HttpMethod.GET, "/users", Collections.emptyMap(), null, new RequestTemplate());
            when(userServiceClient.getUserSummary(BLOCKED_USER_ID))
                    .thenThrow(new FeignException.NotFound("Not found", request, null, null));

            PageResponse<BlockResponse> response = blockService.getBlockedUsers(USER_ID, 0, 20);

            assertThat(response.getContent().get(0).getBlockedUser()).isNotNull();
            assertThat(response.getContent().get(0).getBlockedUser().getUsername()).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("FeignException (other) - fallback to unknown")
        void fetchUserSummary_FeignOtherException() {
            Page<Block> page = new PageImpl<>(List.of(testBlock), PageRequest.of(0, 20), 1);
            when(blockRepository.findByBlockerId(eq(USER_ID), any(Pageable.class))).thenReturn(page);

            Request request = Request.create(Request.HttpMethod.GET, "/users", Collections.emptyMap(), null, new RequestTemplate());
            when(userServiceClient.getUserSummary(BLOCKED_USER_ID))
                    .thenThrow(new FeignException.ServiceUnavailable("Service down", request, null, null));

            PageResponse<BlockResponse> response = blockService.getBlockedUsers(USER_ID, 0, 20);

            assertThat(response.getContent().get(0).getBlockedUser()).isNotNull();
            assertThat(response.getContent().get(0).getBlockedUser().getUsername()).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("Generic Exception - fallback to unknown")
        void fetchUserSummary_GenericException() {
            Page<Block> page = new PageImpl<>(List.of(testBlock), PageRequest.of(0, 20), 1);
            when(blockRepository.findByBlockerId(eq(USER_ID), any(Pageable.class))).thenReturn(page);
            when(userServiceClient.getUserSummary(BLOCKED_USER_ID))
                    .thenThrow(new RuntimeException("Connection timeout"));

            PageResponse<BlockResponse> response = blockService.getBlockedUsers(USER_ID, 0, 20);

            assertThat(response.getContent().get(0).getBlockedUser()).isNotNull();
            assertThat(response.getContent().get(0).getBlockedUser().getUsername()).isEqualTo("Unknown");
            assertThat(response.getContent().get(0).getBlockedUser().getId()).isEqualTo(BLOCKED_USER_ID);
        }
    }

    // ==================== MAP TO BLOCK RESPONSE ====================

    @Nested
    @DisplayName("mapToBlockResponse")
    class MapToBlockResponseTests {

        @Test
        @DisplayName("Maps all fields correctly")
        void mapToBlockResponse_AllFields() {
            testBlock.setReason("Harassment");

            Page<Block> page = new PageImpl<>(List.of(testBlock), PageRequest.of(0, 20), 1);
            when(blockRepository.findByBlockerId(eq(USER_ID), any(Pageable.class))).thenReturn(page);
            when(userServiceClient.getUserSummary(BLOCKED_USER_ID)).thenReturn(testUserSummary);

            PageResponse<BlockResponse> response = blockService.getBlockedUsers(USER_ID, 0, 20);

            BlockResponse blockResponse = response.getContent().get(0);
            assertThat(blockResponse.getId()).isEqualTo(BLOCK_ID);
            assertThat(blockResponse.getBlockedId()).isEqualTo(BLOCKED_USER_ID);
            assertThat(blockResponse.getReason()).isEqualTo("Harassment");
            assertThat(blockResponse.getCreatedAt()).isNotNull();
            assertThat(blockResponse.getBlockedUser()).isNotNull();
            assertThat(blockResponse.getBlockedUser().getUsername()).isEqualTo("blockeduser");
        }

        @Test
        @DisplayName("Maps with null reason")
        void mapToBlockResponse_NullReason() {
            testBlock.setReason(null);

            Page<Block> page = new PageImpl<>(List.of(testBlock), PageRequest.of(0, 20), 1);
            when(blockRepository.findByBlockerId(eq(USER_ID), any(Pageable.class))).thenReturn(page);
            when(userServiceClient.getUserSummary(BLOCKED_USER_ID)).thenReturn(testUserSummary);

            PageResponse<BlockResponse> response = blockService.getBlockedUsers(USER_ID, 0, 20);

            assertThat(response.getContent().get(0).getReason()).isNull();
        }
    }
}