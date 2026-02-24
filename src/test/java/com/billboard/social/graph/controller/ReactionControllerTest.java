package com.billboard.social.graph.controller;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.exception.GlobalExceptionHandler;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.graph.dto.request.SocialRequests.ReactionRequest;
import com.billboard.social.graph.dto.response.SocialResponses.ReactionResponse;
import com.billboard.social.graph.dto.response.SocialResponses.ReactionStatsResponse;
import com.billboard.social.graph.entity.enums.ContentType;
import com.billboard.social.graph.entity.enums.ReactionType;
import com.billboard.social.graph.service.ReactionService;
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

@WebMvcTest(ReactionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ReactionControllerTest {

    private static final Long USER_ID = 1L;
    private static final UUID CONTENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID REACTION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReactionService reactionService;

    private ReactionResponse testReactionResponse;

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

        UserSummary userSummary = UserSummary.builder()
                .id(USER_ID)
                .username("testuser")
                .email("test@gmail.com")
                .build();

        testReactionResponse = ReactionResponse.builder()
                .id(REACTION_ID)
                .userId(USER_ID)
                .contentType(ContentType.POST)
                .contentId(CONTENT_ID)
                .reactionType(ReactionType.LIKE)
                .createdAt(LocalDateTime.now())
                .user(userSummary)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==================== REACT ====================

    @Nested
    @DisplayName("POST /reactions - react")
    class ReactTests {

        @Test
        @DisplayName("Success - LIKE reaction returns 201")
        void react_SuccessLike() throws Exception {
            ReactionRequest request = ReactionRequest.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .reactionType(ReactionType.LIKE)
                    .build();

            when(reactionService.react(eq(USER_ID), any(ReactionRequest.class)))
                    .thenReturn(testReactionResponse);

            mockMvc.perform(post("/reactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(REACTION_ID.toString()))
                    .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                    .andExpect(jsonPath("$.contentType").value("POST"))
                    .andExpect(jsonPath("$.contentId").value(CONTENT_ID.toString()))
                    .andExpect(jsonPath("$.reactionType").value("LIKE"));
        }

        @Test
        @DisplayName("Success - LOVE reaction")
        void react_SuccessLove() throws Exception {
            ReactionRequest request = ReactionRequest.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .reactionType(ReactionType.LOVE)
                    .build();

            testReactionResponse.setReactionType(ReactionType.LOVE);
            when(reactionService.react(eq(USER_ID), any(ReactionRequest.class)))
                    .thenReturn(testReactionResponse);

            mockMvc.perform(post("/reactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.reactionType").value("LOVE"));
        }

        @Test
        @DisplayName("Success - principal is null, userId passed as null")
        void getReactionStats_PrincipalIsNull() throws Exception {
            // Clear security context to simulate unauthenticated request
            SecurityContextHolder.clearContext();

            ReactionStatsResponse statsResponse = ReactionStatsResponse.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .totalCount(10L)
                    .countByType(Map.of(ReactionType.LIKE, 10L))
                    .userReacted(false)
                    .userReactionType(null)
                    .build();

            // When principal is null, userId will be null
            when(reactionService.getReactionStats(null, ContentType.POST, CONTENT_ID))
                    .thenReturn(statsResponse);

            mockMvc.perform(get("/reactions/{contentType}/{contentId}/stats", "POST", CONTENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(10))
                    .andExpect(jsonPath("$.userReacted").value(false));

            // Verify userId was passed as null
            verify(reactionService).getReactionStats(isNull(), eq(ContentType.POST), eq(CONTENT_ID));
        }

        @Test
        @DisplayName("Success - COMMENT content type")
        void react_SuccessCommentContentType() throws Exception {
            ReactionRequest request = ReactionRequest.builder()
                    .contentType(ContentType.COMMENT)
                    .contentId(CONTENT_ID)
                    .reactionType(ReactionType.LIKE)
                    .build();

            testReactionResponse.setContentType(ContentType.COMMENT);
            when(reactionService.react(eq(USER_ID), any(ReactionRequest.class)))
                    .thenReturn(testReactionResponse);

            mockMvc.perform(post("/reactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.contentType").value("COMMENT"));
        }

        @Test
        @DisplayName("Missing contentType - returns 400")
        void react_MissingContentType() throws Exception {
            ReactionRequest request = ReactionRequest.builder()
                    .contentId(CONTENT_ID)
                    .reactionType(ReactionType.LIKE)
                    .build();

            mockMvc.perform(post("/reactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(reactionService);
        }

        @Test
        @DisplayName("Missing contentId - returns 400")
        void react_MissingContentId() throws Exception {
            ReactionRequest request = ReactionRequest.builder()
                    .contentType(ContentType.POST)
                    .reactionType(ReactionType.LIKE)
                    .build();

            mockMvc.perform(post("/reactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(reactionService);
        }

        @Test
        @DisplayName("Missing reactionType - returns 400")
        void react_MissingReactionType() throws Exception {
            ReactionRequest request = ReactionRequest.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .build();

            mockMvc.perform(post("/reactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(reactionService);
        }

        @Test
        @DisplayName("Invalid contentType - returns 400")
        void react_InvalidContentType() throws Exception {
            mockMvc.perform(post("/reactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"contentType\": \"INVALID\", \"contentId\": \"" + CONTENT_ID + "\", \"reactionType\": \"LIKE\"}"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(reactionService);
        }

        @Test
        @DisplayName("Invalid reactionType - returns 400")
        void react_InvalidReactionType() throws Exception {
            mockMvc.perform(post("/reactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"contentType\": \"POST\", \"contentId\": \"" + CONTENT_ID + "\", \"reactionType\": \"INVALID\"}"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(reactionService);
        }

        @Test
        @DisplayName("Content not found - returns 400")
        void react_ContentNotFound() throws Exception {
            ReactionRequest request = ReactionRequest.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .reactionType(ReactionType.LIKE)
                    .build();

            when(reactionService.react(eq(USER_ID), any(ReactionRequest.class)))
                    .thenThrow(new ValidationException("Content not found"));

            mockMvc.perform(post("/reactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Content not found"));
        }

        @Test
        @DisplayName("Missing request body - returns 400")
        void react_MissingBody() throws Exception {
            mockMvc.perform(post("/reactions")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(reactionService);
        }

        @Test
        @DisplayName("Malformed JSON - returns 400")
        void react_MalformedJson() throws Exception {
            mockMvc.perform(post("/reactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"contentType\": }"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(reactionService);
        }
    }

    // ==================== REMOVE REACTION ====================

    @Nested
    @DisplayName("DELETE /reactions/{contentType}/{contentId} - removeReaction")
    class RemoveReactionTests {

        @Test
        @DisplayName("Success - returns 204")
        void removeReaction_Success() throws Exception {
            doNothing().when(reactionService).removeReaction(USER_ID, ContentType.POST, CONTENT_ID);

            mockMvc.perform(delete("/reactions/{contentType}/{contentId}", "POST", CONTENT_ID))
                    .andExpect(status().isNoContent());

            verify(reactionService).removeReaction(USER_ID, ContentType.POST, CONTENT_ID);
        }

        @Test
        @DisplayName("Success - COMMENT content type")
        void removeReaction_CommentContentType() throws Exception {
            doNothing().when(reactionService).removeReaction(USER_ID, ContentType.COMMENT, CONTENT_ID);

            mockMvc.perform(delete("/reactions/{contentType}/{contentId}", "COMMENT", CONTENT_ID))
                    .andExpect(status().isNoContent());

            verify(reactionService).removeReaction(USER_ID, ContentType.COMMENT, CONTENT_ID);
        }

        @Test
        @DisplayName("Reaction not found - returns 400")
        void removeReaction_NotFound() throws Exception {
            doThrow(new ValidationException("Reaction not found"))
                    .when(reactionService).removeReaction(USER_ID, ContentType.POST, CONTENT_ID);

            mockMvc.perform(delete("/reactions/{contentType}/{contentId}", "POST", CONTENT_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Reaction not found"));
        }

        @Test
        @DisplayName("Invalid contentType - returns 400")
        void removeReaction_InvalidContentType() throws Exception {
            mockMvc.perform(delete("/reactions/{contentType}/{contentId}", "INVALID", CONTENT_ID))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(reactionService);
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void removeReaction_InvalidUuid() throws Exception {
            mockMvc.perform(delete("/reactions/{contentType}/{contentId}", "POST", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(reactionService);
        }
    }

    // ==================== GET REACTIONS ====================

    @Nested
    @DisplayName("GET /reactions/{contentType}/{contentId} - getReactions")
    class GetReactionsTests {

        @Test
        @DisplayName("Success - returns paginated reactions")
        void getReactions_Success() throws Exception {
            PageResponse<ReactionResponse> pageResponse = PageResponse.<ReactionResponse>builder()
                    .content(List.of(testReactionResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(reactionService.getReactions(ContentType.POST, CONTENT_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/reactions/{contentType}/{contentId}", "POST", CONTENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].reactionType").value("LIKE"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("Success - empty list")
        void getReactions_Empty() throws Exception {
            PageResponse<ReactionResponse> pageResponse = PageResponse.<ReactionResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(reactionService.getReactions(ContentType.POST, CONTENT_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/reactions/{contentType}/{contentId}", "POST", CONTENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Custom pagination")
        void getReactions_CustomPagination() throws Exception {
            PageResponse<ReactionResponse> pageResponse = PageResponse.<ReactionResponse>builder()
                    .content(Collections.emptyList())
                    .page(5)
                    .size(50)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(reactionService.getReactions(ContentType.POST, CONTENT_ID, 5, 50)).thenReturn(pageResponse);

            mockMvc.perform(get("/reactions/{contentType}/{contentId}", "POST", CONTENT_ID)
                            .param("page", "5")
                            .param("size", "50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(5))
                    .andExpect(jsonPath("$.size").value(50));
        }

        @Test
        @DisplayName("Page below minimum - returns 400")
        void getReactions_PageBelowMin() throws Exception {
            mockMvc.perform(get("/reactions/{contentType}/{contentId}", "POST", CONTENT_ID)
                            .param("page", "-1"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(reactionService);
        }

        @Test
        @DisplayName("Page above maximum - returns 400")
        void getReactions_PageAboveMax() throws Exception {
            mockMvc.perform(get("/reactions/{contentType}/{contentId}", "POST", CONTENT_ID)
                            .param("page", "1001"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(reactionService);
        }

        @Test
        @DisplayName("Size below minimum - returns 400")
        void getReactions_SizeBelowMin() throws Exception {
            mockMvc.perform(get("/reactions/{contentType}/{contentId}", "POST", CONTENT_ID)
                            .param("size", "0"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(reactionService);
        }

        @Test
        @DisplayName("Size above maximum - returns 400")
        void getReactions_SizeAboveMax() throws Exception {
            mockMvc.perform(get("/reactions/{contentType}/{contentId}", "POST", CONTENT_ID)
                            .param("size", "101"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(reactionService);
        }

        @Test
        @DisplayName("Boundary - page at max (1000)")
        void getReactions_PageAtMax() throws Exception {
            PageResponse<ReactionResponse> pageResponse = PageResponse.<ReactionResponse>builder()
                    .content(Collections.emptyList())
                    .page(1000)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(reactionService.getReactions(ContentType.POST, CONTENT_ID, 1000, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/reactions/{contentType}/{contentId}", "POST", CONTENT_ID)
                            .param("page", "1000"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Boundary - size at min (1)")
        void getReactions_SizeAtMin() throws Exception {
            PageResponse<ReactionResponse> pageResponse = PageResponse.<ReactionResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(1)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(reactionService.getReactions(ContentType.POST, CONTENT_ID, 0, 1)).thenReturn(pageResponse);

            mockMvc.perform(get("/reactions/{contentType}/{contentId}", "POST", CONTENT_ID)
                            .param("size", "1"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Boundary - size at max (100)")
        void getReactions_SizeAtMax() throws Exception {
            PageResponse<ReactionResponse> pageResponse = PageResponse.<ReactionResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(100)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(reactionService.getReactions(ContentType.POST, CONTENT_ID, 0, 100)).thenReturn(pageResponse);

            mockMvc.perform(get("/reactions/{contentType}/{contentId}", "POST", CONTENT_ID)
                            .param("size", "100"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Invalid contentType - returns 400")
        void getReactions_InvalidContentType() throws Exception {
            mockMvc.perform(get("/reactions/{contentType}/{contentId}", "INVALID", CONTENT_ID))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(reactionService);
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void getReactions_InvalidUuid() throws Exception {
            mockMvc.perform(get("/reactions/{contentType}/{contentId}", "POST", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(reactionService);
        }
    }

    // ==================== GET REACTIONS BY TYPE ====================

    @Nested
    @DisplayName("GET /reactions/{contentType}/{contentId}/type/{reactionType} - getReactionsByType")
    class GetReactionsByTypeTests {

        @Test
        @DisplayName("Success - returns reactions filtered by LIKE")
        void getReactionsByType_SuccessLike() throws Exception {
            PageResponse<ReactionResponse> pageResponse = PageResponse.<ReactionResponse>builder()
                    .content(List.of(testReactionResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(reactionService.getReactionsByType(ContentType.POST, CONTENT_ID, ReactionType.LIKE, 0, 20))
                    .thenReturn(pageResponse);

            mockMvc.perform(get("/reactions/{contentType}/{contentId}/type/{reactionType}",
                            "POST", CONTENT_ID, "LIKE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].reactionType").value("LIKE"));
        }

        @Test
        @DisplayName("Success - returns reactions filtered by LOVE")
        void getReactionsByType_SuccessLove() throws Exception {
            testReactionResponse.setReactionType(ReactionType.LOVE);
            PageResponse<ReactionResponse> pageResponse = PageResponse.<ReactionResponse>builder()
                    .content(List.of(testReactionResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(reactionService.getReactionsByType(ContentType.POST, CONTENT_ID, ReactionType.LOVE, 0, 20))
                    .thenReturn(pageResponse);

            mockMvc.perform(get("/reactions/{contentType}/{contentId}/type/{reactionType}",
                            "POST", CONTENT_ID, "LOVE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].reactionType").value("LOVE"));
        }

        @Test
        @DisplayName("Success - empty list")
        void getReactionsByType_Empty() throws Exception {
            PageResponse<ReactionResponse> pageResponse = PageResponse.<ReactionResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(reactionService.getReactionsByType(ContentType.POST, CONTENT_ID, ReactionType.LIKE, 0, 20))
                    .thenReturn(pageResponse);

            mockMvc.perform(get("/reactions/{contentType}/{contentId}/type/{reactionType}",
                            "POST", CONTENT_ID, "LIKE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Custom pagination")
        void getReactionsByType_CustomPagination() throws Exception {
            PageResponse<ReactionResponse> pageResponse = PageResponse.<ReactionResponse>builder()
                    .content(Collections.emptyList())
                    .page(3)
                    .size(25)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(reactionService.getReactionsByType(ContentType.POST, CONTENT_ID, ReactionType.LIKE, 3, 25))
                    .thenReturn(pageResponse);

            mockMvc.perform(get("/reactions/{contentType}/{contentId}/type/{reactionType}",
                            "POST", CONTENT_ID, "LIKE")
                            .param("page", "3")
                            .param("size", "25"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Invalid reactionType - returns 400")
        void getReactionsByType_InvalidReactionType() throws Exception {
            mockMvc.perform(get("/reactions/{contentType}/{contentId}/type/{reactionType}",
                            "POST", CONTENT_ID, "INVALID"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(reactionService);
        }

        @Test
        @DisplayName("Invalid contentType - returns 400")
        void getReactionsByType_InvalidContentType() throws Exception {
            mockMvc.perform(get("/reactions/{contentType}/{contentId}/type/{reactionType}",
                            "INVALID", CONTENT_ID, "LIKE"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(reactionService);
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void getReactionsByType_InvalidUuid() throws Exception {
            mockMvc.perform(get("/reactions/{contentType}/{contentId}/type/{reactionType}",
                            "POST", "invalid-uuid", "LIKE"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(reactionService);
        }

        @Test
        @DisplayName("Page above maximum - returns 400")
        void getReactionsByType_PageAboveMax() throws Exception {
            mockMvc.perform(get("/reactions/{contentType}/{contentId}/type/{reactionType}",
                            "POST", CONTENT_ID, "LIKE")
                            .param("page", "1001"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(reactionService);
        }

        @Test
        @DisplayName("Size below minimum - returns 400")
        void getReactionsByType_SizeBelowMin() throws Exception {
            mockMvc.perform(get("/reactions/{contentType}/{contentId}/type/{reactionType}",
                            "POST", CONTENT_ID, "LIKE")
                            .param("size", "0"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(reactionService);
        }
    }

    // ==================== GET REACTION STATS ====================

    @Nested
    @DisplayName("GET /reactions/{contentType}/{contentId}/stats - getReactionStats")
    class GetReactionStatsTests {

        @Test
        @DisplayName("Success - returns reaction stats with user reaction")
        void getReactionStats_SuccessWithUserReaction() throws Exception {
            Map<ReactionType, Long> countByType = new HashMap<>();
            countByType.put(ReactionType.LIKE, 10L);
            countByType.put(ReactionType.LOVE, 5L);

            ReactionStatsResponse statsResponse = ReactionStatsResponse.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .totalCount(15L)
                    .countByType(countByType)
                    .userReacted(true)
                    .userReactionType(ReactionType.LIKE)
                    .build();

            when(reactionService.getReactionStats(USER_ID, ContentType.POST, CONTENT_ID))
                    .thenReturn(statsResponse);

            mockMvc.perform(get("/reactions/{contentType}/{contentId}/stats", "POST", CONTENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contentType").value("POST"))
                    .andExpect(jsonPath("$.contentId").value(CONTENT_ID.toString()))
                    .andExpect(jsonPath("$.totalCount").value(15))
                    .andExpect(jsonPath("$.countByType.LIKE").value(10))
                    .andExpect(jsonPath("$.countByType.LOVE").value(5))
                    .andExpect(jsonPath("$.userReacted").value(true))
                    .andExpect(jsonPath("$.userReactionType").value("LIKE"));
        }

        @Test
        @DisplayName("Success - returns reaction stats without user reaction")
        void getReactionStats_SuccessWithoutUserReaction() throws Exception {
            Map<ReactionType, Long> countByType = new HashMap<>();
            countByType.put(ReactionType.LIKE, 20L);

            ReactionStatsResponse statsResponse = ReactionStatsResponse.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .totalCount(20L)
                    .countByType(countByType)
                    .userReacted(false)
                    .userReactionType(null)
                    .build();

            when(reactionService.getReactionStats(USER_ID, ContentType.POST, CONTENT_ID))
                    .thenReturn(statsResponse);

            mockMvc.perform(get("/reactions/{contentType}/{contentId}/stats", "POST", CONTENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userReacted").value(false))
                    .andExpect(jsonPath("$.userReactionType").doesNotExist());
        }

        @Test
        @DisplayName("Success - zero reactions")
        void getReactionStats_ZeroReactions() throws Exception {
            ReactionStatsResponse statsResponse = ReactionStatsResponse.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .totalCount(0L)
                    .countByType(Collections.emptyMap())
                    .userReacted(false)
                    .userReactionType(null)
                    .build();

            when(reactionService.getReactionStats(USER_ID, ContentType.POST, CONTENT_ID))
                    .thenReturn(statsResponse);

            mockMvc.perform(get("/reactions/{contentType}/{contentId}/stats", "POST", CONTENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(0));
        }

        @Test
        @DisplayName("Success - COMMENT content type")
        void getReactionStats_CommentContentType() throws Exception {
            ReactionStatsResponse statsResponse = ReactionStatsResponse.builder()
                    .contentType(ContentType.COMMENT)
                    .contentId(CONTENT_ID)
                    .totalCount(5L)
                    .countByType(Map.of(ReactionType.LIKE, 5L))
                    .userReacted(false)
                    .build();

            when(reactionService.getReactionStats(USER_ID, ContentType.COMMENT, CONTENT_ID))
                    .thenReturn(statsResponse);

            mockMvc.perform(get("/reactions/{contentType}/{contentId}/stats", "COMMENT", CONTENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contentType").value("COMMENT"));
        }

        @Test
        @DisplayName("Invalid contentType - returns 400")
        void getReactionStats_InvalidContentType() throws Exception {
            mockMvc.perform(get("/reactions/{contentType}/{contentId}/stats", "INVALID", CONTENT_ID))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(reactionService);
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void getReactionStats_InvalidUuid() throws Exception {
            mockMvc.perform(get("/reactions/{contentType}/{contentId}/stats", "POST", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(reactionService);
        }
    }

    // ==================== HAS USER REACTED ====================

    @Nested
    @DisplayName("GET /reactions/{contentType}/{contentId}/check - hasUserReacted")
    class HasUserReactedTests {

        @Test
        @DisplayName("Success - returns true when user has reacted")
        void hasUserReacted_ReturnsTrue() throws Exception {
            when(reactionService.hasUserReacted(USER_ID, ContentType.POST, CONTENT_ID)).thenReturn(true);

            mockMvc.perform(get("/reactions/{contentType}/{contentId}/check", "POST", CONTENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string("true"));
        }

        @Test
        @DisplayName("Success - returns false when user has not reacted")
        void hasUserReacted_ReturnsFalse() throws Exception {
            when(reactionService.hasUserReacted(USER_ID, ContentType.POST, CONTENT_ID)).thenReturn(false);

            mockMvc.perform(get("/reactions/{contentType}/{contentId}/check", "POST", CONTENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string("false"));
        }

        @Test
        @DisplayName("Success - COMMENT content type")
        void hasUserReacted_CommentContentType() throws Exception {
            when(reactionService.hasUserReacted(USER_ID, ContentType.COMMENT, CONTENT_ID)).thenReturn(true);

            mockMvc.perform(get("/reactions/{contentType}/{contentId}/check", "COMMENT", CONTENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string("true"));
        }

        @Test
        @DisplayName("Invalid contentType - returns 400")
        void hasUserReacted_InvalidContentType() throws Exception {
            mockMvc.perform(get("/reactions/{contentType}/{contentId}/check", "INVALID", CONTENT_ID))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(reactionService);
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void hasUserReacted_InvalidUuid() throws Exception {
            mockMvc.perform(get("/reactions/{contentType}/{contentId}/check", "POST", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(reactionService);
        }
    }
}