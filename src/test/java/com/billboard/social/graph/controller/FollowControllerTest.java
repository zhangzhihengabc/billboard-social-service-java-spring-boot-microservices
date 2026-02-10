package com.billboard.social.graph.controller;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.exception.GlobalExceptionHandler;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.common.security.JwtAuthenticationFilter;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.graph.dto.request.SocialRequests.FollowRequest;
import com.billboard.social.graph.dto.request.SocialRequests.UpdateFollowRequest;
import com.billboard.social.graph.dto.response.SocialResponses.FollowResponse;
import com.billboard.social.graph.dto.response.SocialResponses.FollowStatsResponse;
import com.billboard.social.graph.service.FollowService;
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

@WebMvcTest(FollowController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class FollowControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TARGET_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID FOLLOW_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FollowService followService;

    private UserPrincipal userPrincipal;
    private FollowResponse testFollowResponse;

    @BeforeEach
    void setUp() {
        userPrincipal = UserPrincipal.builder()
                .id(USER_ID)
                .username("testuser")
                .email("test@example.com")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        UserSummary targetUserSummary = UserSummary.builder()
                .id(TARGET_USER_ID)
                .username("targetuser")
                .displayName("Target User")
                .build();

        testFollowResponse = FollowResponse.builder()
                .id(FOLLOW_ID)
                .followerId(USER_ID)
                .followingId(TARGET_USER_ID)
                .notificationsEnabled(true)
                .isCloseFriend(false)
                .isMuted(false)
                .createdAt(LocalDateTime.now())
                .user(targetUserSummary)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==================== FOLLOW ====================

    @Nested
    @DisplayName("POST /follows - follow")
    class FollowTests {

        @Test
        @DisplayName("Success - returns 201")
        void follow_Success() throws Exception {
            FollowRequest request = FollowRequest.builder()
                    .userId(TARGET_USER_ID)
                    .build();

            when(followService.follow(eq(USER_ID), any(FollowRequest.class)))
                    .thenReturn(testFollowResponse);

            mockMvc.perform(post("/follows")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(FOLLOW_ID.toString()))
                    .andExpect(jsonPath("$.followerId").value(USER_ID.toString()))
                    .andExpect(jsonPath("$.followingId").value(TARGET_USER_ID.toString()));
        }

        @Test
        @DisplayName("Missing userId - returns 400")
        void follow_MissingUserId() throws Exception {
            FollowRequest request = FollowRequest.builder().build();

            mockMvc.perform(post("/follows")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(followService);
        }

        @Test
        @DisplayName("Cannot follow yourself - returns 400")
        void follow_CannotFollowYourself() throws Exception {
            FollowRequest request = FollowRequest.builder()
                    .userId(TARGET_USER_ID)
                    .build();

            when(followService.follow(eq(USER_ID), any(FollowRequest.class)))
                    .thenThrow(new ValidationException("Cannot follow yourself"));

            mockMvc.perform(post("/follows")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Cannot follow yourself"));
        }

        @Test
        @DisplayName("Already following - returns 400")
        void follow_AlreadyFollowing() throws Exception {
            FollowRequest request = FollowRequest.builder()
                    .userId(TARGET_USER_ID)
                    .build();

            when(followService.follow(eq(USER_ID), any(FollowRequest.class)))
                    .thenThrow(new ValidationException("Already following this user"));

            mockMvc.perform(post("/follows")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Already following this user"));
        }

        @Test
        @DisplayName("User blocked - returns 400")
        void follow_UserBlocked() throws Exception {
            FollowRequest request = FollowRequest.builder()
                    .userId(TARGET_USER_ID)
                    .build();

            when(followService.follow(eq(USER_ID), any(FollowRequest.class)))
                    .thenThrow(new ValidationException("Cannot follow a blocked user"));

            mockMvc.perform(post("/follows")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Max limit reached - returns 400")
        void follow_MaxLimitReached() throws Exception {
            FollowRequest request = FollowRequest.builder()
                    .userId(TARGET_USER_ID)
                    .build();

            when(followService.follow(eq(USER_ID), any(FollowRequest.class)))
                    .thenThrow(new ValidationException("Maximum following limit reached"));

            mockMvc.perform(post("/follows")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Missing request body - returns 400")
        void follow_MissingBody() throws Exception {
            mockMvc.perform(post("/follows")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(followService);
        }

        @Test
        @DisplayName("Malformed JSON - returns 400")
        void follow_MalformedJson() throws Exception {
            mockMvc.perform(post("/follows")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\": }"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(followService);
        }
    }

    // ==================== UNFOLLOW ====================

    @Nested
    @DisplayName("DELETE /follows/{userId} - unfollow")
    class UnfollowTests {

        @Test
        @DisplayName("Success - returns 204")
        void unfollow_Success() throws Exception {
            doNothing().when(followService).unfollow(USER_ID, TARGET_USER_ID);

            mockMvc.perform(delete("/follows/{userId}", TARGET_USER_ID))
                    .andExpect(status().isNoContent());

            verify(followService).unfollow(USER_ID, TARGET_USER_ID);
        }

        @Test
        @DisplayName("Follow not found - returns 400")
        void unfollow_NotFound() throws Exception {
            doThrow(new ValidationException("Follow relationship not found"))
                    .when(followService).unfollow(USER_ID, TARGET_USER_ID);

            mockMvc.perform(delete("/follows/{userId}", TARGET_USER_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Follow relationship not found"));
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void unfollow_InvalidUuid() throws Exception {
            mockMvc.perform(delete("/follows/{userId}", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(followService);
        }
    }

    // ==================== UPDATE FOLLOW ====================

    @Nested
    @DisplayName("PUT /follows/{userId} - updateFollow")
    class UpdateFollowTests {

        @Test
        @DisplayName("Success - returns 200")
        void updateFollow_Success() throws Exception {
            UpdateFollowRequest request = UpdateFollowRequest.builder()
                    .notificationsEnabled(false)
                    .isCloseFriend(true)
                    .isMuted(true)
                    .build();

            testFollowResponse.setNotificationsEnabled(false);
            testFollowResponse.setIsCloseFriend(true);
            testFollowResponse.setIsMuted(true);

            when(followService.updateFollow(eq(USER_ID), eq(TARGET_USER_ID), any(UpdateFollowRequest.class)))
                    .thenReturn(testFollowResponse);

            mockMvc.perform(put("/follows/{userId}", TARGET_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.notificationsEnabled").value(false))
                    .andExpect(jsonPath("$.isCloseFriend").value(true))
                    .andExpect(jsonPath("$.isMuted").value(true));
        }

        @Test
        @DisplayName("Success - update only notifications")
        void updateFollow_OnlyNotifications() throws Exception {
            UpdateFollowRequest request = UpdateFollowRequest.builder()
                    .notificationsEnabled(false)
                    .build();

            when(followService.updateFollow(eq(USER_ID), eq(TARGET_USER_ID), any(UpdateFollowRequest.class)))
                    .thenReturn(testFollowResponse);

            mockMvc.perform(put("/follows/{userId}", TARGET_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Success - update only close friend")
        void updateFollow_OnlyCloseFriend() throws Exception {
            UpdateFollowRequest request = UpdateFollowRequest.builder()
                    .isCloseFriend(true)
                    .build();

            testFollowResponse.setIsCloseFriend(true);

            when(followService.updateFollow(eq(USER_ID), eq(TARGET_USER_ID), any(UpdateFollowRequest.class)))
                    .thenReturn(testFollowResponse);

            mockMvc.perform(put("/follows/{userId}", TARGET_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isCloseFriend").value(true));
        }

        @Test
        @DisplayName("Success - update only muted")
        void updateFollow_OnlyMuted() throws Exception {
            UpdateFollowRequest request = UpdateFollowRequest.builder()
                    .isMuted(true)
                    .build();

            testFollowResponse.setIsMuted(true);

            when(followService.updateFollow(eq(USER_ID), eq(TARGET_USER_ID), any(UpdateFollowRequest.class)))
                    .thenReturn(testFollowResponse);

            mockMvc.perform(put("/follows/{userId}", TARGET_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isMuted").value(true));
        }

        @Test
        @DisplayName("Follow not found - returns 400")
        void updateFollow_NotFound() throws Exception {
            UpdateFollowRequest request = UpdateFollowRequest.builder()
                    .notificationsEnabled(false)
                    .build();

            when(followService.updateFollow(eq(USER_ID), eq(TARGET_USER_ID), any(UpdateFollowRequest.class)))
                    .thenThrow(new ValidationException("Follow relationship not found"));

            mockMvc.perform(put("/follows/{userId}", TARGET_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void updateFollow_InvalidUuid() throws Exception {
            UpdateFollowRequest request = UpdateFollowRequest.builder()
                    .notificationsEnabled(false)
                    .build();

            mockMvc.perform(put("/follows/{userId}", "invalid-uuid")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(followService);
        }

        @Test
        @DisplayName("Missing request body - returns 400")
        void updateFollow_MissingBody() throws Exception {
            mockMvc.perform(put("/follows/{userId}", TARGET_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(followService);
        }

        @Test
        @DisplayName("Empty request body - succeeds (no updates)")
        void updateFollow_EmptyBody() throws Exception {
            UpdateFollowRequest request = UpdateFollowRequest.builder().build();

            when(followService.updateFollow(eq(USER_ID), eq(TARGET_USER_ID), any(UpdateFollowRequest.class)))
                    .thenReturn(testFollowResponse);

            mockMvc.perform(put("/follows/{userId}", TARGET_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }
    }

    // ==================== GET FOLLOWERS ====================

    @Nested
    @DisplayName("GET /follows/followers - getFollowers")
    class GetFollowersTests {

        @Test
        @DisplayName("Success - returns paginated followers")
        void getFollowers_Success() throws Exception {
            PageResponse<FollowResponse> pageResponse = PageResponse.<FollowResponse>builder()
                    .content(List.of(testFollowResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(followService.getFollowers(USER_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/follows/followers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("Success - empty list")
        void getFollowers_Empty() throws Exception {
            PageResponse<FollowResponse> pageResponse = PageResponse.<FollowResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(followService.getFollowers(USER_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/follows/followers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Custom pagination")
        void getFollowers_CustomPagination() throws Exception {
            PageResponse<FollowResponse> pageResponse = PageResponse.<FollowResponse>builder()
                    .content(Collections.emptyList())
                    .page(5)
                    .size(50)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(followService.getFollowers(USER_ID, 5, 50)).thenReturn(pageResponse);

            mockMvc.perform(get("/follows/followers")
                            .param("page", "5")
                            .param("size", "50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(5))
                    .andExpect(jsonPath("$.size").value(50));
        }

        @Test
        @DisplayName("Page below minimum - returns 400")
        void getFollowers_PageBelowMin() throws Exception {
            mockMvc.perform(get("/follows/followers")
                            .param("page", "-1"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(followService);
        }

        @Test
        @DisplayName("Page above maximum - returns 400")
        void getFollowers_PageAboveMax() throws Exception {
            mockMvc.perform(get("/follows/followers")
                            .param("page", "1001"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(followService);
        }

        @Test
        @DisplayName("Size below minimum - returns 400")
        void getFollowers_SizeBelowMin() throws Exception {
            mockMvc.perform(get("/follows/followers")
                            .param("size", "0"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(followService);
        }

        @Test
        @DisplayName("Size above maximum - returns 400")
        void getFollowers_SizeAboveMax() throws Exception {
            mockMvc.perform(get("/follows/followers")
                            .param("size", "101"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(followService);
        }

        @Test
        @DisplayName("Boundary - page at max (1000)")
        void getFollowers_PageAtMax() throws Exception {
            PageResponse<FollowResponse> pageResponse = PageResponse.<FollowResponse>builder()
                    .content(Collections.emptyList())
                    .page(1000)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(followService.getFollowers(USER_ID, 1000, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/follows/followers")
                            .param("page", "1000"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Boundary - size at min (1)")
        void getFollowers_SizeAtMin() throws Exception {
            PageResponse<FollowResponse> pageResponse = PageResponse.<FollowResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(1)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(followService.getFollowers(USER_ID, 0, 1)).thenReturn(pageResponse);

            mockMvc.perform(get("/follows/followers")
                            .param("size", "1"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Boundary - size at max (100)")
        void getFollowers_SizeAtMax() throws Exception {
            PageResponse<FollowResponse> pageResponse = PageResponse.<FollowResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(100)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(followService.getFollowers(USER_ID, 0, 100)).thenReturn(pageResponse);

            mockMvc.perform(get("/follows/followers")
                            .param("size", "100"))
                    .andExpect(status().isOk());
        }
    }

    // ==================== GET FOLLOWING ====================

    @Nested
    @DisplayName("GET /follows/following - getFollowing")
    class GetFollowingTests {

        @Test
        @DisplayName("Success - returns paginated following")
        void getFollowing_Success() throws Exception {
            PageResponse<FollowResponse> pageResponse = PageResponse.<FollowResponse>builder()
                    .content(List.of(testFollowResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(followService.getFollowing(USER_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/follows/following"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));
        }

        @Test
        @DisplayName("Success - empty list")
        void getFollowing_Empty() throws Exception {
            PageResponse<FollowResponse> pageResponse = PageResponse.<FollowResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(followService.getFollowing(USER_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/follows/following"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Custom pagination")
        void getFollowing_CustomPagination() throws Exception {
            PageResponse<FollowResponse> pageResponse = PageResponse.<FollowResponse>builder()
                    .content(Collections.emptyList())
                    .page(3)
                    .size(25)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(followService.getFollowing(USER_ID, 3, 25)).thenReturn(pageResponse);

            mockMvc.perform(get("/follows/following")
                            .param("page", "3")
                            .param("size", "25"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Page below minimum - returns 400")
        void getFollowing_PageBelowMin() throws Exception {
            mockMvc.perform(get("/follows/following")
                            .param("page", "-1"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(followService);
        }

        @Test
        @DisplayName("Size above maximum - returns 400")
        void getFollowing_SizeAboveMax() throws Exception {
            mockMvc.perform(get("/follows/following")
                            .param("size", "101"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(followService);
        }
    }

    // ==================== GET CLOSE FRIENDS ====================

    @Nested
    @DisplayName("GET /follows/close-friends - getCloseFriends")
    class GetCloseFriendsTests {

        @Test
        @DisplayName("Success - returns paginated close friends")
        void getCloseFriends_Success() throws Exception {
            testFollowResponse.setIsCloseFriend(true);
            PageResponse<FollowResponse> pageResponse = PageResponse.<FollowResponse>builder()
                    .content(List.of(testFollowResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(followService.getCloseFriends(USER_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/follows/close-friends"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].isCloseFriend").value(true));
        }

        @Test
        @DisplayName("Success - empty list")
        void getCloseFriends_Empty() throws Exception {
            PageResponse<FollowResponse> pageResponse = PageResponse.<FollowResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(followService.getCloseFriends(USER_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/follows/close-friends"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Custom pagination")
        void getCloseFriends_CustomPagination() throws Exception {
            PageResponse<FollowResponse> pageResponse = PageResponse.<FollowResponse>builder()
                    .content(Collections.emptyList())
                    .page(2)
                    .size(30)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(followService.getCloseFriends(USER_ID, 2, 30)).thenReturn(pageResponse);

            mockMvc.perform(get("/follows/close-friends")
                            .param("page", "2")
                            .param("size", "30"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Page above maximum - returns 400")
        void getCloseFriends_PageAboveMax() throws Exception {
            mockMvc.perform(get("/follows/close-friends")
                            .param("page", "1001"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(followService);
        }

        @Test
        @DisplayName("Size below minimum - returns 400")
        void getCloseFriends_SizeBelowMin() throws Exception {
            mockMvc.perform(get("/follows/close-friends")
                            .param("size", "0"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(followService);
        }
    }

    // ==================== GET FOLLOW STATS ====================

    @Nested
    @DisplayName("GET /follows/stats/{userId} - getFollowStats")
    class GetFollowStatsTests {

        @Test
        @DisplayName("Success - returns follow stats")
        void getFollowStats_Success() throws Exception {
            FollowStatsResponse stats = FollowStatsResponse.builder()
                    .userId(TARGET_USER_ID)
                    .followersCount(150L)
                    .followingCount(200L)
                    .isFollowing(true)
                    .isFollowedBy(false)
                    .build();

            when(followService.getFollowStats(TARGET_USER_ID, USER_ID)).thenReturn(stats);

            mockMvc.perform(get("/follows/stats/{userId}", TARGET_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(TARGET_USER_ID.toString()))
                    .andExpect(jsonPath("$.followersCount").value(150))
                    .andExpect(jsonPath("$.followingCount").value(200))
                    .andExpect(jsonPath("$.isFollowing").value(true))
                    .andExpect(jsonPath("$.isFollowedBy").value(false));
        }

        @Test
        @DisplayName("Success - not following, not followed by")
        void getFollowStats_NotFollowing() throws Exception {
            FollowStatsResponse stats = FollowStatsResponse.builder()
                    .userId(TARGET_USER_ID)
                    .followersCount(0L)
                    .followingCount(0L)
                    .isFollowing(false)
                    .isFollowedBy(false)
                    .build();

            when(followService.getFollowStats(TARGET_USER_ID, USER_ID)).thenReturn(stats);

            mockMvc.perform(get("/follows/stats/{userId}", TARGET_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isFollowing").value(false))
                    .andExpect(jsonPath("$.isFollowedBy").value(false));
        }

        @Test
        @DisplayName("Success - mutual follow")
        void getFollowStats_MutualFollow() throws Exception {
            FollowStatsResponse stats = FollowStatsResponse.builder()
                    .userId(TARGET_USER_ID)
                    .followersCount(100L)
                    .followingCount(100L)
                    .isFollowing(true)
                    .isFollowedBy(true)
                    .build();

            when(followService.getFollowStats(TARGET_USER_ID, USER_ID)).thenReturn(stats);

            mockMvc.perform(get("/follows/stats/{userId}", TARGET_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isFollowing").value(true))
                    .andExpect(jsonPath("$.isFollowedBy").value(true));
        }

        @Test
        @DisplayName("Success - own stats")
        void getFollowStats_OwnStats() throws Exception {
            FollowStatsResponse stats = FollowStatsResponse.builder()
                    .userId(USER_ID)
                    .followersCount(50L)
                    .followingCount(75L)
                    .isFollowing(false)
                    .isFollowedBy(false)
                    .build();

            when(followService.getFollowStats(USER_ID, USER_ID)).thenReturn(stats);

            mockMvc.perform(get("/follows/stats/{userId}", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(USER_ID.toString()));
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void getFollowStats_InvalidUuid() throws Exception {
            mockMvc.perform(get("/follows/stats/{userId}", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(followService);
        }

        @Test
        @DisplayName("User not found - returns 400")
        void getFollowStats_UserNotFound() throws Exception {
            when(followService.getFollowStats(TARGET_USER_ID, USER_ID))
                    .thenThrow(new ValidationException("User not found"));

            mockMvc.perform(get("/follows/stats/{userId}", TARGET_USER_ID))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== GET FOLLOWING IDS ====================

    @Nested
    @DisplayName("GET /follows/following/ids - getFollowingIds")
    class GetFollowingIdsTests {

        @Test
        @DisplayName("Success - returns user IDs")
        void getFollowingIds_Success() throws Exception {
            UUID user2 = UUID.randomUUID();
            UUID user3 = UUID.randomUUID();
            List<UUID> ids = List.of(TARGET_USER_ID, user2, user3);

            when(followService.getFollowingIds(USER_ID)).thenReturn(ids);

            mockMvc.perform(get("/follows/following/ids"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[0]").value(TARGET_USER_ID.toString()));
        }

        @Test
        @DisplayName("Success - empty list")
        void getFollowingIds_Empty() throws Exception {
            when(followService.getFollowingIds(USER_ID)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/follows/following/ids"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Success - single following")
        void getFollowingIds_SingleFollowing() throws Exception {
            List<UUID> ids = List.of(TARGET_USER_ID);

            when(followService.getFollowingIds(USER_ID)).thenReturn(ids);

            mockMvc.perform(get("/follows/following/ids"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        }
    }

    // ==================== IS FOLLOWING ====================

    @Nested
    @DisplayName("GET /follows/check/{userId} - isFollowing")
    class IsFollowingTests {

        @Test
        @DisplayName("Success - returns true when following")
        void isFollowing_ReturnsTrue() throws Exception {
            when(followService.isFollowing(USER_ID, TARGET_USER_ID)).thenReturn(true);

            mockMvc.perform(get("/follows/check/{userId}", TARGET_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string("true"));
        }

        @Test
        @DisplayName("Success - returns false when not following")
        void isFollowing_ReturnsFalse() throws Exception {
            when(followService.isFollowing(USER_ID, TARGET_USER_ID)).thenReturn(false);

            mockMvc.perform(get("/follows/check/{userId}", TARGET_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string("false"));
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void isFollowing_InvalidUuid() throws Exception {
            mockMvc.perform(get("/follows/check/{userId}", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(followService);
        }

        @Test
        @DisplayName("Check self - returns false")
        void isFollowing_CheckSelf() throws Exception {
            when(followService.isFollowing(USER_ID, USER_ID)).thenReturn(false);

            mockMvc.perform(get("/follows/check/{userId}", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string("false"));
        }
    }
}