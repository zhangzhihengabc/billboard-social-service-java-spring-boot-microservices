package com.billboard.social.graph.controller;

import com.billboard.social.common.exception.GlobalExceptionHandler;
import com.billboard.social.graph.service.BlockService;
import com.billboard.social.graph.service.FriendshipService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InternalGraphController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class InternalGraphControllerTest {

    private static final Long USER_ID_1 = 1L;
    private static final Long USER_ID_2 = 2L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FriendshipService friendshipService;

    @MockBean
    private BlockService blockService;

    // ==================== DECLINED PAIR ====================

    @Nested
    @DisplayName("Declined pair — S2S endpoints must answer correctly")
    class DeclinedPairTests {

        @Test
        @DisplayName("areFriends returns false — DECLINED is not ACCEPTED")
        void declinedPair_AreFriendsFalse() throws Exception {
            when(friendshipService.areFriends(USER_ID_1, USER_ID_2)).thenReturn(false);

            mockMvc.perform(get("/api/v1/internal/graph/friends/check")
                            .param("userId1", USER_ID_1.toString())
                            .param("userId2", USER_ID_2.toString()))
                    .andExpect(status().isOk())
                    .andExpect(content().string("false"));
        }

        @Test
        @DisplayName("isBlockedEitherWay returns false — declined pair is not blocked")
        void declinedPair_IsBlockedEitherWayFalse() throws Exception {
            when(blockService.isBlockedEitherWay(USER_ID_1, USER_ID_2)).thenReturn(false);

            mockMvc.perform(get("/api/v1/internal/graph/blocks/check")
                            .param("userId1", USER_ID_1.toString())
                            .param("userId2", USER_ID_2.toString()))
                    .andExpect(status().isOk())
                    .andExpect(content().string("false"));
        }

        @Test
        @DisplayName("Combined: declined pair → areFriends=false, isBlockedEitherWay=false (CHAT/CONTENT: not friends, not blocked)")
        void declinedPair_CombinedS2SAnswersCorrectly() throws Exception {
            when(friendshipService.areFriends(USER_ID_1, USER_ID_2)).thenReturn(false);
            when(blockService.isBlockedEitherWay(USER_ID_1, USER_ID_2)).thenReturn(false);

            mockMvc.perform(get("/api/v1/internal/graph/friends/check")
                            .param("userId1", USER_ID_1.toString())
                            .param("userId2", USER_ID_2.toString()))
                    .andExpect(status().isOk())
                    .andExpect(content().string("false"));

            mockMvc.perform(get("/api/v1/internal/graph/blocks/check")
                            .param("userId1", USER_ID_1.toString())
                            .param("userId2", USER_ID_2.toString()))
                    .andExpect(status().isOk())
                    .andExpect(content().string("false"));
        }
    }

    // ==================== BLOCKED PAIR ====================

    @Nested
    @DisplayName("Blocked pair — S2S endpoints must answer correctly")
    class BlockedPairTests {

        @Test
        @DisplayName("areFriends returns false — BLOCKED is not ACCEPTED")
        void blockedPair_AreFriendsFalse() throws Exception {
            when(friendshipService.areFriends(USER_ID_1, USER_ID_2)).thenReturn(false);

            mockMvc.perform(get("/api/v1/internal/graph/friends/check")
                            .param("userId1", USER_ID_1.toString())
                            .param("userId2", USER_ID_2.toString()))
                    .andExpect(status().isOk())
                    .andExpect(content().string("false"));
        }

        @Test
        @DisplayName("isBlockedEitherWay returns true — CHAT/CONTENT must reject message/post for blocked pair")
        void blockedPair_IsBlockedEitherWayTrue() throws Exception {
            when(blockService.isBlockedEitherWay(USER_ID_1, USER_ID_2)).thenReturn(true);

            mockMvc.perform(get("/api/v1/internal/graph/blocks/check")
                            .param("userId1", USER_ID_1.toString())
                            .param("userId2", USER_ID_2.toString()))
                    .andExpect(status().isOk())
                    .andExpect(content().string("true"));
        }

        @Test
        @DisplayName("Combined: blocked pair → areFriends=false, isBlockedEitherWay=true (CHAT/CONTENT must gate)")
        void blockedPair_CombinedS2SAnswersCorrectly() throws Exception {
            when(friendshipService.areFriends(USER_ID_1, USER_ID_2)).thenReturn(false);
            when(blockService.isBlockedEitherWay(USER_ID_1, USER_ID_2)).thenReturn(true);

            mockMvc.perform(get("/api/v1/internal/graph/friends/check")
                            .param("userId1", USER_ID_1.toString())
                            .param("userId2", USER_ID_2.toString()))
                    .andExpect(status().isOk())
                    .andExpect(content().string("false"));

            mockMvc.perform(get("/api/v1/internal/graph/blocks/check")
                            .param("userId1", USER_ID_1.toString())
                            .param("userId2", USER_ID_2.toString()))
                    .andExpect(status().isOk())
                    .andExpect(content().string("true"));
        }
    }

    // ==================== getFriendIds S2S ====================

    @Nested
    @DisplayName("getFriendIds — S2S endpoint")
    class GetFriendIdsEndpointTests {

        @Test
        @DisplayName("Returns friend IDs for a user (S2S)")
        void getFriendIds_ReturnsIds() throws Exception {
            when(friendshipService.getFriendIds(USER_ID_1)).thenReturn(java.util.List.of(USER_ID_2));

            mockMvc.perform(get("/api/v1/internal/graph/friends/ids")
                            .param("userId", USER_ID_1.toString()))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[2]"));
        }

        @Test
        @DisplayName("Returns empty list when user has no friends (declined/blocked do not appear)")
        void getFriendIds_EmptyForDeclinedOrBlocked() throws Exception {
            when(friendshipService.getFriendIds(USER_ID_1)).thenReturn(java.util.Collections.emptyList());

            mockMvc.perform(get("/api/v1/internal/graph/friends/ids")
                            .param("userId", USER_ID_1.toString()))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[]"));
        }
    }
}
