package com.billboard.social.graph.service;

import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.graph.dto.request.SocialRequests.FriendRequest;
import com.billboard.social.graph.dto.response.SocialResponses.FriendResponse;
import com.billboard.social.graph.dto.response.SocialResponses.FriendshipResponse;
import com.billboard.social.graph.entity.Friendship;
import com.billboard.social.graph.entity.enums.FriendshipStatus;
import com.billboard.social.graph.event.SocialEventPublisher;
import com.billboard.social.graph.repository.BlockRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendshipServiceTest {

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private BlockRepository blockRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private SocialEventPublisher eventPublisher;

    @InjectMocks
    private FriendshipService friendshipService;

    // Test constants
    private static final Long USER_ID = 1L;
    private static final Long FRIEND_ID = 2L;
    private static final UUID FRIENDSHIP_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private Friendship testFriendship;
    private UserSummary testUserSummary;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(friendshipService, "maxFriends", 5000);

        testFriendship = Friendship.builder()
                .id(FRIENDSHIP_ID)
                .requesterId(USER_ID)
                .addresseeId(FRIEND_ID)
                .status(FriendshipStatus.PENDING)
                .message("Let's be friends!")
                .mutualFriendsCount(5)
                .build();
        testFriendship.setCreatedAt(LocalDateTime.now());

        testUserSummary = UserSummary.builder()
                .id(FRIEND_ID)
                .username("friend")
                .email("test@gmail.com")
                .build();
    }

    // ==================== SEND FRIEND REQUEST ====================

    @Nested
    @DisplayName("sendFriendRequest")
    class SendFriendRequestTests {

        @Test
        @DisplayName("Success - sends friend request with message")
        void sendFriendRequest_SuccessWithMessage() {
            FriendRequest request = FriendRequest.builder()
                    .userId(FRIEND_ID)
                    .message("Let's be friends!")
                    .build();

            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(testUserSummary);
            when(blockRepository.isBlockedEitherWay(USER_ID, FRIEND_ID)).thenReturn(false);
            when(friendshipRepository.findBetweenUsers(USER_ID, FRIEND_ID)).thenReturn(Optional.empty());
            when(friendshipRepository.countFriends(USER_ID)).thenReturn(100L);
            when(friendshipRepository.findMutualFriendIds(USER_ID, FRIEND_ID)).thenReturn(List.of(10L));
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> {
                Friendship saved = invocation.getArgument(0);
                saved.setId(FRIENDSHIP_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });

            FriendshipResponse response = friendshipService.sendFriendRequest(USER_ID, request);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(FRIENDSHIP_ID);
            assertThat(response.getRequesterId()).isEqualTo(USER_ID);
            assertThat(response.getAddresseeId()).isEqualTo(FRIEND_ID);
            assertThat(response.getStatus()).isEqualTo(FriendshipStatus.PENDING);
            assertThat(response.getMessage()).isEqualTo("Let's be friends!");
            assertThat(response.getMutualFriendsCount()).isEqualTo(1);
            verify(eventPublisher).publishFriendRequestSent(any(Friendship.class));
        }

        @Test
        @DisplayName("Friend request already pending (isPending branch) - throws ValidationException")
        void sendFriendRequest_IsPendingBranch() {
            FriendRequest request = FriendRequest.builder()
                    .userId(FRIEND_ID)
                    .build();

            // Create friendship with PENDING status
            // isAccepted() returns false, isPending() returns true
            Friendship pendingFriendship = mock(Friendship.class);
            when(pendingFriendship.isAccepted()).thenReturn(false);  // Skip first if
            when(pendingFriendship.isPending()).thenReturn(true);    // Enter second if

            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(testUserSummary);
            when(blockRepository.isBlockedEitherWay(USER_ID, FRIEND_ID)).thenReturn(false);
            when(friendshipRepository.findBetweenUsers(USER_ID, FRIEND_ID)).thenReturn(Optional.of(pendingFriendship));

            assertThatThrownBy(() -> friendshipService.sendFriendRequest(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Friend request already pending");

            // Verify both methods were called in order
            verify(pendingFriendship).isAccepted();
            verify(pendingFriendship).isPending();
            verify(friendshipRepository, never()).save(any());
        }

        @Test
        @DisplayName("Existing friendship is declined - allows new request (isPending false branch)")
        void sendFriendRequest_ExistingDeclinedFriendship_AllowsNewRequest() {
            FriendRequest request = FriendRequest.builder()
                    .userId(FRIEND_ID)
                    .build();

            // Create friendship with DECLINED status (neither ACCEPTED nor PENDING)
            // isAccepted() returns false, isPending() returns false -> continues past both ifs
            Friendship declinedFriendship = mock(Friendship.class);
            when(declinedFriendship.isAccepted()).thenReturn(false);  // Skip first if
            when(declinedFriendship.isPending()).thenReturn(false);   // Skip second if -> continue

            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(testUserSummary);
            when(blockRepository.isBlockedEitherWay(USER_ID, FRIEND_ID)).thenReturn(false);
            when(friendshipRepository.findBetweenUsers(USER_ID, FRIEND_ID)).thenReturn(Optional.of(declinedFriendship));
            when(friendshipRepository.countFriends(USER_ID)).thenReturn(0L);
            when(friendshipRepository.findMutualFriendIds(USER_ID, FRIEND_ID)).thenReturn(Collections.emptyList());
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> {
                Friendship saved = invocation.getArgument(0);
                saved.setId(FRIENDSHIP_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });

            FriendshipResponse response = friendshipService.sendFriendRequest(USER_ID, request);

            // Verify both branches were checked and skipped
            verify(declinedFriendship).isAccepted();
            verify(declinedFriendship).isPending();

            // Verify code continued past ifPresent block
            verify(friendshipRepository).countFriends(USER_ID);
            verify(friendshipRepository).save(any(Friendship.class));

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Success - sends friend request without message")
        void sendFriendRequest_SuccessWithoutMessage() {
            FriendRequest request = FriendRequest.builder()
                    .userId(FRIEND_ID)
                    .build();

            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(testUserSummary);
            when(blockRepository.isBlockedEitherWay(USER_ID, FRIEND_ID)).thenReturn(false);
            when(friendshipRepository.findBetweenUsers(USER_ID, FRIEND_ID)).thenReturn(Optional.empty());
            when(friendshipRepository.countFriends(USER_ID)).thenReturn(0L);
            when(friendshipRepository.findMutualFriendIds(USER_ID, FRIEND_ID)).thenReturn(Collections.emptyList());
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> {
                Friendship saved = invocation.getArgument(0);
                saved.setId(FRIENDSHIP_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });

            FriendshipResponse response = friendshipService.sendFriendRequest(USER_ID, request);

            assertThat(response).isNotNull();
            assertThat(response.getMessage()).isNull();
            assertThat(response.getMutualFriendsCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("Null userId - throws ValidationException")
        void sendFriendRequest_NullUserId() {
            FriendRequest request = FriendRequest.builder().build();

            assertThatThrownBy(() -> friendshipService.sendFriendRequest(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("User ID is required");

            verifyNoInteractions(friendshipRepository);
        }

        @Test
        @DisplayName("Cannot send to yourself - throws ValidationException")
        void sendFriendRequest_CannotSendToYourself() {
            FriendRequest request = FriendRequest.builder()
                    .userId(USER_ID)
                    .build();

            assertThatThrownBy(() -> friendshipService.sendFriendRequest(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Cannot send friend request to yourself");

            verifyNoInteractions(friendshipRepository);
        }

        @Test
        @DisplayName("Blocked either way - throws ValidationException")
        void sendFriendRequest_BlockedEitherWay() {
            FriendRequest request = FriendRequest.builder()
                    .userId(FRIEND_ID)
                    .build();

            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(testUserSummary);
            when(blockRepository.isBlockedEitherWay(USER_ID, FRIEND_ID)).thenReturn(true);

            assertThatThrownBy(() -> friendshipService.sendFriendRequest(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Cannot send friend request to this user");

            verify(friendshipRepository, never()).save(any());
        }

        @Test
        @DisplayName("Already friends - throws ValidationException")
        void sendFriendRequest_AlreadyFriends() {
            FriendRequest request = FriendRequest.builder()
                    .userId(FRIEND_ID)
                    .build();

            Friendship acceptedFriendship = Friendship.builder()
                    .id(FRIENDSHIP_ID)
                    .requesterId(USER_ID)
                    .addresseeId(FRIEND_ID)
                    .status(FriendshipStatus.ACCEPTED)
                    .build();

            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(testUserSummary);
            when(blockRepository.isBlockedEitherWay(USER_ID, FRIEND_ID)).thenReturn(false);
            when(friendshipRepository.findBetweenUsers(USER_ID, FRIEND_ID)).thenReturn(Optional.of(acceptedFriendship));

            assertThatThrownBy(() -> friendshipService.sendFriendRequest(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Already friends with this user");
        }

        @Test
        @DisplayName("Friend request already pending - throws ValidationException")
        void sendFriendRequest_AlreadyPending() {
            FriendRequest request = FriendRequest.builder()
                    .userId(FRIEND_ID)
                    .build();

            // Create a PENDING friendship (not ACCEPTED)
            Friendship pendingFriendship = Friendship.builder()
                    .id(FRIENDSHIP_ID)
                    .requesterId(USER_ID)
                    .addresseeId(FRIEND_ID)
                    .status(FriendshipStatus.PENDING)  // Explicitly PENDING
                    .build();

            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(testUserSummary);
            when(blockRepository.isBlockedEitherWay(USER_ID, FRIEND_ID)).thenReturn(false);
            when(friendshipRepository.findBetweenUsers(USER_ID, FRIEND_ID)).thenReturn(Optional.of(pendingFriendship));

            assertThatThrownBy(() -> friendshipService.sendFriendRequest(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Friend request already pending");

            verify(friendshipRepository, never()).save(any());
            verify(friendshipRepository, never()).countFriends(any());  // Should not reach this check
        }

        @Test
        @DisplayName("Max friends limit reached - throws ValidationException")
        void sendFriendRequest_MaxLimitReached() {
            FriendRequest request = FriendRequest.builder()
                    .userId(FRIEND_ID)
                    .build();

            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(testUserSummary);
            when(blockRepository.isBlockedEitherWay(USER_ID, FRIEND_ID)).thenReturn(false);
            when(friendshipRepository.findBetweenUsers(USER_ID, FRIEND_ID)).thenReturn(Optional.empty());
            when(friendshipRepository.countFriends(USER_ID)).thenReturn(5000L);

            assertThatThrownBy(() -> friendshipService.sendFriendRequest(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Maximum friends limit reached");
        }

        @Test
        @DisplayName("At boundary - one below max limit succeeds")
        void sendFriendRequest_OneBelowMaxLimit() {
            FriendRequest request = FriendRequest.builder()
                    .userId(FRIEND_ID)
                    .build();

            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(testUserSummary);
            when(blockRepository.isBlockedEitherWay(USER_ID, FRIEND_ID)).thenReturn(false);
            when(friendshipRepository.findBetweenUsers(USER_ID, FRIEND_ID)).thenReturn(Optional.empty());
            when(friendshipRepository.countFriends(USER_ID)).thenReturn(4999L);
            when(friendshipRepository.findMutualFriendIds(USER_ID, FRIEND_ID)).thenReturn(Collections.emptyList());
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> {
                Friendship saved = invocation.getArgument(0);
                saved.setId(FRIENDSHIP_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });

            FriendshipResponse response = friendshipService.sendFriendRequest(USER_ID, request);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Race condition - DataIntegrityViolationException")
        void sendFriendRequest_RaceCondition() {
            FriendRequest request = FriendRequest.builder()
                    .userId(FRIEND_ID)
                    .build();

            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(testUserSummary);
            when(blockRepository.isBlockedEitherWay(USER_ID, FRIEND_ID)).thenReturn(false);
            when(friendshipRepository.findBetweenUsers(USER_ID, FRIEND_ID)).thenReturn(Optional.empty());
            when(friendshipRepository.countFriends(USER_ID)).thenReturn(0L);
            when(friendshipRepository.findMutualFriendIds(USER_ID, FRIEND_ID)).thenReturn(Collections.emptyList());
            when(friendshipRepository.save(any(Friendship.class)))
                    .thenThrow(new DataIntegrityViolationException("Duplicate key"));

            assertThatThrownBy(() -> friendshipService.sendFriendRequest(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Friend request already exists or is pending");

            verify(eventPublisher, never()).publishFriendRequestSent(any());
        }
    }

    // ==================== VALIDATE USER EXISTS ====================

    @Nested
    @DisplayName("validateUserExists (via sendFriendRequest)")
    class ValidateUserExistsTests {

        @Test
        @DisplayName("User exists - validation passes")
        void validateUserExists_Success() {
            FriendRequest request = FriendRequest.builder()
                    .userId(FRIEND_ID)
                    .build();

            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(testUserSummary);
            when(blockRepository.isBlockedEitherWay(USER_ID, FRIEND_ID)).thenReturn(false);
            when(friendshipRepository.findBetweenUsers(USER_ID, FRIEND_ID)).thenReturn(Optional.empty());
            when(friendshipRepository.countFriends(USER_ID)).thenReturn(0L);
            when(friendshipRepository.findMutualFriendIds(USER_ID, FRIEND_ID)).thenReturn(Collections.emptyList());
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> {
                Friendship saved = invocation.getArgument(0);
                saved.setId(FRIENDSHIP_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });

            FriendshipResponse response = friendshipService.sendFriendRequest(USER_ID, request);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("User summary returns null - throws ValidationException")
        void validateUserExists_ReturnsNull() {
            FriendRequest request = FriendRequest.builder()
                    .userId(FRIEND_ID)
                    .build();

            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(null);

            assertThatThrownBy(() -> friendshipService.sendFriendRequest(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("User not found with id: " + FRIEND_ID);
        }

        @Test
        @DisplayName("FeignException.NotFound - throws ValidationException")
        void validateUserExists_FeignNotFound() {
            FriendRequest request = FriendRequest.builder()
                    .userId(FRIEND_ID)
                    .build();

            Request feignRequest = Request.create(Request.HttpMethod.GET, "/users",
                    Collections.emptyMap(), null, new RequestTemplate());
            when(userServiceClient.getUserSummary(FRIEND_ID))
                    .thenThrow(new FeignException.NotFound("Not found", feignRequest, null, null));

            assertThatThrownBy(() -> friendshipService.sendFriendRequest(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("User not found with id: " + FRIEND_ID);
        }

        @Test
        @DisplayName("FeignException (other) - throws ValidationException with generic message")
        void validateUserExists_FeignOtherException() {
            FriendRequest request = FriendRequest.builder()
                    .userId(FRIEND_ID)
                    .build();

            Request feignRequest = Request.create(Request.HttpMethod.GET, "/users",
                    Collections.emptyMap(), null, new RequestTemplate());
            when(userServiceClient.getUserSummary(FRIEND_ID))
                    .thenThrow(new FeignException.ServiceUnavailable("Service unavailable", feignRequest, null, null));

            assertThatThrownBy(() -> friendshipService.sendFriendRequest(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Unable to verify user. Please try again later.");
        }

        @Test
        @DisplayName("Generic Exception - throws ValidationException with generic message")
        void validateUserExists_GenericException() {
            FriendRequest request = FriendRequest.builder()
                    .userId(FRIEND_ID)
                    .build();

            when(userServiceClient.getUserSummary(FRIEND_ID))
                    .thenThrow(new RuntimeException("Connection failed"));

            assertThatThrownBy(() -> friendshipService.sendFriendRequest(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Unable to verify user. Please try again later.");
        }
    }

    // ==================== ACCEPT FRIEND REQUEST ====================

    @Nested
    @DisplayName("acceptFriendRequest")
    class AcceptFriendRequestTests {

        @Test
        @DisplayName("Success - accepts friend request")
        void acceptFriendRequest_Success() {
            // USER_ID is the addressee
            testFriendship.setRequesterId(FRIEND_ID);
            testFriendship.setAddresseeId(USER_ID);

            when(friendshipRepository.findById(FRIENDSHIP_ID)).thenReturn(Optional.of(testFriendship));
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> {
                Friendship saved = invocation.getArgument(0);
                return saved;
            });

            FriendshipResponse response = friendshipService.acceptFriendRequest(USER_ID, FRIENDSHIP_ID);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
            assertThat(response.getAcceptedAt()).isNotNull();
            verify(eventPublisher).publishFriendRequestAccepted(any(Friendship.class));
        }

        @Test
        @DisplayName("Not the addressee - throws ValidationException")
        void acceptFriendRequest_NotTheAddressee() {
            // USER_ID is the requester, not addressee
            testFriendship.setRequesterId(USER_ID);
            testFriendship.setAddresseeId(FRIEND_ID);

            when(friendshipRepository.findById(FRIENDSHIP_ID)).thenReturn(Optional.of(testFriendship));

            assertThatThrownBy(() -> friendshipService.acceptFriendRequest(USER_ID, FRIENDSHIP_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Only the addressee can accept this request");

            verify(friendshipRepository, never()).save(any());
            verify(eventPublisher, never()).publishFriendRequestAccepted(any());
        }

        @Test
        @DisplayName("Request not pending - throws ValidationException")
        void acceptFriendRequest_RequestNotPending() {
            testFriendship.setRequesterId(FRIEND_ID);
            testFriendship.setAddresseeId(USER_ID);
            testFriendship.setStatus(FriendshipStatus.ACCEPTED);

            when(friendshipRepository.findById(FRIENDSHIP_ID)).thenReturn(Optional.of(testFriendship));

            assertThatThrownBy(() -> friendshipService.acceptFriendRequest(USER_ID, FRIENDSHIP_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Friend request is not pending");
        }

        @Test
        @DisplayName("Friendship not found - throws ValidationException")
        void acceptFriendRequest_FriendshipNotFound() {
            when(friendshipRepository.findById(FRIENDSHIP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> friendshipService.acceptFriendRequest(USER_ID, FRIENDSHIP_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Friendship not found with id: " + FRIENDSHIP_ID);
        }
    }

    // ==================== DECLINE FRIEND REQUEST ====================

    @Nested
    @DisplayName("declineFriendRequest")
    class DeclineFriendRequestTests {

        @Test
        @DisplayName("Success - declines friend request")
        void declineFriendRequest_Success() {
            testFriendship.setRequesterId(FRIEND_ID);
            testFriendship.setAddresseeId(USER_ID);

            when(friendshipRepository.findById(FRIENDSHIP_ID)).thenReturn(Optional.of(testFriendship));
            doNothing().when(friendshipRepository).delete(testFriendship);

            friendshipService.declineFriendRequest(USER_ID, FRIENDSHIP_ID);

            verify(friendshipRepository).delete(testFriendship);
        }

        @Test
        @DisplayName("Not the addressee - throws ValidationException")
        void declineFriendRequest_NotTheAddressee() {
            testFriendship.setRequesterId(USER_ID);
            testFriendship.setAddresseeId(FRIEND_ID);

            when(friendshipRepository.findById(FRIENDSHIP_ID)).thenReturn(Optional.of(testFriendship));

            assertThatThrownBy(() -> friendshipService.declineFriendRequest(USER_ID, FRIENDSHIP_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Only the addressee can decline this request");

            verify(friendshipRepository, never()).delete(any(Friendship.class));
        }

        @Test
        @DisplayName("Request not pending - throws ValidationException")
        void declineFriendRequest_RequestNotPending() {
            testFriendship.setRequesterId(FRIEND_ID);
            testFriendship.setAddresseeId(USER_ID);
            testFriendship.setStatus(FriendshipStatus.ACCEPTED);

            when(friendshipRepository.findById(FRIENDSHIP_ID)).thenReturn(Optional.of(testFriendship));

            assertThatThrownBy(() -> friendshipService.declineFriendRequest(USER_ID, FRIENDSHIP_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Friend request is not pending");
        }

        @Test
        @DisplayName("Friendship not found - throws ValidationException")
        void declineFriendRequest_FriendshipNotFound() {
            when(friendshipRepository.findById(FRIENDSHIP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> friendshipService.declineFriendRequest(USER_ID, FRIENDSHIP_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Friendship not found with id: " + FRIENDSHIP_ID);
        }
    }

    // ==================== CANCEL FRIEND REQUEST ====================

    @Nested
    @DisplayName("cancelFriendRequest")
    class CancelFriendRequestTests {

        @Test
        @DisplayName("Success - cancels friend request")
        void cancelFriendRequest_Success() {
            testFriendship.setRequesterId(USER_ID);
            testFriendship.setAddresseeId(FRIEND_ID);

            when(friendshipRepository.findById(FRIENDSHIP_ID)).thenReturn(Optional.of(testFriendship));
            doNothing().when(friendshipRepository).delete(testFriendship);

            friendshipService.cancelFriendRequest(USER_ID, FRIENDSHIP_ID);

            verify(friendshipRepository).delete(testFriendship);
        }

        @Test
        @DisplayName("Not the requester - throws ValidationException")
        void cancelFriendRequest_NotTheRequester() {
            testFriendship.setRequesterId(FRIEND_ID);
            testFriendship.setAddresseeId(USER_ID);

            when(friendshipRepository.findById(FRIENDSHIP_ID)).thenReturn(Optional.of(testFriendship));

            assertThatThrownBy(() -> friendshipService.cancelFriendRequest(USER_ID, FRIENDSHIP_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Only the requester can cancel this request");

            verify(friendshipRepository, never()).delete(any(Friendship.class));
        }

        @Test
        @DisplayName("Request not pending - throws ValidationException")
        void cancelFriendRequest_RequestNotPending() {
            testFriendship.setRequesterId(USER_ID);
            testFriendship.setAddresseeId(FRIEND_ID);
            testFriendship.setStatus(FriendshipStatus.ACCEPTED);

            when(friendshipRepository.findById(FRIENDSHIP_ID)).thenReturn(Optional.of(testFriendship));

            assertThatThrownBy(() -> friendshipService.cancelFriendRequest(USER_ID, FRIENDSHIP_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Friend request is not pending");
        }

        @Test
        @DisplayName("Friendship not found - throws ValidationException")
        void cancelFriendRequest_FriendshipNotFound() {
            when(friendshipRepository.findById(FRIENDSHIP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> friendshipService.cancelFriendRequest(USER_ID, FRIENDSHIP_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Friendship not found with id: " + FRIENDSHIP_ID);
        }
    }

    // ==================== UNFRIEND ====================

    @Nested
    @DisplayName("unfriend")
    class UnfriendTests {

        @Test
        @DisplayName("Success - unfriends user")
        void unfriend_Success() {
            Friendship acceptedFriendship = Friendship.builder()
                    .id(FRIENDSHIP_ID)
                    .requesterId(USER_ID)
                    .addresseeId(FRIEND_ID)
                    .status(FriendshipStatus.ACCEPTED)
                    .build();

            when(friendshipRepository.findBetweenUsers(USER_ID, FRIEND_ID))
                    .thenReturn(Optional.of(acceptedFriendship));
            doNothing().when(friendshipRepository).delete(acceptedFriendship);

            friendshipService.unfriend(USER_ID, FRIEND_ID);

            verify(friendshipRepository).delete(acceptedFriendship);
            verify(eventPublisher).publishUnfriended(USER_ID, FRIEND_ID);
        }

        @Test
        @DisplayName("Friendship not found - throws ValidationException")
        void unfriend_FriendshipNotFound() {
            when(friendshipRepository.findBetweenUsers(USER_ID, FRIEND_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> friendshipService.unfriend(USER_ID, FRIEND_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Friendship not found");

            verify(friendshipRepository, never()).delete(any(Friendship.class));
        }

        @Test
        @DisplayName("Not friends (pending) - throws ValidationException")
        void unfriend_NotFriends() {
            testFriendship.setStatus(FriendshipStatus.PENDING);

            when(friendshipRepository.findBetweenUsers(USER_ID, FRIEND_ID))
                    .thenReturn(Optional.of(testFriendship));

            assertThatThrownBy(() -> friendshipService.unfriend(USER_ID, FRIEND_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Not friends with this user");
        }
    }

    // ==================== GET FRIENDS ====================

    @Nested
    @DisplayName("getFriends")
    class GetFriendsTests {

        @Test
        @DisplayName("Success - returns paginated friends (user is requester)")
        void getFriends_UserIsRequester() {
            Friendship friendship = Friendship.builder()
                    .id(FRIENDSHIP_ID)
                    .requesterId(USER_ID)
                    .addresseeId(FRIEND_ID)
                    .status(FriendshipStatus.ACCEPTED)
                    .mutualFriendsCount(3)
                    .acceptedAt(LocalDateTime.now())
                    .build();

            Page<Friendship> page = new PageImpl<>(
                    List.of(friendship),
                    PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "acceptedAt")),
                    1
            );

            when(friendshipRepository.findAcceptedFriendships(eq(USER_ID), any(Pageable.class))).thenReturn(page);
            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(testUserSummary);

            PageResponse<FriendResponse> response = friendshipService.getFriends(USER_ID, 0, 20);

            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getContent().get(0).getFriendId()).isEqualTo(FRIEND_ID);
            assertThat(response.getContent().get(0).getUsername()).isEqualTo("friend");
        }

        @Test
        @DisplayName("Success - returns paginated friends (user is addressee)")
        void getFriends_UserIsAddressee() {
            Friendship friendship = Friendship.builder()
                    .id(FRIENDSHIP_ID)
                    .requesterId(FRIEND_ID)
                    .addresseeId(USER_ID)
                    .status(FriendshipStatus.ACCEPTED)
                    .mutualFriendsCount(3)
                    .acceptedAt(LocalDateTime.now())
                    .build();

            Page<Friendship> page = new PageImpl<>(
                    List.of(friendship),
                    PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "acceptedAt")),
                    1
            );

            when(friendshipRepository.findAcceptedFriendships(eq(USER_ID), any(Pageable.class))).thenReturn(page);
            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(testUserSummary);

            PageResponse<FriendResponse> response = friendshipService.getFriends(USER_ID, 0, 20);

            assertThat(response.getContent()).hasSize(1);
            // Friend should be the requester since user is addressee
            assertThat(response.getContent().get(0).getFriendId()).isEqualTo(FRIEND_ID);
        }

        @Test
        @DisplayName("Success - empty list")
        void getFriends_Empty() {
            Page<Friendship> emptyPage = new PageImpl<>(
                    Collections.emptyList(),
                    PageRequest.of(0, 20),
                    0
            );

            when(friendshipRepository.findAcceptedFriendships(eq(USER_ID), any(Pageable.class))).thenReturn(emptyPage);

            PageResponse<FriendResponse> response = friendshipService.getFriends(USER_ID, 0, 20);

            assertThat(response.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Success - custom pagination")
        void getFriends_CustomPagination() {
            Page<Friendship> page = new PageImpl<>(
                    Collections.emptyList(),
                    PageRequest.of(5, 50),
                    0
            );

            when(friendshipRepository.findAcceptedFriendships(eq(USER_ID), any(Pageable.class))).thenReturn(page);

            PageResponse<FriendResponse> response = friendshipService.getFriends(USER_ID, 5, 50);

            assertThat(response.getPage()).isEqualTo(5);
            assertThat(response.getSize()).isEqualTo(50);
        }
    }

    // ==================== GET PENDING REQUESTS ====================

    @Nested
    @DisplayName("getPendingRequests")
    class GetPendingRequestsTests {

        @Test
        @DisplayName("Success - returns paginated pending requests")
        void getPendingRequests_Success() {
            Page<Friendship> page = new PageImpl<>(
                    List.of(testFriendship),
                    PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")),
                    1
            );

            when(friendshipRepository.findPendingRequests(eq(USER_ID), any(Pageable.class))).thenReturn(page);

            PageResponse<FriendshipResponse> response = friendshipService.getPendingRequests(USER_ID, 0, 20);

            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getContent().get(0).getStatus()).isEqualTo(FriendshipStatus.PENDING);
        }

        @Test
        @DisplayName("Success - empty list")
        void getPendingRequests_Empty() {
            Page<Friendship> emptyPage = new PageImpl<>(
                    Collections.emptyList(),
                    PageRequest.of(0, 20),
                    0
            );

            when(friendshipRepository.findPendingRequests(eq(USER_ID), any(Pageable.class))).thenReturn(emptyPage);

            PageResponse<FriendshipResponse> response = friendshipService.getPendingRequests(USER_ID, 0, 20);

            assertThat(response.getContent()).isEmpty();
        }
    }

    // ==================== GET SENT REQUESTS ====================

    @Nested
    @DisplayName("getSentRequests")
    class GetSentRequestsTests {

        @Test
        @DisplayName("Success - returns paginated sent requests")
        void getSentRequests_Success() {
            Page<Friendship> page = new PageImpl<>(
                    List.of(testFriendship),
                    PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")),
                    1
            );

            when(friendshipRepository.findSentRequests(eq(USER_ID), any(Pageable.class))).thenReturn(page);

            PageResponse<FriendshipResponse> response = friendshipService.getSentRequests(USER_ID, 0, 20);

            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getContent().get(0).getRequesterId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("Success - empty list")
        void getSentRequests_Empty() {
            Page<Friendship> emptyPage = new PageImpl<>(
                    Collections.emptyList(),
                    PageRequest.of(0, 20),
                    0
            );

            when(friendshipRepository.findSentRequests(eq(USER_ID), any(Pageable.class))).thenReturn(emptyPage);

            PageResponse<FriendshipResponse> response = friendshipService.getSentRequests(USER_ID, 0, 20);

            assertThat(response.getContent()).isEmpty();
        }
    }

    // ==================== GET FRIEND IDS ====================

    @Nested
    @DisplayName("getFriendIds")
    class GetFriendIdsTests {

        @Test
        @DisplayName("Success - returns friend IDs")
        void getFriendIds_Success() {
            Long friend2 = 10L;
            List<Long> ids = List.of(FRIEND_ID, friend2);

            when(friendshipRepository.findFriendIds(USER_ID)).thenReturn(ids);

            List<Long> result = friendshipService.getFriendIds(USER_ID);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(FRIEND_ID, friend2);
        }

        @Test
        @DisplayName("Success - empty list")
        void getFriendIds_Empty() {
            when(friendshipRepository.findFriendIds(USER_ID)).thenReturn(Collections.emptyList());

            List<Long> result = friendshipService.getFriendIds(USER_ID);

            assertThat(result).isEmpty();
        }
    }

    // ==================== GET MUTUAL FRIEND IDS ====================

    @Nested
    @DisplayName("getMutualFriendIds")
    class GetMutualFriendIdsTests {

        @Test
        @DisplayName("Success - returns mutual friend IDs")
        void getMutualFriendIds_Success() {
            Long mutual1 = 10L;
            Long mutual2 = 11L;
            List<Long> mutualIds = List.of(mutual1, mutual2);

            when(friendshipRepository.findMutualFriendIds(USER_ID, FRIEND_ID)).thenReturn(mutualIds);

            List<Long> result = friendshipService.getMutualFriendIds(USER_ID, FRIEND_ID);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("Success - empty list (no mutual friends)")
        void getMutualFriendIds_Empty() {
            when(friendshipRepository.findMutualFriendIds(USER_ID, FRIEND_ID)).thenReturn(Collections.emptyList());

            List<Long> result = friendshipService.getMutualFriendIds(USER_ID, FRIEND_ID);

            assertThat(result).isEmpty();
        }
    }

    // ==================== ARE FRIENDS ====================

    @Nested
    @DisplayName("areFriends")
    class AreFriendsTests {

        @Test
        @DisplayName("Returns true when friends")
        void areFriends_ReturnsTrue() {
            when(friendshipRepository.areFriends(USER_ID, FRIEND_ID)).thenReturn(true);

            boolean result = friendshipService.areFriends(USER_ID, FRIEND_ID);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Returns false when not friends")
        void areFriends_ReturnsFalse() {
            when(friendshipRepository.areFriends(USER_ID, FRIEND_ID)).thenReturn(false);

            boolean result = friendshipService.areFriends(USER_ID, FRIEND_ID);

            assertThat(result).isFalse();
        }
    }

    // ==================== GET FRIENDS COUNT ====================

    @Nested
    @DisplayName("getFriendsCount")
    class GetFriendsCountTests {

        @Test
        @DisplayName("Success - returns count")
        void getFriendsCount_Success() {
            when(friendshipRepository.countFriends(USER_ID)).thenReturn(42L);

            long result = friendshipService.getFriendsCount(USER_ID);

            assertThat(result).isEqualTo(42L);
        }

        @Test
        @DisplayName("Success - returns zero")
        void getFriendsCount_Zero() {
            when(friendshipRepository.countFriends(USER_ID)).thenReturn(0L);

            long result = friendshipService.getFriendsCount(USER_ID);

            assertThat(result).isEqualTo(0L);
        }
    }

    // ==================== FETCH USER SUMMARY WITH FALLBACK ====================

    @Nested
    @DisplayName("fetchUserSummaryWithFallback (via getFriends)")
    class FetchUserSummaryWithFallbackTests {

        private void setupFriendsPage() {
            Friendship friendship = Friendship.builder()
                    .id(FRIENDSHIP_ID)
                    .requesterId(USER_ID)
                    .addresseeId(FRIEND_ID)
                    .status(FriendshipStatus.ACCEPTED)
                    .acceptedAt(LocalDateTime.now())
                    .build();

            Page<Friendship> page = new PageImpl<>(List.of(friendship), PageRequest.of(0, 20), 1);
            when(friendshipRepository.findAcceptedFriendships(eq(USER_ID), any(Pageable.class))).thenReturn(page);
        }

        @Test
        @DisplayName("Success - returns user summary")
        void fetchUserSummaryWithFallback_Success() {
            setupFriendsPage();
            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(testUserSummary);

            PageResponse<FriendResponse> response = friendshipService.getFriends(USER_ID, 0, 20);

            assertThat(response.getContent().get(0).getUsername()).isEqualTo("friend");
        }

        @Test
        @DisplayName("Returns null - uses fallback")
        void fetchUserSummaryWithFallback_ReturnsNull() {
            setupFriendsPage();
            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(null);

            PageResponse<FriendResponse> response = friendshipService.getFriends(USER_ID, 0, 20);

            assertThat(response.getContent().get(0).getFriendId()).isEqualTo(FRIEND_ID);
            assertThat(response.getContent().get(0).getUsername()).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("FeignException.NotFound - uses fallback")
        void fetchUserSummaryWithFallback_FeignNotFound() {
            setupFriendsPage();
            Request feignRequest = Request.create(Request.HttpMethod.GET, "/users",
                    Collections.emptyMap(), null, new RequestTemplate());
            when(userServiceClient.getUserSummary(FRIEND_ID))
                    .thenThrow(new FeignException.NotFound("Not found", feignRequest, null, null));

            PageResponse<FriendResponse> response = friendshipService.getFriends(USER_ID, 0, 20);

            assertThat(response.getContent().get(0).getUsername()).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("FeignException (other) - uses fallback")
        void fetchUserSummaryWithFallback_FeignOther() {
            setupFriendsPage();
            Request feignRequest = Request.create(Request.HttpMethod.GET, "/users",
                    Collections.emptyMap(), null, new RequestTemplate());
            when(userServiceClient.getUserSummary(FRIEND_ID))
                    .thenThrow(new FeignException.ServiceUnavailable("Service unavailable", feignRequest, null, null));

            PageResponse<FriendResponse> response = friendshipService.getFriends(USER_ID, 0, 20);

            assertThat(response.getContent().get(0).getUsername()).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("Generic Exception - uses fallback")
        void fetchUserSummaryWithFallback_GenericException() {
            setupFriendsPage();
            when(userServiceClient.getUserSummary(FRIEND_ID))
                    .thenThrow(new RuntimeException("Connection failed"));

            PageResponse<FriendResponse> response = friendshipService.getFriends(USER_ID, 0, 20);

            assertThat(response.getContent().get(0).getUsername()).isEqualTo("Unknown");
        }
    }

    // ==================== MAP TO FRIENDSHIP RESPONSE ====================

    @Nested
    @DisplayName("mapToFriendshipResponse")
    class MapToFriendshipResponseTests {

        @Test
        @DisplayName("Maps all fields correctly")
        void mapToFriendshipResponse_AllFields() {
            testFriendship.setRequesterId(FRIEND_ID);
            testFriendship.setAddresseeId(USER_ID);

            when(friendshipRepository.findById(FRIENDSHIP_ID)).thenReturn(Optional.of(testFriendship));
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> invocation.getArgument(0));

            FriendshipResponse response = friendshipService.acceptFriendRequest(USER_ID, FRIENDSHIP_ID);

            assertThat(response.getId()).isEqualTo(FRIENDSHIP_ID);
            assertThat(response.getRequesterId()).isEqualTo(FRIEND_ID);
            assertThat(response.getAddresseeId()).isEqualTo(USER_ID);
            assertThat(response.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
            assertThat(response.getMessage()).isEqualTo("Let's be friends!");
            assertThat(response.getMutualFriendsCount()).isEqualTo(5);
            assertThat(response.getAcceptedAt()).isNotNull();
            assertThat(response.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Maps with null message")
        void mapToFriendshipResponse_NullMessage() {
            testFriendship.setRequesterId(FRIEND_ID);
            testFriendship.setAddresseeId(USER_ID);
            testFriendship.setMessage(null);

            when(friendshipRepository.findById(FRIENDSHIP_ID)).thenReturn(Optional.of(testFriendship));
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> invocation.getArgument(0));

            FriendshipResponse response = friendshipService.acceptFriendRequest(USER_ID, FRIENDSHIP_ID);

            assertThat(response.getMessage()).isNull();
        }
    }

    // ==================== MAP TO FRIEND RESPONSE ====================

    @Nested
    @DisplayName("mapToFriendResponse")
    class MapToFriendResponseTests {

        @Test
        @DisplayName("Maps all fields correctly")
        void mapToFriendResponse_AllFields() {
            Friendship friendship = Friendship.builder()
                    .id(FRIENDSHIP_ID)
                    .requesterId(USER_ID)
                    .addresseeId(FRIEND_ID)
                    .status(FriendshipStatus.ACCEPTED)
                    .mutualFriendsCount(10)
                    .acceptedAt(LocalDateTime.now())
                    .build();

            Page<Friendship> page = new PageImpl<>(List.of(friendship), PageRequest.of(0, 20), 1);
            when(friendshipRepository.findAcceptedFriendships(eq(USER_ID), any(Pageable.class))).thenReturn(page);
            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(testUserSummary);

            PageResponse<FriendResponse> response = friendshipService.getFriends(USER_ID, 0, 20);

            FriendResponse friendResponse = response.getContent().get(0);
            assertThat(friendResponse.getFriendId()).isEqualTo(FRIEND_ID);
            assertThat(friendResponse.getUsername()).isEqualTo("friend");
            assertThat(friendResponse.getMutualFriendsCount()).isEqualTo(10);
            assertThat(friendResponse.getFriendsSince()).isNotNull();
        }
    }
}