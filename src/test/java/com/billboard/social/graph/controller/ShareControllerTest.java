package com.billboard.social.graph.controller;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.exception.GlobalExceptionHandler;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.common.security.JwtAuthenticationFilter;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.graph.dto.request.SocialRequests.ShareRequest;
import com.billboard.social.graph.dto.response.SocialResponses.ShareResponse;
import com.billboard.social.graph.entity.enums.ContentType;
import com.billboard.social.graph.service.ShareService;
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

@WebMvcTest(ShareController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ShareControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CONTENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TARGET_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID SHARE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ShareService shareService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private UserPrincipal userPrincipal;
    private ShareResponse testShareResponse;

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

        UserSummary userSummary = UserSummary.builder()
                .id(USER_ID)
                .username("testuser")
                .email("test@gmail.com")
                .build();

        testShareResponse = ShareResponse.builder()
                .id(SHARE_ID)
                .userId(USER_ID)
                .contentType(ContentType.POST)
                .contentId(CONTENT_ID)
                .targetUserId(null)
                .message("Check this out!")
                .shareToFeed(true)
                .shareToStory(false)
                .isPrivateShare(false)
                .createdAt(LocalDateTime.now())
                .user(userSummary)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==================== SHARE ====================

    @Nested
    @DisplayName("POST /shares - share")
    class ShareTests {

        @Test
        @DisplayName("Success - share to feed returns 201")
        void share_SuccessToFeed() throws Exception {
            ShareRequest request = ShareRequest.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .message("Check this out!")
                    .shareToFeed(true)
                    .shareToStory(false)
                    .build();

            when(shareService.share(eq(USER_ID), any(ShareRequest.class)))
                    .thenReturn(testShareResponse);

            mockMvc.perform(post("/shares")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(SHARE_ID.toString()))
                    .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                    .andExpect(jsonPath("$.contentType").value("POST"))
                    .andExpect(jsonPath("$.contentId").value(CONTENT_ID.toString()))
                    .andExpect(jsonPath("$.message").value("Check this out!"))
                    .andExpect(jsonPath("$.shareToFeed").value(true))
                    .andExpect(jsonPath("$.shareToStory").value(false))
                    .andExpect(jsonPath("$.isPrivateShare").value(false));
        }

        @Test
        @DisplayName("Success - share to story")
        void share_SuccessToStory() throws Exception {
            ShareRequest request = ShareRequest.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .shareToFeed(false)
                    .shareToStory(true)
                    .build();

            testShareResponse.setShareToFeed(false);
            testShareResponse.setShareToStory(true);
            when(shareService.share(eq(USER_ID), any(ShareRequest.class)))
                    .thenReturn(testShareResponse);

            mockMvc.perform(post("/shares")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.shareToStory").value(true));
        }

        @Test
        @DisplayName("Success - private share to target user")
        void share_SuccessPrivateShare() throws Exception {
            ShareRequest request = ShareRequest.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .targetUserId(TARGET_USER_ID)
                    .message("Hey, check this!")
                    .build();

            testShareResponse.setTargetUserId(TARGET_USER_ID);
            testShareResponse.setIsPrivateShare(true);
            when(shareService.share(eq(USER_ID), any(ShareRequest.class)))
                    .thenReturn(testShareResponse);

            mockMvc.perform(post("/shares")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.targetUserId").value(TARGET_USER_ID.toString()))
                    .andExpect(jsonPath("$.isPrivateShare").value(true));
        }

        @Test
        @DisplayName("Success - without message")
        void share_SuccessWithoutMessage() throws Exception {
            ShareRequest request = ShareRequest.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .shareToFeed(true)
                    .build();

            testShareResponse.setMessage(null);
            when(shareService.share(eq(USER_ID), any(ShareRequest.class)))
                    .thenReturn(testShareResponse);

            mockMvc.perform(post("/shares")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.message").doesNotExist());
        }

        @Test
        @DisplayName("Success - COMMENT content type")
        void share_CommentContentType() throws Exception {
            ShareRequest request = ShareRequest.builder()
                    .contentType(ContentType.COMMENT)
                    .contentId(CONTENT_ID)
                    .shareToFeed(true)
                    .build();

            testShareResponse.setContentType(ContentType.COMMENT);
            when(shareService.share(eq(USER_ID), any(ShareRequest.class)))
                    .thenReturn(testShareResponse);

            mockMvc.perform(post("/shares")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.contentType").value("COMMENT"));
        }

        @Test
        @DisplayName("Missing contentType - returns 400")
        void share_MissingContentType() throws Exception {
            ShareRequest request = ShareRequest.builder()
                    .contentId(CONTENT_ID)
                    .shareToFeed(true)
                    .build();

            mockMvc.perform(post("/shares")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(shareService);
        }

        @Test
        @DisplayName("Missing contentId - returns 400")
        void share_MissingContentId() throws Exception {
            ShareRequest request = ShareRequest.builder()
                    .contentType(ContentType.POST)
                    .shareToFeed(true)
                    .build();

            mockMvc.perform(post("/shares")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(shareService);
        }

        @Test
        @DisplayName("Invalid contentType - returns 400")
        void share_InvalidContentType() throws Exception {
            mockMvc.perform(post("/shares")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"contentType\": \"INVALID\", \"contentId\": \"" + CONTENT_ID + "\", \"shareToFeed\": true}"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(shareService);
        }

        @Test
        @DisplayName("Content not found - returns 400")
        void share_ContentNotFound() throws Exception {
            ShareRequest request = ShareRequest.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .shareToFeed(true)
                    .build();

            when(shareService.share(eq(USER_ID), any(ShareRequest.class)))
                    .thenThrow(new ValidationException("Content not found"));

            mockMvc.perform(post("/shares")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Content not found"));
        }

        @Test
        @DisplayName("Target user blocked - returns 400")
        void share_TargetUserBlocked() throws Exception {
            ShareRequest request = ShareRequest.builder()
                    .contentType(ContentType.POST)
                    .contentId(CONTENT_ID)
                    .targetUserId(TARGET_USER_ID)
                    .build();

            when(shareService.share(eq(USER_ID), any(ShareRequest.class)))
                    .thenThrow(new ValidationException("Cannot share to blocked user"));

            mockMvc.perform(post("/shares")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Missing request body - returns 400")
        void share_MissingBody() throws Exception {
            mockMvc.perform(post("/shares")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(shareService);
        }

        @Test
        @DisplayName("Malformed JSON - returns 400")
        void share_MalformedJson() throws Exception {
            mockMvc.perform(post("/shares")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"contentType\": }"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(shareService);
        }
    }

    // ==================== GET SHARES BY CONTENT ====================

    @Nested
    @DisplayName("GET /shares/content/{contentType}/{contentId} - getSharesByContent")
    class GetSharesByContentTests {

        @Test
        @DisplayName("Success - returns paginated shares")
        void getSharesByContent_Success() throws Exception {
            PageResponse<ShareResponse> pageResponse = PageResponse.<ShareResponse>builder()
                    .content(List.of(testShareResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(shareService.getSharesByContent(ContentType.POST, CONTENT_ID, 0, 20))
                    .thenReturn(pageResponse);

            mockMvc.perform(get("/shares/content/{contentType}/{contentId}", "POST", CONTENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].id").value(SHARE_ID.toString()))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("Success - empty list")
        void getSharesByContent_Empty() throws Exception {
            PageResponse<ShareResponse> pageResponse = PageResponse.<ShareResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(shareService.getSharesByContent(ContentType.POST, CONTENT_ID, 0, 20))
                    .thenReturn(pageResponse);

            mockMvc.perform(get("/shares/content/{contentType}/{contentId}", "POST", CONTENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Custom pagination")
        void getSharesByContent_CustomPagination() throws Exception {
            PageResponse<ShareResponse> pageResponse = PageResponse.<ShareResponse>builder()
                    .content(Collections.emptyList())
                    .page(5)
                    .size(50)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(shareService.getSharesByContent(ContentType.POST, CONTENT_ID, 5, 50))
                    .thenReturn(pageResponse);

            mockMvc.perform(get("/shares/content/{contentType}/{contentId}", "POST", CONTENT_ID)
                            .param("page", "5")
                            .param("size", "50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(5))
                    .andExpect(jsonPath("$.size").value(50));
        }

        @Test
        @DisplayName("COMMENT content type")
        void getSharesByContent_CommentContentType() throws Exception {
            testShareResponse.setContentType(ContentType.COMMENT);
            PageResponse<ShareResponse> pageResponse = PageResponse.<ShareResponse>builder()
                    .content(List.of(testShareResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(shareService.getSharesByContent(ContentType.COMMENT, CONTENT_ID, 0, 20))
                    .thenReturn(pageResponse);

            mockMvc.perform(get("/shares/content/{contentType}/{contentId}", "COMMENT", CONTENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].contentType").value("COMMENT"));
        }

        @Test
        @DisplayName("Page below minimum - returns 400")
        void getSharesByContent_PageBelowMin() throws Exception {
            mockMvc.perform(get("/shares/content/{contentType}/{contentId}", "POST", CONTENT_ID)
                            .param("page", "-1"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(shareService);
        }

        @Test
        @DisplayName("Page above maximum - returns 400")
        void getSharesByContent_PageAboveMax() throws Exception {
            mockMvc.perform(get("/shares/content/{contentType}/{contentId}", "POST", CONTENT_ID)
                            .param("page", "1001"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(shareService);
        }

        @Test
        @DisplayName("Size below minimum - returns 400")
        void getSharesByContent_SizeBelowMin() throws Exception {
            mockMvc.perform(get("/shares/content/{contentType}/{contentId}", "POST", CONTENT_ID)
                            .param("size", "0"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(shareService);
        }

        @Test
        @DisplayName("Size above maximum - returns 400")
        void getSharesByContent_SizeAboveMax() throws Exception {
            mockMvc.perform(get("/shares/content/{contentType}/{contentId}", "POST", CONTENT_ID)
                            .param("size", "101"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(shareService);
        }

        @Test
        @DisplayName("Boundary - page at max (1000)")
        void getSharesByContent_PageAtMax() throws Exception {
            PageResponse<ShareResponse> pageResponse = PageResponse.<ShareResponse>builder()
                    .content(Collections.emptyList())
                    .page(1000)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(shareService.getSharesByContent(ContentType.POST, CONTENT_ID, 1000, 20))
                    .thenReturn(pageResponse);

            mockMvc.perform(get("/shares/content/{contentType}/{contentId}", "POST", CONTENT_ID)
                            .param("page", "1000"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Boundary - size at min (1)")
        void getSharesByContent_SizeAtMin() throws Exception {
            PageResponse<ShareResponse> pageResponse = PageResponse.<ShareResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(1)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(shareService.getSharesByContent(ContentType.POST, CONTENT_ID, 0, 1))
                    .thenReturn(pageResponse);

            mockMvc.perform(get("/shares/content/{contentType}/{contentId}", "POST", CONTENT_ID)
                            .param("size", "1"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Boundary - size at max (100)")
        void getSharesByContent_SizeAtMax() throws Exception {
            PageResponse<ShareResponse> pageResponse = PageResponse.<ShareResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(100)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(shareService.getSharesByContent(ContentType.POST, CONTENT_ID, 0, 100))
                    .thenReturn(pageResponse);

            mockMvc.perform(get("/shares/content/{contentType}/{contentId}", "POST", CONTENT_ID)
                            .param("size", "100"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Invalid contentType - returns 400")
        void getSharesByContent_InvalidContentType() throws Exception {
            mockMvc.perform(get("/shares/content/{contentType}/{contentId}", "INVALID", CONTENT_ID))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(shareService);
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void getSharesByContent_InvalidUuid() throws Exception {
            mockMvc.perform(get("/shares/content/{contentType}/{contentId}", "POST", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(shareService);
        }
    }

    // ==================== GET SHARES BY USER ====================

    @Nested
    @DisplayName("GET /shares/user - getSharesByUser")
    class GetSharesByUserTests {

        @Test
        @DisplayName("Success - returns paginated user shares")
        void getSharesByUser_Success() throws Exception {
            PageResponse<ShareResponse> pageResponse = PageResponse.<ShareResponse>builder()
                    .content(List.of(testShareResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(shareService.getSharesByUser(USER_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/shares/user"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].userId").value(USER_ID.toString()))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("Success - empty list")
        void getSharesByUser_Empty() throws Exception {
            PageResponse<ShareResponse> pageResponse = PageResponse.<ShareResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(shareService.getSharesByUser(USER_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/shares/user"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Custom pagination")
        void getSharesByUser_CustomPagination() throws Exception {
            PageResponse<ShareResponse> pageResponse = PageResponse.<ShareResponse>builder()
                    .content(Collections.emptyList())
                    .page(3)
                    .size(25)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(shareService.getSharesByUser(USER_ID, 3, 25)).thenReturn(pageResponse);

            mockMvc.perform(get("/shares/user")
                            .param("page", "3")
                            .param("size", "25"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(3))
                    .andExpect(jsonPath("$.size").value(25));
        }

        @Test
        @DisplayName("Page below minimum - returns 400")
        void getSharesByUser_PageBelowMin() throws Exception {
            mockMvc.perform(get("/shares/user")
                            .param("page", "-1"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(shareService);
        }

        @Test
        @DisplayName("Page above maximum - returns 400")
        void getSharesByUser_PageAboveMax() throws Exception {
            mockMvc.perform(get("/shares/user")
                            .param("page", "1001"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(shareService);
        }

        @Test
        @DisplayName("Size below minimum - returns 400")
        void getSharesByUser_SizeBelowMin() throws Exception {
            mockMvc.perform(get("/shares/user")
                            .param("size", "0"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(shareService);
        }

        @Test
        @DisplayName("Size above maximum - returns 400")
        void getSharesByUser_SizeAboveMax() throws Exception {
            mockMvc.perform(get("/shares/user")
                            .param("size", "101"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(shareService);
        }

        @Test
        @DisplayName("Boundary - page at max (1000)")
        void getSharesByUser_PageAtMax() throws Exception {
            PageResponse<ShareResponse> pageResponse = PageResponse.<ShareResponse>builder()
                    .content(Collections.emptyList())
                    .page(1000)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(shareService.getSharesByUser(USER_ID, 1000, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/shares/user")
                            .param("page", "1000"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Boundary - size at min (1)")
        void getSharesByUser_SizeAtMin() throws Exception {
            PageResponse<ShareResponse> pageResponse = PageResponse.<ShareResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(1)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(shareService.getSharesByUser(USER_ID, 0, 1)).thenReturn(pageResponse);

            mockMvc.perform(get("/shares/user")
                            .param("size", "1"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Boundary - size at max (100)")
        void getSharesByUser_SizeAtMax() throws Exception {
            PageResponse<ShareResponse> pageResponse = PageResponse.<ShareResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(100)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(shareService.getSharesByUser(USER_ID, 0, 100)).thenReturn(pageResponse);

            mockMvc.perform(get("/shares/user")
                            .param("size", "100"))
                    .andExpect(status().isOk());
        }
    }

    // ==================== GET SHARE COUNT ====================

    @Nested
    @DisplayName("GET /shares/count/{contentType}/{contentId} - getShareCount")
    class GetShareCountTests {

        @Test
        @DisplayName("Success - returns count")
        void getShareCount_Success() throws Exception {
            when(shareService.getShareCount(ContentType.POST, CONTENT_ID)).thenReturn(25L);

            mockMvc.perform(get("/shares/count/{contentType}/{contentId}", "POST", CONTENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string("25"));
        }

        @Test
        @DisplayName("Success - returns zero")
        void getShareCount_Zero() throws Exception {
            when(shareService.getShareCount(ContentType.POST, CONTENT_ID)).thenReturn(0L);

            mockMvc.perform(get("/shares/count/{contentType}/{contentId}", "POST", CONTENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string("0"));
        }

        @Test
        @DisplayName("Success - returns large count")
        void getShareCount_LargeCount() throws Exception {
            when(shareService.getShareCount(ContentType.POST, CONTENT_ID)).thenReturn(10000L);

            mockMvc.perform(get("/shares/count/{contentType}/{contentId}", "POST", CONTENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string("10000"));
        }

        @Test
        @DisplayName("Success - COMMENT content type")
        void getShareCount_CommentContentType() throws Exception {
            when(shareService.getShareCount(ContentType.COMMENT, CONTENT_ID)).thenReturn(5L);

            mockMvc.perform(get("/shares/count/{contentType}/{contentId}", "COMMENT", CONTENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string("5"));
        }

        @Test
        @DisplayName("Invalid contentType - returns 400")
        void getShareCount_InvalidContentType() throws Exception {
            mockMvc.perform(get("/shares/count/{contentType}/{contentId}", "INVALID", CONTENT_ID))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(shareService);
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void getShareCount_InvalidUuid() throws Exception {
            mockMvc.perform(get("/shares/count/{contentType}/{contentId}", "POST", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(shareService);
        }
    }
}