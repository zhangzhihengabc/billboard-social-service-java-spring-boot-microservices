package com.billboard.social.graph.controller;

import com.billboard.social.common.exception.GlobalExceptionHandler;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.graph.dto.response.SocialResponses.FriendPresenceResponse;
import com.billboard.social.graph.service.PresenceService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PresenceController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PresenceControllerTest {

    private static final Long USER_ID     = 1L;
    private static final Long FRIEND_ID_1 = 2L;
    private static final Long FRIEND_ID_2 = 3L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PresenceService presenceService;

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
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==================== PUT /api/v1/presence/heartbeat ====================

    @Nested
    @DisplayName("PUT /api/v1/presence/heartbeat - heartbeat")
    class HeartbeatTests {

        @Test
        @DisplayName("Success - returns 200 OK and calls markOnline for the authenticated user")
        void heartbeat_Success_Returns200() throws Exception {
            doNothing().when(presenceService).markOnline(USER_ID);

            mockMvc.perform(put("/api/v1/presence/heartbeat"))
                    .andExpect(status().isOk());

            verify(presenceService).markOnline(USER_ID);
        }

        @Test
        @DisplayName("Calls markOnline with the authenticated user's ID, not a hardcoded value")
        void heartbeat_CallsMarkOnlineWithCorrectUserId() throws Exception {
            doNothing().when(presenceService).markOnline(USER_ID);

            mockMvc.perform(put("/api/v1/presence/heartbeat"))
                    .andExpect(status().isOk());

            verify(presenceService, times(1)).markOnline(USER_ID);
            verify(presenceService, never()).markOnline(FRIEND_ID_1);
        }
    }

    // ==================== GET /api/v1/presence/friends ====================

    @Nested
    @DisplayName("GET /api/v1/presence/friends - getOnlineFriends")
    class GetOnlineFriendsTests {

        @Test
        @DisplayName("Success - returns 200 with list of online friends")
        void getOnlineFriends_Success_Returns200WithList() throws Exception {
            List<FriendPresenceResponse> onlineFriends = List.of(
                    FriendPresenceResponse.builder()
                            .userId(FRIEND_ID_1)
                            .username("alice")
                            .online(true)
                            .build(),
                    FriendPresenceResponse.builder()
                            .userId(FRIEND_ID_2)
                            .username("bob")
                            .online(true)
                            .build()
            );
            when(presenceService.getOnlineFriends(USER_ID)).thenReturn(onlineFriends);

            mockMvc.perform(get("/api/v1/presence/friends"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].userId").value(FRIEND_ID_1.intValue()))
                    .andExpect(jsonPath("$[0].username").value("alice"))
                    .andExpect(jsonPath("$[0].online").value(true))
                    .andExpect(jsonPath("$[1].userId").value(FRIEND_ID_2.intValue()))
                    .andExpect(jsonPath("$[1].username").value("bob"))
                    .andExpect(jsonPath("$[1].online").value(true));
        }

        @Test
        @DisplayName("Success - returns 200 with empty list when no friends are online")
        void getOnlineFriends_NoOnlineFriends_Returns200WithEmptyList() throws Exception {
            when(presenceService.getOnlineFriends(USER_ID)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/v1/presence/friends"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Success - returns 200 with single online friend")
        void getOnlineFriends_OneFriendOnline_Returns200WithSingleEntry() throws Exception {
            List<FriendPresenceResponse> onlineFriends = List.of(
                    FriendPresenceResponse.builder()
                            .userId(FRIEND_ID_1)
                            .username("alice")
                            .online(true)
                            .build()
            );
            when(presenceService.getOnlineFriends(USER_ID)).thenReturn(onlineFriends);

            mockMvc.perform(get("/api/v1/presence/friends"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].userId").value(FRIEND_ID_1.intValue()))
                    .andExpect(jsonPath("$[0].username").value("alice"))
                    .andExpect(jsonPath("$[0].online").value(true));
        }

        @Test
        @DisplayName("Delegates to PresenceService with the authenticated user's ID")
        void getOnlineFriends_DelegatesToServiceWithCorrectUserId() throws Exception {
            when(presenceService.getOnlineFriends(USER_ID)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/v1/presence/friends"))
                    .andExpect(status().isOk());

            verify(presenceService, times(1)).getOnlineFriends(USER_ID);
            verify(presenceService, never()).getOnlineFriends(FRIEND_ID_1);
        }

        @Test
        @DisplayName("Returns friends with unknown username when fallback is applied")
        void getOnlineFriends_FallbackUsername_AppearsInResponse() throws Exception {
            List<FriendPresenceResponse> onlineFriends = List.of(
                    FriendPresenceResponse.builder()
                            .userId(FRIEND_ID_1)
                            .username("Unknown")
                            .online(true)
                            .build()
            );
            when(presenceService.getOnlineFriends(USER_ID)).thenReturn(onlineFriends);

            mockMvc.perform(get("/api/v1/presence/friends"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].username").value("Unknown"));
        }
    }
}