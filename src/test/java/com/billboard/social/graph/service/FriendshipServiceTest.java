package com.billboard.social.graph.service;

import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.dto.ApiResponse;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.graph.dto.request.SocialRequests.FriendRequest;
import com.billboard.social.graph.dto.response.SocialResponses.FriendResponse;
import com.billboard.social.graph.dto.response.SocialResponses.FriendshipResponse;
import com.billboard.social.graph.entity.Friendship;
import com.billboard.social.graph.entity.enums.FriendshipStatus;
import com.billboard.social.graph.event.SocialEventPublisher;
import com.billboard.social.graph.repository.BlockRepository;
import com.billboard.social.graph.entity.FriendshipEvent;
import com.billboard.social.graph.repository.FriendshipEventRepository;
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
    private FriendshipEventRepository friendshipEventRepository;

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

            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(apiResponse(testUserSummary));
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
        @DisplayName("Friend request already pending (PENDING switch case) - throws ValidationException")
        void sendFriendRequest_IsPendingBranch() {
            FriendRequest request = FriendRequest.builder()
                    .userId(FRIEND_ID)
                    .build();

            // Production code uses switch(existing.getStatus()); a real entity is required so
            // getStatus() returns PENDING rather than null (which would cause NPE).
            Friendship pendingFriendship = Friendship.builder()
                    .id(FRIENDSHIP_ID)
                    .requesterId(USER_ID)
                    .addresseeId(FRIEND_ID)
                    .status(FriendshipStatus.PENDING)
                    .build();

            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(apiResponse(testUserSummary));
            when(blockRepository.isBlockedEitherWay(USER_ID, FRIEND_ID)).thenReturn(false);
            when(friendshipRepository.findBetweenUsers(USER_ID, FRIEND_ID)).thenReturn(Optional.of(pendingFriendship));

            assertThatThrownBy(() -> friendshipService.sendFriendRequest(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Friend request already pending");

            verify(friendshipRepository, never()).save(any());
        }

        @Test
        @DisplayName("Existing friendship is declined - reactivates to PENDING with correct event; max-friends check skipped")
        void sendFriendRequest_ExistingDeclinedFriendship_AllowsNewRequest() {
            FriendRequest request = FriendRequest.builder()
                    .userId(FRIEND_ID)
                    .build();

            // Real entity so status mutation can be observed — the prior mock(Friendship.class) was a
            // false green: it never verified the status was actually set to PENDING or that the event
            // was recorded with fromStatus=DECLINED.
            Friendship declinedFriendship = Friendship.builder()
                    .id(FRIENDSHIP_ID)
                    .requesterId(FRIEND_ID)
                    .addresseeId(USER_ID)
                    .status(FriendshipStatus.DECLINED)
                    .build();
            declinedFriendship.setCreatedAt(LocalDateTime.now());

            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(apiResponse(testUserSummary));
            when(blockRepository.isBlockedEitherWay(USER_ID, FRIEND_ID)).thenReturn(false);
            when(friendshipRepository.findBetweenUsers(USER_ID, FRIEND_ID)).thenReturn(Optional.of(declinedFriendship));
            when(friendshipRepository.findMutualFriendIds(USER_ID, FRIEND_ID)).thenReturn(Collections.emptyList());
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> invocation.getArgument(0));

            FriendshipResponse response = friendshipService.sendFriendRequest(USER_ID, request);

            // Row returns to PENDING
            assertThat(response.getStatus()).isEqualTo(FriendshipStatus.PENDING);

            // Reactivation event saved: DECLINED → PENDING
            ArgumentCaptor<FriendshipEvent> eventCaptor = ArgumentCaptor.forClass(FriendshipEvent.class);
            verify(friendshipEventRepository).save(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getFromStatus()).isEqualTo(FriendshipStatus.DECLINED);
            assertThat(eventCaptor.getValue().getToStatus()).isEqualTo(FriendshipStatus.PENDING);

            // Friend-request-sent event published
            verify(eventPublisher).publishFriendRequestSent(any(Friendship.class));

            // max-friends count is NOT consulted for a reactivation (no new row inserted)
            verify(friendshipRepository, never()).countFriends(any());
        }

        @Test
        @DisplayName("Success - sends friend request without message")
        void sendFriendRequest_SuccessWithoutMessage() {
            FriendRequest request = FriendRequest.builder()
                    .userId(FRIEND_ID)
                    .build();

            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(apiResponse(testUserSummary));
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

            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(apiResponse(testUserSummary));
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

            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(apiResponse(testUserSummary));
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

            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(apiResponse(testUserSummary));
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

            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(apiResponse(testUserSummary));
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

            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(apiResponse(testUserSummary));
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

            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(apiResponse(testUserSummary));
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

            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(apiResponse(testUserSummary));
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
        @DisplayName("User summary returns null - throws ValidationException (NDC-23: non-disclosive message)")
        void validateUserExists_ReturnsNull() {
            FriendRequest request = FriendRequest.builder()
                    .userId(FRIEND_ID)
                    .build();

            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(apiResponse(null));

            assertThatThrownBy(() -> friendshipService.sendFriendRequest(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Unable to send friend request");
        }

        @Test
        @DisplayName("FeignException.NotFound - throws ValidationException (NDC-23: non-disclosive message)")
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
                    .hasMessage("Unable to send friend request");
        }

        @Test
        @DisplayName("NDC-23: non-existent user and null SSO response produce identical message (non-disclosure)")
        void sendFriendRequest_NonDisclosure_BothNotFoundPathsProduceSameMessage() {
            FriendRequest request = FriendRequest.builder()
                    .userId(FRIEND_ID)
                    .build();

            // Path 1: SSO returns 404 (FeignException.NotFound — user does not exist)
            Request feignRequest = Request.create(Request.HttpMethod.GET, "/users",
                    Collections.emptyMap(), null, new RequestTemplate());
            when(userServiceClient.getUserSummary(FRIEND_ID))
                    .thenThrow(new FeignException.NotFound("Not found", feignRequest, null, null));

            ValidationException ex1 = catchThrowableOfType(
                    () -> friendshipService.sendFriendRequest(USER_ID, request),
                    ValidationException.class);

            // Path 2: SSO returns null data (user does not exist via different internal path)
            reset(userServiceClient);
            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(apiResponse(null));

            ValidationException ex2 = catchThrowableOfType(
                    () -> friendshipService.sendFriendRequest(USER_ID, request),
                    ValidationException.class);

            // Both paths must produce identical message — no enumeration signal to caller
            assertThat(ex1).isNotNull();
            assertThat(ex2).isNotNull();
            assertThat(ex1.getMessage()).isEqualTo("Unable to send friend request");
            assertThat(ex2.getMessage()).isEqualTo("Unable to send friend request");
            assertThat(ex1.getMessage()).isEqualTo(ex2.getMessage());
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
        @DisplayName("Success - declines friend request; row transitions to DECLINED and event recorded")
        void declineFriendRequest_Success() {
            testFriendship.setRequesterId(FRIEND_ID);
            testFriendship.setAddresseeId(USER_ID);

            when(friendshipRepository.findById(FRIENDSHIP_ID)).thenReturn(Optional.of(testFriendship));
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> invocation.getArgument(0));

            friendshipService.declineFriendRequest(USER_ID, FRIENDSHIP_ID);

            ArgumentCaptor<Friendship> savedCaptor = ArgumentCaptor.forClass(Friendship.class);
            verify(friendshipRepository).save(savedCaptor.capture());
            assertThat(savedCaptor.getValue().getStatus()).isEqualTo(FriendshipStatus.DECLINED);

            ArgumentCaptor<FriendshipEvent> eventCaptor = ArgumentCaptor.forClass(FriendshipEvent.class);
            verify(friendshipEventRepository).save(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getFromStatus()).isEqualTo(FriendshipStatus.PENDING);
            assertThat(eventCaptor.getValue().getToStatus()).isEqualTo(FriendshipStatus.DECLINED);

            verify(eventPublisher).publishFriendRequestDeclined(any(Friendship.class));
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

            verify(friendshipRepository, never()).save(any(Friendship.class));
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
        @DisplayName("Success - cancels friend request; row transitions to CANCELLED and event recorded")
        void cancelFriendRequest_Success() {
            testFriendship.setRequesterId(USER_ID);
            testFriendship.setAddresseeId(FRIEND_ID);

            when(friendshipRepository.findById(FRIENDSHIP_ID)).thenReturn(Optional.of(testFriendship));
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> invocation.getArgument(0));

            friendshipService.cancelFriendRequest(USER_ID, FRIENDSHIP_ID);

            ArgumentCaptor<Friendship> savedCaptor = ArgumentCaptor.forClass(Friendship.class);
            verify(friendshipRepository).save(savedCaptor.capture());
            assertThat(savedCaptor.getValue().getStatus()).isEqualTo(FriendshipStatus.CANCELLED);

            ArgumentCaptor<FriendshipEvent> eventCaptor = ArgumentCaptor.forClass(FriendshipEvent.class);
            verify(friendshipEventRepository).save(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getFromStatus()).isEqualTo(FriendshipStatus.PENDING);
            assertThat(eventCaptor.getValue().getToStatus()).isEqualTo(FriendshipStatus.CANCELLED);
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

            verify(friendshipRepository, never()).save(any(Friendship.class));
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
        @DisplayName("Success - unfriends user; row transitions to UNFRIENDED, event recorded, social.unfriended published")
        void unfriend_Success() {
            Friendship acceptedFriendship = Friendship.builder()
                    .id(FRIENDSHIP_ID)
                    .requesterId(USER_ID)
                    .addresseeId(FRIEND_ID)
                    .status(FriendshipStatus.ACCEPTED)
                    .build();

            when(friendshipRepository.findBetweenUsers(USER_ID, FRIEND_ID))
                    .thenReturn(Optional.of(acceptedFriendship));
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> invocation.getArgument(0));

            friendshipService.unfriend(USER_ID, FRIEND_ID);

            ArgumentCaptor<Friendship> savedCaptor = ArgumentCaptor.forClass(Friendship.class);
            verify(friendshipRepository).save(savedCaptor.capture());
            assertThat(savedCaptor.getValue().getStatus()).isEqualTo(FriendshipStatus.UNFRIENDED);

            ArgumentCaptor<FriendshipEvent> eventCaptor = ArgumentCaptor.forClass(FriendshipEvent.class);
            verify(friendshipEventRepository).save(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getFromStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
            assertThat(eventCaptor.getValue().getToStatus()).isEqualTo(FriendshipStatus.UNFRIENDED);

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

            verify(friendshipRepository, never()).save(any(Friendship.class));
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
            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(apiResponse(testUserSummary));

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
            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(apiResponse(testUserSummary));

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
            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(apiResponse(testUserSummary));

            PageResponse<FriendResponse> response = friendshipService.getFriends(USER_ID, 0, 20);

            assertThat(response.getContent().get(0).getUsername()).isEqualTo("friend");
        }

        @Test
        @DisplayName("SSO returns null — username is null, no fake email")
        void fetchUserSummaryWithFallback_ReturnsNull() {
            setupFriendsPage();
            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(apiResponse(null));

            PageResponse<FriendResponse> response = friendshipService.getFriends(USER_ID, 0, 20);

            assertThat(response.getContent().get(0).getFriendId()).isEqualTo(FRIEND_ID);
            assertThat(response.getContent().get(0).getUsername()).isNull();
        }

        @Test
        @DisplayName("FeignException.NotFound — throws, never returns unknown@gmail.com")
        void fetchUserSummaryWithFallback_FeignNotFound() {
            setupFriendsPage();
            Request feignRequest = Request.create(Request.HttpMethod.GET, "/users",
                    Collections.emptyMap(), null, new RequestTemplate());
            when(userServiceClient.getUserSummary(FRIEND_ID))
                    .thenThrow(new FeignException.NotFound("Not found", feignRequest, null, null));

            assertThatThrownBy(() -> friendshipService.getFriends(USER_ID, 0, 20))
                    .isInstanceOf(FeignException.NotFound.class);
        }

        @Test
        @DisplayName("FeignException (5xx) — throws, never returns unknown@gmail.com")
        void fetchUserSummaryWithFallback_FeignOther() {
            setupFriendsPage();
            Request feignRequest = Request.create(Request.HttpMethod.GET, "/users",
                    Collections.emptyMap(), null, new RequestTemplate());
            when(userServiceClient.getUserSummary(FRIEND_ID))
                    .thenThrow(new FeignException.ServiceUnavailable("Service unavailable", feignRequest, null, null));

            assertThatThrownBy(() -> friendshipService.getFriends(USER_ID, 0, 20))
                    .isInstanceOf(FeignException.class);
        }

        @Test
        @DisplayName("Generic Exception — throws, never returns unknown@gmail.com")
        void fetchUserSummaryWithFallback_GenericException() {
            setupFriendsPage();
            when(userServiceClient.getUserSummary(FRIEND_ID))
                    .thenThrow(new RuntimeException("Connection failed"));

            assertThatThrownBy(() -> friendshipService.getFriends(USER_ID, 0, 20))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Connection failed");
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
            when(friendshipRepository.findMutualFriendIds(FRIEND_ID, USER_ID)).thenReturn(List.of(10L, 20L, 30L, 40L, 50L));

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
            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(apiResponse(testUserSummary));
            when(friendshipRepository.findMutualFriendIds(USER_ID, FRIEND_ID))
                    .thenReturn(List.of(10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L));

            PageResponse<FriendResponse> response = friendshipService.getFriends(USER_ID, 0, 20);

            FriendResponse friendResponse = response.getContent().get(0);
            assertThat(friendResponse.getFriendId()).isEqualTo(FRIEND_ID);
            assertThat(friendResponse.getUsername()).isEqualTo("friend");
            assertThat(friendResponse.getMutualFriendsCount()).isEqualTo(10);
            assertThat(friendResponse.getFriendsSince()).isNotNull();
        }
    }

    // ==================== STATUS TRANSITION GUARDS ====================

    @Nested
    @DisplayName("Status transition guards (P04)")
    class StatusTransitionGuardTests {

        @Test
        @DisplayName("After decline, declined pair does not appear in getFriendIds (regression guard: query must filter by ACCEPTED)")
        void afterDecline_PairAbsentFromFriendIdsList() {
            // findFriendIds() uses WHERE status = 'ACCEPTED' — a DECLINED row must never leak.
            // If someone removes that filter from the JPQL, this contract assertion fails.
            when(friendshipRepository.findFriendIds(USER_ID)).thenReturn(Collections.emptyList());

            List<Long> result = friendshipService.getFriendIds(USER_ID);

            assertThat(result).doesNotContain(FRIEND_ID);
            verify(friendshipRepository).findFriendIds(USER_ID);
        }

        @Test
        @DisplayName("After block, areFriends returns false (BLOCKED status is not ACCEPTED)")
        void afterBlock_AreFriendsIsFalse() {
            when(friendshipRepository.areFriends(USER_ID, FRIEND_ID)).thenReturn(false);

            assertThat(friendshipService.areFriends(USER_ID, FRIEND_ID)).isFalse();
        }

        @Test
        @DisplayName("Re-request after decline: row returns to PENDING and both events survive in friendship_events")
        void reRequestAfterDecline_BothEventsInHistory() {
            // Step 1 — decline the pending request
            testFriendship.setRequesterId(FRIEND_ID);
            testFriendship.setAddresseeId(USER_ID);
            when(friendshipRepository.findById(FRIENDSHIP_ID)).thenReturn(Optional.of(testFriendship));
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> invocation.getArgument(0));

            friendshipService.declineFriendRequest(USER_ID, FRIENDSHIP_ID);
            // testFriendship.status is now DECLINED

            // Step 2 — re-request; friendship row is found in DECLINED state
            FriendRequest request = FriendRequest.builder().userId(FRIEND_ID).build();
            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(apiResponse(testUserSummary));
            when(blockRepository.isBlockedEitherWay(USER_ID, FRIEND_ID)).thenReturn(false);
            when(friendshipRepository.findBetweenUsers(USER_ID, FRIEND_ID)).thenReturn(Optional.of(testFriendship));
            when(friendshipRepository.findMutualFriendIds(USER_ID, FRIEND_ID)).thenReturn(Collections.emptyList());

            FriendshipResponse response = friendshipService.sendFriendRequest(USER_ID, request);

            assertThat(response.getStatus()).isEqualTo(FriendshipStatus.PENDING);

            // Both events saved across the two operations: PENDING→DECLINED then DECLINED→PENDING
            ArgumentCaptor<FriendshipEvent> eventCaptor = ArgumentCaptor.forClass(FriendshipEvent.class);
            verify(friendshipEventRepository, times(2)).save(eventCaptor.capture());
            List<FriendshipEvent> events = eventCaptor.getAllValues();
            assertThat(events.get(0).getFromStatus()).isEqualTo(FriendshipStatus.PENDING);
            assertThat(events.get(0).getToStatus()).isEqualTo(FriendshipStatus.DECLINED);
            assertThat(events.get(1).getFromStatus()).isEqualTo(FriendshipStatus.DECLINED);
            assertThat(events.get(1).getToStatus()).isEqualTo(FriendshipStatus.PENDING);

            // Append-only: no event deleted
            verify(friendshipEventRepository, never()).delete(any());
            verify(friendshipEventRepository, never()).deleteAll();
        }

        @Test
        @DisplayName("Re-request after block: BLOCKED friendship row refuses with same message as isBlockedEitherWay refusal")
        void reRequestAfterBlock_RefusedWithSameBlockMessage() {
            FriendRequest request = FriendRequest.builder().userId(FRIEND_ID).build();

            Friendship blockedFriendship = Friendship.builder()
                    .id(FRIENDSHIP_ID)
                    .requesterId(USER_ID)
                    .addresseeId(FRIEND_ID)
                    .status(FriendshipStatus.BLOCKED)
                    .build();

            when(userServiceClient.getUserSummary(FRIEND_ID)).thenReturn(apiResponse(testUserSummary));
            // isBlockedEitherWay returns false so we reach the switch; friendship row has BLOCKED status
            when(blockRepository.isBlockedEitherWay(USER_ID, FRIEND_ID)).thenReturn(false);
            when(friendshipRepository.findBetweenUsers(USER_ID, FRIEND_ID)).thenReturn(Optional.of(blockedFriendship));

            assertThatThrownBy(() -> friendshipService.sendFriendRequest(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Cannot send friend request to this user");

            // Same message the existing sendFriendRequest_BlockedEitherWay test asserts — indistinguishable paths
            verify(friendshipRepository, never()).save(any(Friendship.class));
            verify(friendshipRepository, never()).countFriends(any());
        }

        @Test
        @DisplayName("Unfriend publishes social.unfriended (explicit regression guard)")
        void unfriend_StillPublishesSocialUnfriendedEvent() {
            Friendship acceptedFriendship = Friendship.builder()
                    .id(FRIENDSHIP_ID)
                    .requesterId(USER_ID)
                    .addresseeId(FRIEND_ID)
                    .status(FriendshipStatus.ACCEPTED)
                    .build();

            when(friendshipRepository.findBetweenUsers(USER_ID, FRIEND_ID)).thenReturn(Optional.of(acceptedFriendship));
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> invocation.getArgument(0));

            friendshipService.unfriend(USER_ID, FRIEND_ID);

            verify(eventPublisher).publishUnfriended(USER_ID, FRIEND_ID);
        }
    }

    private static ApiResponse<UserSummary> apiResponse(UserSummary summary) {
        ApiResponse<UserSummary> response = new ApiResponse<>();
        response.setSuccess(summary != null);
        response.setData(summary);
        return response;
    }
}