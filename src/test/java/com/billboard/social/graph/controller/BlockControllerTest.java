package com.billboard.social.graph.controller;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.exception.GlobalExceptionHandler;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.common.security.JwtAuthenticationFilter;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.graph.dto.request.SocialRequests.BlockRequest;
import com.billboard.social.graph.dto.response.SocialResponses.BlockResponse;
import com.billboard.social.graph.service.BlockService;
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

@WebMvcTest(BlockController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class BlockControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BLOCKED_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BLOCK_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BlockService blockService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private UserPrincipal userPrincipal;
    private BlockResponse testBlockResponse;

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

        UserSummary blockedUserSummary = UserSummary.builder()
                .id(BLOCKED_USER_ID)
                .username("blockeduser")
                .email("test@gmail.com")
                .build();

        testBlockResponse = BlockResponse.builder()
                .id(BLOCK_ID)
                .blockedId(BLOCKED_USER_ID)
                .reason("Spam")
                .blockedUser(blockedUserSummary)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==================== BLOCK USER ====================

    @Nested
    @DisplayName("POST /blocks - blockUser")
    class BlockUserTests {

        @Test
        @DisplayName("Success - returns 201")
        void blockUser_Success() throws Exception {
            BlockRequest request = BlockRequest.builder()
                    .userId(BLOCKED_USER_ID)
                    .build();

            when(blockService.blockUser(eq(USER_ID), any(BlockRequest.class)))
                    .thenReturn(testBlockResponse);

            mockMvc.perform(post("/blocks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(BLOCK_ID.toString()))
                    .andExpect(jsonPath("$.blockedId").value(BLOCKED_USER_ID.toString()));
        }

        @Test
        @DisplayName("Missing userId - returns 400")
        void blockUser_MissingUserId() throws Exception {
            BlockRequest request = BlockRequest.builder().build();

            mockMvc.perform(post("/blocks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(blockService);
        }

        @Test
        @DisplayName("Cannot block yourself - returns 400")
        void blockUser_CannotBlockYourself() throws Exception {
            BlockRequest request = BlockRequest.builder()
                    .userId(BLOCKED_USER_ID)
                    .build();

            when(blockService.blockUser(eq(USER_ID), any(BlockRequest.class)))
                    .thenThrow(new ValidationException("Cannot block yourself"));

            mockMvc.perform(post("/blocks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Cannot block yourself"));
        }

        @Test
        @DisplayName("Already blocked - returns 400")
        void blockUser_AlreadyBlocked() throws Exception {
            BlockRequest request = BlockRequest.builder()
                    .userId(BLOCKED_USER_ID)
                    .build();

            when(blockService.blockUser(eq(USER_ID), any(BlockRequest.class)))
                    .thenThrow(new ValidationException("User is already blocked"));

            mockMvc.perform(post("/blocks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("User is already blocked"));
        }

        @Test
        @DisplayName("Max block limit reached - returns 400")
        void blockUser_MaxLimitReached() throws Exception {
            BlockRequest request = BlockRequest.builder()
                    .userId(BLOCKED_USER_ID)
                    .build();

            when(blockService.blockUser(eq(USER_ID), any(BlockRequest.class)))
                    .thenThrow(new ValidationException("Maximum block limit reached"));

            mockMvc.perform(post("/blocks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Maximum block limit reached"));
        }

        @Test
        @DisplayName("User not found - returns 400")
        void blockUser_UserNotFound() throws Exception {
            BlockRequest request = BlockRequest.builder()
                    .userId(BLOCKED_USER_ID)
                    .build();

            when(blockService.blockUser(eq(USER_ID), any(BlockRequest.class)))
                    .thenThrow(new ValidationException("User not found"));

            mockMvc.perform(post("/blocks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Missing request body - returns 400")
        void blockUser_MissingBody() throws Exception {
            mockMvc.perform(post("/blocks")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(blockService);
        }

        @Test
        @DisplayName("Malformed JSON - returns 400")
        void blockUser_MalformedJson() throws Exception {
            mockMvc.perform(post("/blocks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\": }"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(blockService);
        }
    }

    // ==================== UNBLOCK USER ====================

    @Nested
    @DisplayName("DELETE /blocks/{userId} - unblockUser")
    class UnblockUserTests {

        @Test
        @DisplayName("Success - returns 204")
        void unblockUser_Success() throws Exception {
            doNothing().when(blockService).unblockUser(USER_ID, BLOCKED_USER_ID);

            mockMvc.perform(delete("/blocks/{userId}", BLOCKED_USER_ID))
                    .andExpect(status().isNoContent());

            verify(blockService).unblockUser(USER_ID, BLOCKED_USER_ID);
        }

        @Test
        @DisplayName("Block not found - returns 400")
        void unblockUser_NotFound() throws Exception {
            doThrow(new ValidationException("Block relationship not found"))
                    .when(blockService).unblockUser(USER_ID, BLOCKED_USER_ID);

            mockMvc.perform(delete("/blocks/{userId}", BLOCKED_USER_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Block relationship not found"));
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void unblockUser_InvalidUuid() throws Exception {
            mockMvc.perform(delete("/blocks/{userId}", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(blockService);
        }
    }

    // ==================== GET BLOCKED USERS ====================

    @Nested
    @DisplayName("GET /blocks - getBlockedUsers")
    class GetBlockedUsersTests {

        @Test
        @DisplayName("Success - returns paginated blocked users")
        void getBlockedUsers_Success() throws Exception {
            PageResponse<BlockResponse> pageResponse = PageResponse.<BlockResponse>builder()
                    .content(List.of(testBlockResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(blockService.getBlockedUsers(USER_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/blocks"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].id").value(BLOCK_ID.toString()))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("Success - empty list")
        void getBlockedUsers_Empty() throws Exception {
            PageResponse<BlockResponse> pageResponse = PageResponse.<BlockResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(blockService.getBlockedUsers(USER_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/blocks"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @DisplayName("Success - custom pagination")
        void getBlockedUsers_CustomPagination() throws Exception {
            PageResponse<BlockResponse> pageResponse = PageResponse.<BlockResponse>builder()
                    .content(Collections.emptyList())
                    .page(5)
                    .size(50)
                    .totalElements(250)
                    .totalPages(5)
                    .build();

            when(blockService.getBlockedUsers(USER_ID, 5, 50)).thenReturn(pageResponse);

            mockMvc.perform(get("/blocks")
                            .param("page", "5")
                            .param("size", "50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(5))
                    .andExpect(jsonPath("$.size").value(50));
        }

        @Test
        @DisplayName("Page below minimum - returns 400")
        void getBlockedUsers_PageBelowMin() throws Exception {
            mockMvc.perform(get("/blocks")
                            .param("page", "-1"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(blockService);
        }

        @Test
        @DisplayName("Page above maximum - returns 400")
        void getBlockedUsers_PageAboveMax() throws Exception {
            mockMvc.perform(get("/blocks")
                            .param("page", "1001"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(blockService);
        }

        @Test
        @DisplayName("Size below minimum - returns 400")
        void getBlockedUsers_SizeBelowMin() throws Exception {
            mockMvc.perform(get("/blocks")
                            .param("size", "0"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(blockService);
        }

        @Test
        @DisplayName("Size above maximum - returns 400")
        void getBlockedUsers_SizeAboveMax() throws Exception {
            mockMvc.perform(get("/blocks")
                            .param("size", "101"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(blockService);
        }

        @Test
        @DisplayName("Boundary - page at max (1000)")
        void getBlockedUsers_PageAtMax() throws Exception {
            PageResponse<BlockResponse> pageResponse = PageResponse.<BlockResponse>builder()
                    .content(Collections.emptyList())
                    .page(1000)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(blockService.getBlockedUsers(USER_ID, 1000, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/blocks")
                            .param("page", "1000"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Boundary - size at max (100)")
        void getBlockedUsers_SizeAtMax() throws Exception {
            PageResponse<BlockResponse> pageResponse = PageResponse.<BlockResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(100)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(blockService.getBlockedUsers(USER_ID, 0, 100)).thenReturn(pageResponse);

            mockMvc.perform(get("/blocks")
                            .param("size", "100"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Boundary - size at min (1)")
        void getBlockedUsers_SizeAtMin() throws Exception {
            PageResponse<BlockResponse> pageResponse = PageResponse.<BlockResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(1)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(blockService.getBlockedUsers(USER_ID, 0, 1)).thenReturn(pageResponse);

            mockMvc.perform(get("/blocks")
                            .param("size", "1"))
                    .andExpect(status().isOk());
        }
    }

    // ==================== GET BLOCKED USER IDS ====================

    @Nested
    @DisplayName("GET /blocks/ids - getBlockedUserIds")
    class GetBlockedUserIdsTests {

        @Test
        @DisplayName("Success - returns user IDs")
        void getBlockedUserIds_Success() throws Exception {
            UUID secondBlockedUser = UUID.randomUUID();
            List<UUID> ids = List.of(BLOCKED_USER_ID, secondBlockedUser);

            when(blockService.getBlockedUserIds(USER_ID)).thenReturn(ids);

            mockMvc.perform(get("/blocks/ids"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0]").value(BLOCKED_USER_ID.toString()))
                    .andExpect(jsonPath("$[1]").value(secondBlockedUser.toString()));
        }

        @Test
        @DisplayName("Success - empty list")
        void getBlockedUserIds_Empty() throws Exception {
            when(blockService.getBlockedUserIds(USER_ID)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/blocks/ids"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Success - single blocked user")
        void getBlockedUserIds_SingleUser() throws Exception {
            List<UUID> ids = List.of(BLOCKED_USER_ID);

            when(blockService.getBlockedUserIds(USER_ID)).thenReturn(ids);

            mockMvc.perform(get("/blocks/ids"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        }
    }

    // ==================== IS BLOCKED ====================

    @Nested
    @DisplayName("GET /blocks/check/{userId} - isBlocked")
    class IsBlockedTests {

        @Test
        @DisplayName("Success - user is blocked returns true")
        void isBlocked_ReturnsTrue() throws Exception {
            when(blockService.isBlocked(USER_ID, BLOCKED_USER_ID)).thenReturn(true);

            mockMvc.perform(get("/blocks/check/{userId}", BLOCKED_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string("true"));
        }

        @Test
        @DisplayName("Success - user is not blocked returns false")
        void isBlocked_ReturnsFalse() throws Exception {
            when(blockService.isBlocked(USER_ID, BLOCKED_USER_ID)).thenReturn(false);

            mockMvc.perform(get("/blocks/check/{userId}", BLOCKED_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string("false"));
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void isBlocked_InvalidUuid() throws Exception {
            mockMvc.perform(get("/blocks/check/{userId}", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(blockService);
        }

        @Test
        @DisplayName("Check self - returns false")
        void isBlocked_CheckSelf() throws Exception {
            when(blockService.isBlocked(USER_ID, USER_ID)).thenReturn(false);

            mockMvc.perform(get("/blocks/check/{userId}", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string("false"));
        }
    }
}