package com.billboard.social.graph.controller;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.exception.GlobalExceptionHandler;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.graph.dto.request.SocialRequests.FriendRequest;
import com.billboard.social.graph.dto.response.SocialResponses.FriendResponse;
import com.billboard.social.graph.dto.response.SocialResponses.FriendshipResponse;
import com.billboard.social.graph.entity.enums.FriendshipStatus;
import com.billboard.social.graph.service.FriendshipService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.*;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FriendshipController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class FriendshipControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FRIEND_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID FRIENDSHIP_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FriendshipService friendshipService;

    private FriendshipResponse testFriendshipResponse;
    private FriendResponse testFriendResponse;

    @BeforeEach
    void setUp() {
        UserPrincipal userPrincipal = UserPrincipal.builder()
                .id(USER_ID)
                .username("testuser")
                .email("test@example.com")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        testFriendshipResponse = FriendshipResponse.builder()
                .id(FRIENDSHIP_ID)
                .requesterId(USER_ID)
                .addresseeId(FRIEND_ID)
                .status(FriendshipStatus.PENDING)
                .message("Let's be friends!")
                .mutualFriendsCount(5)
                .createdAt(LocalDateTime.now())
                .build();

        testFriendResponse = FriendResponse.builder()
                .friendId(FRIEND_ID)
                .username("friend")
                .displayName("Friend User")
                .avatarUrl("https://example.com/avatar.jpg")
                .isVerified(false)
                .mutualFriendsCount(3)
                .friendsSince(LocalDateTime.now())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==================== SEND FRIEND REQUEST ====================

    @Nested
    @DisplayName("POST /friendships/request - sendFriendRequest")
    class SendFriendRequestTests {

        @Test
        @DisplayName("Success - returns 201")
        void sendFriendRequest_Success() throws Exception {
            FriendRequest request = FriendRequest.builder()
                    .userId(FRIEND_ID)
                    .message("Let's be friends!")
                    .build();

            when(friendshipService.sendFriendRequest(eq(USER_ID), any(FriendRequest.class)))
                    .thenReturn(testFriendshipResponse);

            mockMvc.perform(post("/friendships/request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(FRIENDSHIP_ID.toString()))
                    .andExpect(jsonPath("$.requesterId").value(USER_ID.toString()))
                    .andExpect(jsonPath("$.addresseeId").value(FRIEND_ID.toString()))
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        @DisplayName("Success - without message")
        void sendFriendRequest_WithoutMessage() throws Exception {
            FriendRequest request = FriendRequest.builder()
                    .userId(FRIEND_ID)
                    .build();

            testFriendshipResponse.setMessage(null);
            when(friendshipService.sendFriendRequest(eq(USER_ID), any(FriendRequest.class)))
                    .thenReturn(testFriendshipResponse);

            mockMvc.perform(post("/friendships/request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("Missing userId - returns 400")
        void sendFriendRequest_MissingUserId() throws Exception {
            FriendRequest request = FriendRequest.builder()
                    .message("Hello!")
                    .build();

            mockMvc.perform(post("/friendships/request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(friendshipService);
        }

        @Test
        @DisplayName("Cannot send to yourself - returns 400")
        void sendFriendRequest_CannotSendToYourself() throws Exception {
            FriendRequest request = FriendRequest.builder()
                    .userId(FRIEND_ID)
                    .build();

            when(friendshipService.sendFriendRequest(eq(USER_ID), any(FriendRequest.class)))
                    .thenThrow(new ValidationException("Cannot send friend request to yourself"));

            mockMvc.perform(post("/friendships/request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Cannot send friend request to yourself"));
        }

        @Test
        @DisplayName("Already friends - returns 400")
        void sendFriendRequest_AlreadyFriends() throws Exception {
            FriendRequest request = FriendRequest.builder()
                    .userId(FRIEND_ID)
                    .build();

            when(friendshipService.sendFriendRequest(eq(USER_ID), any(FriendRequest.class)))
                    .thenThrow(new ValidationException("Already friends with this user"));

            mockMvc.perform(post("/friendships/request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Request pending - returns 400")
        void sendFriendRequest_RequestPending() throws Exception {
            FriendRequest request = FriendRequest.builder()
                    .userId(FRIEND_ID)
                    .build();

            when(friendshipService.sendFriendRequest(eq(USER_ID), any(FriendRequest.class)))
                    .thenThrow(new ValidationException("Friend request already pending"));

            mockMvc.perform(post("/friendships/request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("User blocked - returns 400")
        void sendFriendRequest_UserBlocked() throws Exception {
            FriendRequest request = FriendRequest.builder()
                    .userId(FRIEND_ID)
                    .build();

            when(friendshipService.sendFriendRequest(eq(USER_ID), any(FriendRequest.class)))
                    .thenThrow(new ValidationException("Cannot send friend request to blocked user"));

            mockMvc.perform(post("/friendships/request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Max limit reached - returns 400")
        void sendFriendRequest_MaxLimitReached() throws Exception {
            FriendRequest request = FriendRequest.builder()
                    .userId(FRIEND_ID)
                    .build();

            when(friendshipService.sendFriendRequest(eq(USER_ID), any(FriendRequest.class)))
                    .thenThrow(new ValidationException("Maximum friends limit reached"));

            mockMvc.perform(post("/friendships/request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("User not found - returns 400")
        void sendFriendRequest_UserNotFound() throws Exception {
            FriendRequest request = FriendRequest.builder()
                    .userId(FRIEND_ID)
                    .build();

            when(friendshipService.sendFriendRequest(eq(USER_ID), any(FriendRequest.class)))
                    .thenThrow(new ValidationException("User not found"));

            mockMvc.perform(post("/friendships/request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Missing request body - returns 400")
        void sendFriendRequest_MissingBody() throws Exception {
            mockMvc.perform(post("/friendships/request")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(friendshipService);
        }

        @Test
        @DisplayName("Malformed JSON - returns 400")
        void sendFriendRequest_MalformedJson() throws Exception {
            mockMvc.perform(post("/friendships/request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\": }"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(friendshipService);
        }
    }

    // ==================== ACCEPT FRIEND REQUEST ====================

    @Nested
    @DisplayName("POST /friendships/{friendshipId}/accept - acceptFriendRequest")
    class AcceptFriendRequestTests {

        @Test
        @DisplayName("Success - returns 200")
        void acceptFriendRequest_Success() throws Exception {
            testFriendshipResponse.setStatus(FriendshipStatus.ACCEPTED);
            testFriendshipResponse.setAcceptedAt(LocalDateTime.now());

            when(friendshipService.acceptFriendRequest(USER_ID, FRIENDSHIP_ID))
                    .thenReturn(testFriendshipResponse);

            mockMvc.perform(post("/friendships/{friendshipId}/accept", FRIENDSHIP_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(FRIENDSHIP_ID.toString()))
                    .andExpect(jsonPath("$.status").value("ACCEPTED"));
        }

        @Test
        @DisplayName("Not the addressee - returns 400")
        void acceptFriendRequest_NotTheAddressee() throws Exception {
            when(friendshipService.acceptFriendRequest(USER_ID, FRIENDSHIP_ID))
                    .thenThrow(new ValidationException("Only the addressee can accept this request"));

            mockMvc.perform(post("/friendships/{friendshipId}/accept", FRIENDSHIP_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Only the addressee can accept this request"));
        }

        @Test
        @DisplayName("Request not pending - returns 400")
        void acceptFriendRequest_RequestNotPending() throws Exception {
            when(friendshipService.acceptFriendRequest(USER_ID, FRIENDSHIP_ID))
                    .thenThrow(new ValidationException("Request is not pending"));

            mockMvc.perform(post("/friendships/{friendshipId}/accept", FRIENDSHIP_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Friendship not found - returns 400")
        void acceptFriendRequest_FriendshipNotFound() throws Exception {
            when(friendshipService.acceptFriendRequest(USER_ID, FRIENDSHIP_ID))
                    .thenThrow(new ValidationException("Friendship not found"));

            mockMvc.perform(post("/friendships/{friendshipId}/accept", FRIENDSHIP_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void acceptFriendRequest_InvalidUuid() throws Exception {
            mockMvc.perform(post("/friendships/{friendshipId}/accept", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(friendshipService);
        }
    }

    // ==================== DECLINE FRIEND REQUEST ====================

    @Nested
    @DisplayName("POST /friendships/{friendshipId}/decline - declineFriendRequest")
    class DeclineFriendRequestTests {

        @Test
        @DisplayName("Success - returns 204")
        void declineFriendRequest_Success() throws Exception {
            doNothing().when(friendshipService).declineFriendRequest(USER_ID, FRIENDSHIP_ID);

            mockMvc.perform(post("/friendships/{friendshipId}/decline", FRIENDSHIP_ID))
                    .andExpect(status().isNoContent());

            verify(friendshipService).declineFriendRequest(USER_ID, FRIENDSHIP_ID);
        }

        @Test
        @DisplayName("Not the addressee - returns 400")
        void declineFriendRequest_NotTheAddressee() throws Exception {
            doThrow(new ValidationException("Only the addressee can decline this request"))
                    .when(friendshipService).declineFriendRequest(USER_ID, FRIENDSHIP_ID);

            mockMvc.perform(post("/friendships/{friendshipId}/decline", FRIENDSHIP_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Request not pending - returns 400")
        void declineFriendRequest_RequestNotPending() throws Exception {
            doThrow(new ValidationException("Request is not pending"))
                    .when(friendshipService).declineFriendRequest(USER_ID, FRIENDSHIP_ID);

            mockMvc.perform(post("/friendships/{friendshipId}/decline", FRIENDSHIP_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Friendship not found - returns 400")
        void declineFriendRequest_FriendshipNotFound() throws Exception {
            doThrow(new ValidationException("Friendship not found"))
                    .when(friendshipService).declineFriendRequest(USER_ID, FRIENDSHIP_ID);

            mockMvc.perform(post("/friendships/{friendshipId}/decline", FRIENDSHIP_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void declineFriendRequest_InvalidUuid() throws Exception {
            mockMvc.perform(post("/friendships/{friendshipId}/decline", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(friendshipService);
        }
    }

    // ==================== CANCEL FRIEND REQUEST ====================

    @Nested
    @DisplayName("DELETE /friendships/{friendshipId}/cancel - cancelFriendRequest")
    class CancelFriendRequestTests {

        @Test
        @DisplayName("Success - returns 204")
        void cancelFriendRequest_Success() throws Exception {
            doNothing().when(friendshipService).cancelFriendRequest(USER_ID, FRIENDSHIP_ID);

            mockMvc.perform(delete("/friendships/{friendshipId}/cancel", FRIENDSHIP_ID))
                    .andExpect(status().isNoContent());

            verify(friendshipService).cancelFriendRequest(USER_ID, FRIENDSHIP_ID);
        }

        @Test
        @DisplayName("Not the requester - returns 400")
        void cancelFriendRequest_NotTheRequester() throws Exception {
            doThrow(new ValidationException("Only the requester can cancel this request"))
                    .when(friendshipService).cancelFriendRequest(USER_ID, FRIENDSHIP_ID);

            mockMvc.perform(delete("/friendships/{friendshipId}/cancel", FRIENDSHIP_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Request not pending - returns 400")
        void cancelFriendRequest_RequestNotPending() throws Exception {
            doThrow(new ValidationException("Request is not pending"))
                    .when(friendshipService).cancelFriendRequest(USER_ID, FRIENDSHIP_ID);

            mockMvc.perform(delete("/friendships/{friendshipId}/cancel", FRIENDSHIP_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Friendship not found - returns 400")
        void cancelFriendRequest_FriendshipNotFound() throws Exception {
            doThrow(new ValidationException("Friendship not found"))
                    .when(friendshipService).cancelFriendRequest(USER_ID, FRIENDSHIP_ID);

            mockMvc.perform(delete("/friendships/{friendshipId}/cancel", FRIENDSHIP_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void cancelFriendRequest_InvalidUuid() throws Exception {
            mockMvc.perform(delete("/friendships/{friendshipId}/cancel", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(friendshipService);
        }
    }

    // ==================== UNFRIEND ====================

    @Nested
    @DisplayName("DELETE /friendships/{friendId} - unfriend")
    class UnfriendTests {

        @Test
        @DisplayName("Success - returns 204")
        void unfriend_Success() throws Exception {
            doNothing().when(friendshipService).unfriend(USER_ID, FRIEND_ID);

            mockMvc.perform(delete("/friendships/{friendId}", FRIEND_ID))
                    .andExpect(status().isNoContent());

            verify(friendshipService).unfriend(USER_ID, FRIEND_ID);
        }

        @Test
        @DisplayName("Not friends - returns 400")
        void unfriend_NotFriends() throws Exception {
            doThrow(new ValidationException("Not friends with this user"))
                    .when(friendshipService).unfriend(USER_ID, FRIEND_ID);

            mockMvc.perform(delete("/friendships/{friendId}", FRIEND_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Not friends with this user"));
        }

        @Test
        @DisplayName("Friendship not found - returns 400")
        void unfriend_FriendshipNotFound() throws Exception {
            doThrow(new ValidationException("Friendship not found"))
                    .when(friendshipService).unfriend(USER_ID, FRIEND_ID);

            mockMvc.perform(delete("/friendships/{friendId}", FRIEND_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void unfriend_InvalidUuid() throws Exception {
            mockMvc.perform(delete("/friendships/{friendId}", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(friendshipService);
        }
    }

    // ==================== GET FRIENDS ====================

    @Nested
    @DisplayName("GET /friendships - getFriends")
    class GetFriendsTests {

        @Test
        @DisplayName("Success - returns paginated friends")
        void getFriends_Success() throws Exception {
            PageResponse<FriendResponse> pageResponse = PageResponse.<FriendResponse>builder()
                    .content(List.of(testFriendResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(friendshipService.getFriends(USER_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/friendships"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].friendId").value(FRIEND_ID.toString()))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("Success - empty list")
        void getFriends_Empty() throws Exception {
            PageResponse<FriendResponse> pageResponse = PageResponse.<FriendResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(friendshipService.getFriends(USER_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/friendships"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Custom pagination")
        void getFriends_CustomPagination() throws Exception {
            PageResponse<FriendResponse> pageResponse = PageResponse.<FriendResponse>builder()
                    .content(Collections.emptyList())
                    .page(5)
                    .size(50)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(friendshipService.getFriends(USER_ID, 5, 50)).thenReturn(pageResponse);

            mockMvc.perform(get("/friendships")
                            .param("page", "5")
                            .param("size", "50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(5))
                    .andExpect(jsonPath("$.size").value(50));
        }

        @Test
        @DisplayName("Page below minimum - returns 400")
        void getFriends_PageBelowMin() throws Exception {
            mockMvc.perform(get("/friendships")
                            .param("page", "-1"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(friendshipService);
        }

        @Test
        @DisplayName("Page above maximum - returns 400")
        void getFriends_PageAboveMax() throws Exception {
            mockMvc.perform(get("/friendships")
                            .param("page", "1001"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(friendshipService);
        }

        @Test
        @DisplayName("Size below minimum - returns 400")
        void getFriends_SizeBelowMin() throws Exception {
            mockMvc.perform(get("/friendships")
                            .param("size", "0"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(friendshipService);
        }

        @Test
        @DisplayName("Size above maximum - returns 400")
        void getFriends_SizeAboveMax() throws Exception {
            mockMvc.perform(get("/friendships")
                            .param("size", "101"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(friendshipService);
        }

        @Test
        @DisplayName("Boundary - page at max (1000)")
        void getFriends_PageAtMax() throws Exception {
            PageResponse<FriendResponse> pageResponse = PageResponse.<FriendResponse>builder()
                    .content(Collections.emptyList())
                    .page(1000)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(friendshipService.getFriends(USER_ID, 1000, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/friendships")
                            .param("page", "1000"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Boundary - size at min (1)")
        void getFriends_SizeAtMin() throws Exception {
            PageResponse<FriendResponse> pageResponse = PageResponse.<FriendResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(1)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(friendshipService.getFriends(USER_ID, 0, 1)).thenReturn(pageResponse);

            mockMvc.perform(get("/friendships")
                            .param("size", "1"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Boundary - size at max (100)")
        void getFriends_SizeAtMax() throws Exception {
            PageResponse<FriendResponse> pageResponse = PageResponse.<FriendResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(100)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(friendshipService.getFriends(USER_ID, 0, 100)).thenReturn(pageResponse);

            mockMvc.perform(get("/friendships")
                            .param("size", "100"))
                    .andExpect(status().isOk());
        }
    }

    // ==================== GET PENDING REQUESTS ====================

    @Nested
    @DisplayName("GET /friendships/requests/pending - getPendingRequests")
    class GetPendingRequestsTests {

        @Test
        @DisplayName("Success - returns paginated pending requests")
        void getPendingRequests_Success() throws Exception {
            PageResponse<FriendshipResponse> pageResponse = PageResponse.<FriendshipResponse>builder()
                    .content(List.of(testFriendshipResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(friendshipService.getPendingRequests(USER_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/friendships/requests/pending"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].status").value("PENDING"));
        }

        @Test
        @DisplayName("Success - empty list")
        void getPendingRequests_Empty() throws Exception {
            PageResponse<FriendshipResponse> pageResponse = PageResponse.<FriendshipResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(friendshipService.getPendingRequests(USER_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/friendships/requests/pending"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Custom pagination")
        void getPendingRequests_CustomPagination() throws Exception {
            PageResponse<FriendshipResponse> pageResponse = PageResponse.<FriendshipResponse>builder()
                    .content(Collections.emptyList())
                    .page(3)
                    .size(25)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(friendshipService.getPendingRequests(USER_ID, 3, 25)).thenReturn(pageResponse);

            mockMvc.perform(get("/friendships/requests/pending")
                            .param("page", "3")
                            .param("size", "25"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Page above maximum - returns 400")
        void getPendingRequests_PageAboveMax() throws Exception {
            mockMvc.perform(get("/friendships/requests/pending")
                            .param("page", "1001"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(friendshipService);
        }

        @Test
        @DisplayName("Size below minimum - returns 400")
        void getPendingRequests_SizeBelowMin() throws Exception {
            mockMvc.perform(get("/friendships/requests/pending")
                            .param("size", "0"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(friendshipService);
        }
    }

    // ==================== GET SENT REQUESTS ====================

    @Nested
    @DisplayName("GET /friendships/requests/sent - getSentRequests")
    class GetSentRequestsTests {

        @Test
        @DisplayName("Success - returns paginated sent requests")
        void getSentRequests_Success() throws Exception {
            PageResponse<FriendshipResponse> pageResponse = PageResponse.<FriendshipResponse>builder()
                    .content(List.of(testFriendshipResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(friendshipService.getSentRequests(USER_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/friendships/requests/sent"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].requesterId").value(USER_ID.toString()));
        }

        @Test
        @DisplayName("Success - empty list")
        void getSentRequests_Empty() throws Exception {
            PageResponse<FriendshipResponse> pageResponse = PageResponse.<FriendshipResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(friendshipService.getSentRequests(USER_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/friendships/requests/sent"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Custom pagination")
        void getSentRequests_CustomPagination() throws Exception {
            PageResponse<FriendshipResponse> pageResponse = PageResponse.<FriendshipResponse>builder()
                    .content(Collections.emptyList())
                    .page(2)
                    .size(30)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(friendshipService.getSentRequests(USER_ID, 2, 30)).thenReturn(pageResponse);

            mockMvc.perform(get("/friendships/requests/sent")
                            .param("page", "2")
                            .param("size", "30"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Size above maximum - returns 400")
        void getSentRequests_SizeAboveMax() throws Exception {
            mockMvc.perform(get("/friendships/requests/sent")
                            .param("size", "101"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(friendshipService);
        }
    }

    // ==================== GET FRIEND IDS ====================

    @Nested
    @DisplayName("GET /friendships/ids - getFriendIds")
    class GetFriendIdsTests {

        @Test
        @DisplayName("Success - returns friend IDs")
        void getFriendIds_Success() throws Exception {
            UUID friend2 = UUID.randomUUID();
            UUID friend3 = UUID.randomUUID();
            List<UUID> ids = List.of(FRIEND_ID, friend2, friend3);

            when(friendshipService.getFriendIds(USER_ID)).thenReturn(ids);

            mockMvc.perform(get("/friendships/ids"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[0]").value(FRIEND_ID.toString()));
        }

        @Test
        @DisplayName("Success - empty list")
        void getFriendIds_Empty() throws Exception {
            when(friendshipService.getFriendIds(USER_ID)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/friendships/ids"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Success - single friend")
        void getFriendIds_SingleFriend() throws Exception {
            when(friendshipService.getFriendIds(USER_ID)).thenReturn(List.of(FRIEND_ID));

            mockMvc.perform(get("/friendships/ids"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        }
    }

    // ==================== GET MUTUAL FRIENDS ====================

    @Nested
    @DisplayName("GET /friendships/mutual/{userId} - getMutualFriends")
    class GetMutualFriendsTests {

        @Test
        @DisplayName("Success - returns mutual friend IDs")
        void getMutualFriends_Success() throws Exception {
            UUID mutual1 = UUID.randomUUID();
            UUID mutual2 = UUID.randomUUID();
            List<UUID> mutualIds = List.of(mutual1, mutual2);

            when(friendshipService.getMutualFriendIds(USER_ID, FRIEND_ID)).thenReturn(mutualIds);

            mockMvc.perform(get("/friendships/mutual/{userId}", FRIEND_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)));
        }

        @Test
        @DisplayName("Success - empty list (no mutual friends)")
        void getMutualFriends_Empty() throws Exception {
            when(friendshipService.getMutualFriendIds(USER_ID, FRIEND_ID)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/friendships/mutual/{userId}", FRIEND_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Check mutual with self - returns empty")
        void getMutualFriends_CheckSelf() throws Exception {
            when(friendshipService.getMutualFriendIds(USER_ID, USER_ID)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/friendships/mutual/{userId}", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void getMutualFriends_InvalidUuid() throws Exception {
            mockMvc.perform(get("/friendships/mutual/{userId}", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(friendshipService);
        }
    }

    // ==================== ARE FRIENDS ====================

    @Nested
    @DisplayName("GET /friendships/check/{userId} - areFriends")
    class AreFriendsTests {

        @Test
        @DisplayName("Success - returns true when friends")
        void areFriends_ReturnsTrue() throws Exception {
            when(friendshipService.areFriends(USER_ID, FRIEND_ID)).thenReturn(true);

            mockMvc.perform(get("/friendships/check/{userId}", FRIEND_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string("true"));
        }

        @Test
        @DisplayName("Success - returns false when not friends")
        void areFriends_ReturnsFalse() throws Exception {
            when(friendshipService.areFriends(USER_ID, FRIEND_ID)).thenReturn(false);

            mockMvc.perform(get("/friendships/check/{userId}", FRIEND_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string("false"));
        }

        @Test
        @DisplayName("Check self - returns false")
        void areFriends_CheckSelf() throws Exception {
            when(friendshipService.areFriends(USER_ID, USER_ID)).thenReturn(false);

            mockMvc.perform(get("/friendships/check/{userId}", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string("false"));
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void areFriends_InvalidUuid() throws Exception {
            mockMvc.perform(get("/friendships/check/{userId}", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(friendshipService);
        }
    }

    // ==================== GET FRIENDS COUNT ====================

    @Nested
    @DisplayName("GET /friendships/count - getFriendsCount")
    class GetFriendsCountTests {

        @Test
        @DisplayName("Success - returns count")
        void getFriendsCount_Success() throws Exception {
            when(friendshipService.getFriendsCount(USER_ID)).thenReturn(42L);

            mockMvc.perform(get("/friendships/count"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("42"));
        }

        @Test
        @DisplayName("Success - returns zero")
        void getFriendsCount_Zero() throws Exception {
            when(friendshipService.getFriendsCount(USER_ID)).thenReturn(0L);

            mockMvc.perform(get("/friendships/count"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("0"));
        }

        @Test
        @DisplayName("Success - returns large count")
        void getFriendsCount_LargeCount() throws Exception {
            when(friendshipService.getFriendsCount(USER_ID)).thenReturn(5000L);

            mockMvc.perform(get("/friendships/count"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("5000"));
        }
    }
}