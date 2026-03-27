package com.billboard.social.gamegroup.controller;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.exception.ForbiddenException;
import com.billboard.social.common.exception.GlobalExceptionHandler;
import com.billboard.social.common.exception.ResourceNotFoundException;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.gamegroup.dto.request.GameGroupRequests.*;
import com.billboard.social.gamegroup.dto.response.GameGroupResponses.*;
import com.billboard.social.gamegroup.service.AuditService;
import com.billboard.social.gamegroup.service.GameAccountLinkService;
import com.billboard.social.gamegroup.service.GameGroupService;
import com.billboard.social.group.dto.request.GroupRequests.JoinGroupRequest;
import com.billboard.social.group.dto.response.GroupResponses.GroupMemberResponse;
import com.billboard.social.group.entity.enums.MemberRole;
import com.billboard.social.group.entity.enums.MemberStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GameGroupController")
class GameGroupControllerTest {

    // ─────────────────────────────────────────────────────────────
    // Mocks & subject
    // ─────────────────────────────────────────────────────────────

    @Mock private GameGroupService       gameGroupService;
    @Mock private AuditService           auditService;
    @Mock private GameAccountLinkService gameAccountLinkService;

    @InjectMocks private GameGroupController controller;

    // ─────────────────────────────────────────────────────────────
    // Infrastructure
    // ─────────────────────────────────────────────────────────────

    private MockMvc      mockMvc;
    private ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────────────
    // Shared constants
    // ─────────────────────────────────────────────────────────────

    private static final UUID GROUP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Long USER_ID  = 42L;
    private static final Long TEAM_ID  = 7L;

    private UserPrincipal principal;

    // ─────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        // ── Jackson ────────────────────────────────────────────────────────────
        // JsonMapper is the modern Jackson 2.10+ builder (com.fasterxml.jackson).
        // JavaTimeModule must be registered explicitly — it is NOT auto-registered
        // in a plain ObjectMapper created outside a Spring context.
        objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();

        // ── MockMvc ────────────────────────────────────────────────────────────
        // MappingJacksonHttpMessageConverter is the Spring Framework 7.0+
        // replacement for the deprecated MappingJackson2HttpMessageConverter.
        // Constructor signature is identical: new MappingJacksonHttpMessageConverter(objectMapper).
        //
        // AuthenticationPrincipalArgumentResolver must be registered explicitly
        // in standaloneSetup — Spring Security resolvers are not auto-wired
        // without a full application context.
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        principal = UserPrincipal.builder()
                .id(USER_ID)
                .username("testuser")
                .email("test@example.com")
                .authorities(List.of())
                .build();

        setAuthenticatedPrincipal(principal);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─────────────────────────────────────────────────────────────
    // Security context helpers
    // ─────────────────────────────────────────────────────────────

    private void setAuthenticatedPrincipal(UserPrincipal p) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    private void clearPrincipal() {
        SecurityContextHolder.clearContext();
    }

    // ─────────────────────────────────────────────────────────────
    // Data helpers
    // ─────────────────────────────────────────────────────────────

    private <T> PageResponse<T> emptyPage() {
        return PageResponse.<T>builder()
                .content(List.of())
                .page(0).size(20).totalElements(0).totalPages(0)
                .first(true).last(true).empty(true)
                .build();
    }

    private GameGroupResponse sampleGameGroupResponse() {
        return GameGroupResponse.builder()
                .id(GROUP_ID)
                .name("Test Group")
                .slug("test-group")
                .gameTag("valorant")
                .region("NA")
                .ownerId(USER_ID)
                .memberCount(5)
                .isVerified(false)
                .createdAt(LocalDateTime.of(2025, 1, 1, 12, 0))
                .build();
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

    // ══════════════════════════════════════════════════════════════
    // 1. POST /api/v1/game-groups — createGameGroup
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST / — createGameGroup")
    class CreateGameGroup {

        private CreateGameGroupRequest validRequest() {
            return CreateGameGroupRequest.builder()
                    .name("My Group").gameTag("valorant").build();
        }

        @Test
        @DisplayName("201 when service succeeds")
        void success_returns201() throws Exception {
            when(gameGroupService.createGameGroup(eq(USER_ID), any()))
                    .thenReturn(sampleGameGroupResponse());

            mockMvc.perform(post("/api/v1/game-groups")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(GROUP_ID.toString()))
                    .andExpect(jsonPath("$.name").value("Test Group"));

            verify(gameGroupService).createGameGroup(eq(USER_ID), any());
        }

        @Test
        @DisplayName("400 when name is blank — @NotBlank fires")
        void blankName_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/game-groups")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(CreateGameGroupRequest.builder()
                                    .name("").gameTag("valorant").build())))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(gameGroupService);
        }

        @Test
        @DisplayName("400 when gameTag is blank — @NotBlank fires")
        void blankGameTag_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/game-groups")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(CreateGameGroupRequest.builder()
                                    .name("Valid Name").gameTag("").build())))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(gameGroupService);
        }

        @Test
        @DisplayName("400 when name exceeds 100 characters — @Size fires")
        void nameTooLong_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/game-groups")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(CreateGameGroupRequest.builder()
                                    .name("A".repeat(101)).gameTag("valorant").build())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 when service throws ValidationException")
        void serviceValidationException_returns400() throws Exception {
            when(gameGroupService.createGameGroup(any(), any()))
                    .thenThrow(new ValidationException("Duplicate group name"));

            mockMvc.perform(post("/api/v1/game-groups")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRequest())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Duplicate group name"));
        }

        @Test
        @DisplayName("400 when request body is missing")
        void missingBody_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/game-groups")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 2. GET /api/v1/game-groups/{groupId} — getGameGroup
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /{groupId} — getGameGroup")
    class GetGameGroup {

        @Test
        @DisplayName("200 with authenticated principal — userId forwarded to service")
        void withPrincipal_returns200() throws Exception {
            when(gameGroupService.getGameGroup(GROUP_ID, USER_ID))
                    .thenReturn(sampleGameGroupResponse());

            mockMvc.perform(get("/api/v1/game-groups/{id}", GROUP_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(GROUP_ID.toString()));

            verify(gameGroupService).getGameGroup(GROUP_ID, USER_ID);
        }

        @Test
        @DisplayName("200 with null principal — userId passed as null (anonymous browse)")
        void withNullPrincipal_passesNullUserId() throws Exception {
            clearPrincipal();

            when(gameGroupService.getGameGroup(GROUP_ID, null))
                    .thenReturn(sampleGameGroupResponse());

            mockMvc.perform(get("/api/v1/game-groups/{id}", GROUP_ID))
                    .andExpect(status().isOk());

            verify(gameGroupService).getGameGroup(GROUP_ID, null);
        }

        @Test
        @DisplayName("400 when service throws ResourceNotFoundException")
        void notFound_returns400() throws Exception {
            when(gameGroupService.getGameGroup(any(), any()))
                    .thenThrow(new ResourceNotFoundException("Group", "id", GROUP_ID));

            mockMvc.perform(get("/api/v1/game-groups/{id}", GROUP_ID))
                    .andExpect(status().isBadRequest());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 3. PUT /api/v1/game-groups/{groupId}/profile — updateGameGroupProfile
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("PUT /{groupId}/profile — updateGameGroupProfile")
    class UpdateGameGroupProfile {

        @Test
        @DisplayName("200 when service succeeds with partial update")
        void success_returns200() throws Exception {
            UpdateGameGroupProfileRequest req = UpdateGameGroupProfileRequest.builder()
                    .region("EU").platform("PC").build();

            when(gameGroupService.updateGameGroupProfile(eq(USER_ID), eq(GROUP_ID), any()))
                    .thenReturn(sampleGameGroupResponse());

            mockMvc.perform(put("/api/v1/game-groups/{id}/profile", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(req)))
                    .andExpect(status().isOk());

            verify(gameGroupService).updateGameGroupProfile(eq(USER_ID), eq(GROUP_ID), any());
        }

        @Test
        @DisplayName("400 when region exceeds 30 characters — @Size fires")
        void regionTooLong_returns400() throws Exception {
            mockMvc.perform(put("/api/v1/game-groups/{id}/profile", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(UpdateGameGroupProfileRequest.builder()
                                    .region("R".repeat(31)).build())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("403 when caller is not admin")
        void forbidden_returns403() throws Exception {
            when(gameGroupService.updateGameGroupProfile(any(), any(), any()))
                    .thenThrow(new ForbiddenException("Admin access required"));

            mockMvc.perform(put("/api/v1/game-groups/{id}/profile", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(UpdateGameGroupProfileRequest.builder().build())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Admin access required"));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 4. GET /api/v1/game-groups/search — searchGameGroups
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /search — searchGameGroups")
    class SearchGameGroups {

        @Test
        @DisplayName("200 with all params provided")
        void withAllParams_returns200() throws Exception {
            when(gameGroupService.searchGameGroups("valorant", "NA", 0, 20))
                    .thenReturn(emptyPage());

            mockMvc.perform(get("/api/v1/game-groups/search")
                            .param("gameTag", "valorant").param("region", "NA")
                            .param("page", "0").param("size", "20"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("200 with default params when none supplied")
        void withDefaultParams_returns200() throws Exception {
            when(gameGroupService.searchGameGroups("", "", 0, 20)).thenReturn(emptyPage());

            mockMvc.perform(get("/api/v1/game-groups/search"))
                    .andExpect(status().isOk());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 5. GET /api/v1/game-groups/{groupId}/embed — getGroupEmbed (PUBLIC)
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /{groupId}/embed — getGroupEmbed (public)")
    class GetGroupEmbed {

        @Test
        @DisplayName("200 — public endpoint, no auth needed")
        void success_returns200() throws Exception {
            GameGroupEmbedResponse embed = GameGroupEmbedResponse.builder()
                    .id(GROUP_ID).name("Test Group")
                    .gameTag("valorant").memberCount(3).isVerified(false).build();

            when(gameGroupService.getGroupEmbed(GROUP_ID)).thenReturn(embed);

            mockMvc.perform(get("/api/v1/game-groups/{id}/embed", GROUP_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(GROUP_ID.toString()))
                    .andExpect(jsonPath("$.gameTag").value("valorant"));
        }

        @Test
        @DisplayName("400 when group not found")
        void notFound_returns400() throws Exception {
            when(gameGroupService.getGroupEmbed(GROUP_ID))
                    .thenThrow(new ResourceNotFoundException("Group", "id", GROUP_ID));

            mockMvc.perform(get("/api/v1/game-groups/{id}/embed", GROUP_ID))
                    .andExpect(status().isBadRequest());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 6. PUT /api/v1/game-groups/{groupId}/scrim-filter — saveScrimFilter
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("PUT /{groupId}/scrim-filter — saveScrimFilter")
    class SaveScrimFilter {

        private ScrimFilterRequest validRequest() {
            return ScrimFilterRequest.builder()
                    .gameTag("valorant").region("NA")
                    .minTeamSize(5).maxTeamSize(5).isActive(true).build();
        }

        @Test
        @DisplayName("200 when service succeeds")
        void success_returns200() throws Exception {
            ScrimFilterResponse resp = ScrimFilterResponse.builder()
                    .id(UUID.randomUUID()).groupId(GROUP_ID)
                    .gameTag("valorant").isActive(true).build();

            when(gameGroupService.saveScrimFilter(eq(USER_ID), eq(GROUP_ID), any()))
                    .thenReturn(resp);

            mockMvc.perform(put("/api/v1/game-groups/{id}/scrim-filter", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.gameTag").value("valorant"));
        }

        @Test
        @DisplayName("400 when gameTag is blank — @NotBlank fires")
        void blankGameTag_returns400() throws Exception {
            mockMvc.perform(put("/api/v1/game-groups/{id}/scrim-filter", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(ScrimFilterRequest.builder().gameTag("").build())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("403 when caller is not admin")
        void notAdmin_returns403() throws Exception {
            when(gameGroupService.saveScrimFilter(any(), any(), any()))
                    .thenThrow(new ForbiddenException("Admin access required"));

            mockMvc.perform(put("/api/v1/game-groups/{id}/scrim-filter", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRequest())))
                    .andExpect(status().isForbidden());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 7. GET /api/v1/game-groups/{groupId}/scrim-filter — getScrimFilter
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /{groupId}/scrim-filter — getScrimFilter")
    class GetScrimFilter {

        @Test
        @DisplayName("200 when filter exists")
        void success_returns200() throws Exception {
            ScrimFilterResponse resp = ScrimFilterResponse.builder()
                    .id(UUID.randomUUID()).groupId(GROUP_ID)
                    .gameTag("cs2").isActive(false).build();

            when(gameGroupService.getScrimFilter(GROUP_ID)).thenReturn(resp);

            mockMvc.perform(get("/api/v1/game-groups/{id}/scrim-filter", GROUP_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.gameTag").value("cs2"));
        }

        @Test
        @DisplayName("400 when filter not found")
        void notFound_returns400() throws Exception {
            when(gameGroupService.getScrimFilter(GROUP_ID))
                    .thenThrow(new ResourceNotFoundException("ScrimFilter", "groupId", GROUP_ID));

            mockMvc.perform(get("/api/v1/game-groups/{id}/scrim-filter", GROUP_ID))
                    .andExpect(status().isBadRequest());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 8. POST /api/v1/game-groups/{groupId}/lfs/broadcast — broadcastLfs
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /{groupId}/lfs/broadcast — broadcastLfs")
    class BroadcastLfs {

        private LfsBroadcastRequest validRequest() {
            return LfsBroadcastRequest.builder()
                    .groupId(GROUP_ID).message("Looking for 5v5").build();
        }

        @Test
        @DisplayName("200 when broadcast succeeds")
        void success_returns200() throws Exception {
            doNothing().when(gameGroupService).broadcastLfs(eq(USER_ID), eq(GROUP_ID), any());

            mockMvc.perform(post("/api/v1/game-groups/{id}/lfs/broadcast", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRequest())))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("400 when groupId is null in body — @NotNull fires")
        void nullGroupId_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/game-groups/{id}/lfs/broadcast", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(LfsBroadcastRequest.builder().groupId(null).build())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 when 5-minute cooldown is still active")
        void cooldownActive_returns400() throws Exception {
            doThrow(new ValidationException(
                    "LFS broadcast cooldown: please wait 5 minutes between broadcasts"))
                    .when(gameGroupService).broadcastLfs(any(), any(), any());

            mockMvc.perform(post("/api/v1/game-groups/{id}/lfs/broadcast", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRequest())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value(Matchers.containsString("cooldown")));
        }

        @Test
        @DisplayName("400 when scrim filter is not active")
        void filterInactive_returns400() throws Exception {
            doThrow(new ValidationException("Scrim filter must be active to broadcast LFS"))
                    .when(gameGroupService).broadcastLfs(any(), any(), any());

            mockMvc.perform(post("/api/v1/game-groups/{id}/lfs/broadcast", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRequest())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("403 when caller is not admin")
        void notAdmin_returns403() throws Exception {
            doThrow(new ForbiddenException("Admin access required"))
                    .when(gameGroupService).broadcastLfs(any(), any(), any());

            mockMvc.perform(post("/api/v1/game-groups/{id}/lfs/broadcast", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRequest())))
                    .andExpect(status().isForbidden());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 9. DELETE /api/v1/game-groups/{groupId}/lfs/broadcast — cancelLfs
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("DELETE /{groupId}/lfs/broadcast — cancelLfs")
    class CancelLfs {

        @Test
        @DisplayName("204 when cancel succeeds")
        void success_returns204() throws Exception {
            doNothing().when(gameGroupService).cancelLfs(USER_ID, GROUP_ID);

            mockMvc.perform(delete("/api/v1/game-groups/{id}/lfs/broadcast", GROUP_ID))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("403 when caller is not admin")
        void notAdmin_returns403() throws Exception {
            doThrow(new ForbiddenException("Admin access required"))
                    .when(gameGroupService).cancelLfs(any(), any());

            mockMvc.perform(delete("/api/v1/game-groups/{id}/lfs/broadcast", GROUP_ID))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("400 when group not found")
        void groupNotFound_returns400() throws Exception {
            doThrow(new ResourceNotFoundException("Group", "id", GROUP_ID))
                    .when(gameGroupService).cancelLfs(any(), any());

            mockMvc.perform(delete("/api/v1/game-groups/{id}/lfs/broadcast", GROUP_ID))
                    .andExpect(status().isBadRequest());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 10. POST /api/v1/game-groups/{groupId}/lfs/match-found — handleMatchFound
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /{groupId}/lfs/match-found — handleMatchFound")
    class HandleMatchFound {

        private static final UUID MATCHED_ID =
                UUID.fromString("00000000-0000-0000-0000-000000000099");

        private LfsMatchFoundRequest validRequest() {
            return LfsMatchFoundRequest.builder()
                    .groupId(GROUP_ID).matchedGroupId(MATCHED_ID).gameTag("valorant").build();
        }

        @Test
        @DisplayName("200 when service succeeds")
        void success_returns200() throws Exception {
            LfsMatchFoundResponse resp = LfsMatchFoundResponse.builder()
                    .groupId(GROUP_ID).matchedGroupId(MATCHED_ID).gameTag("valorant")
                    .matchedAt(LocalDateTime.of(2025, 1, 1, 12, 0)).build();

            when(gameGroupService.handleMatchFound(eq(USER_ID), eq(GROUP_ID), any()))
                    .thenReturn(resp);

            mockMvc.perform(post("/api/v1/game-groups/{id}/lfs/match-found", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.matchedGroupId").value(MATCHED_ID.toString()));
        }

        @Test
        @DisplayName("400 when matchedGroupId is null — @NotNull fires")
        void nullMatchedGroupId_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/game-groups/{id}/lfs/match-found", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(LfsMatchFoundRequest.builder()
                                    .groupId(GROUP_ID).matchedGroupId(null).build())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("403 when caller is not admin")
        void notAdmin_returns403() throws Exception {
            when(gameGroupService.handleMatchFound(any(), any(), any()))
                    .thenThrow(new ForbiddenException("Admin access required"));

            mockMvc.perform(post("/api/v1/game-groups/{id}/lfs/match-found", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRequest())))
                    .andExpect(status().isForbidden());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 11. POST /api/v1/game-groups/{groupId}/teams — linkTeam
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /{groupId}/teams — linkTeam")
    class LinkTeam {

        private LinkTeamRequest validRequest() {
            return LinkTeamRequest.builder().teamId(TEAM_ID).build();
        }

        @Test
        @DisplayName("201 when link succeeds")
        void success_returns201() throws Exception {
            GroupTeamLinkResponse resp = GroupTeamLinkResponse.builder()
                    .id(UUID.randomUUID()).groupId(GROUP_ID).teamId(TEAM_ID)
                    .teamName("Team Alpha").linkedBy(USER_ID)
                    .linkedAt(LocalDateTime.of(2025, 1, 1, 12, 0)).build();

            when(gameGroupService.linkTeam(eq(USER_ID), eq(GROUP_ID), any())).thenReturn(resp);

            mockMvc.perform(post("/api/v1/game-groups/{id}/teams", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.teamId").value(TEAM_ID));
        }

        @Test
        @DisplayName("400 when teamId is null — @NotNull fires")
        void nullTeamId_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/game-groups/{id}/teams", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(LinkTeamRequest.builder().teamId(null).build())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 when team is already linked")
        void alreadyLinked_returns400() throws Exception {
            when(gameGroupService.linkTeam(any(), any(), any()))
                    .thenThrow(new ValidationException("Team is already linked to this group"));

            mockMvc.perform(post("/api/v1/game-groups/{id}/teams", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRequest())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Team is already linked to this group"));
        }

        @Test
        @DisplayName("403 when caller is not a member of the team")
        void notTeamMember_returns403() throws Exception {
            when(gameGroupService.linkTeam(any(), any(), any()))
                    .thenThrow(new ForbiddenException("You must be a member of the team to link it"));

            mockMvc.perform(post("/api/v1/game-groups/{id}/teams", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRequest())))
                    .andExpect(status().isForbidden());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 12. GET /api/v1/game-groups/{groupId}/teams — getLinkedTeams
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /{groupId}/teams — getLinkedTeams")
    class GetLinkedTeams {

        @Test
        @DisplayName("200 with list of linked teams")
        void success_returns200() throws Exception {
            List<GroupTeamLinkResponse> teams = List.of(
                    GroupTeamLinkResponse.builder().id(UUID.randomUUID()).groupId(GROUP_ID)
                            .teamId(1L).teamName("Alpha").linkedBy(USER_ID)
                            .linkedAt(LocalDateTime.of(2025, 1, 1, 12, 0)).build(),
                    GroupTeamLinkResponse.builder().id(UUID.randomUUID()).groupId(GROUP_ID)
                            .teamId(2L).teamName("Beta").linkedBy(USER_ID)
                            .linkedAt(LocalDateTime.of(2025, 1, 2, 12, 0)).build()
            );

            when(gameGroupService.getLinkedTeams(GROUP_ID)).thenReturn(teams);

            mockMvc.perform(get("/api/v1/game-groups/{id}/teams", GROUP_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("200 with empty list when no teams linked")
        void emptyList_returns200() throws Exception {
            when(gameGroupService.getLinkedTeams(GROUP_ID)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/game-groups/{id}/teams", GROUP_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 13. DELETE /api/v1/game-groups/{groupId}/teams/{teamId} — unlinkTeam
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("DELETE /{groupId}/teams/{teamId} — unlinkTeam")
    class UnlinkTeam {

        @Test
        @DisplayName("204 when unlink succeeds")
        void success_returns204() throws Exception {
            doNothing().when(gameGroupService).unlinkTeam(USER_ID, GROUP_ID, TEAM_ID);

            mockMvc.perform(delete("/api/v1/game-groups/{gId}/teams/{tId}", GROUP_ID, TEAM_ID))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("400 when team link not found")
        void linkNotFound_returns400() throws Exception {
            doThrow(new ResourceNotFoundException("Team link not found for teamId=" + TEAM_ID))
                    .when(gameGroupService).unlinkTeam(any(), any(), any());

            mockMvc.perform(delete("/api/v1/game-groups/{gId}/teams/{tId}", GROUP_ID, TEAM_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("403 when caller is not admin")
        void notAdmin_returns403() throws Exception {
            doThrow(new ForbiddenException("Admin access required"))
                    .when(gameGroupService).unlinkTeam(any(), any(), any());

            mockMvc.perform(delete("/api/v1/game-groups/{gId}/teams/{tId}", GROUP_ID, TEAM_ID))
                    .andExpect(status().isForbidden());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 14. POST /api/v1/game-groups/{groupId}/transfer-ownership
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /{groupId}/transfer-ownership — transferOwnership")
    class TransferOwnership {

        private TransferOwnershipRequest validRequest() {
            return TransferOwnershipRequest.builder().newOwnerId(99L).build();
        }

        @Test
        @DisplayName("200 when transfer succeeds")
        void success_returns200() throws Exception {
            doNothing().when(gameGroupService).transferOwnership(eq(USER_ID), eq(GROUP_ID), any());

            mockMvc.perform(post("/api/v1/game-groups/{id}/transfer-ownership", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRequest())))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("400 when newOwnerId is null — @NotNull fires")
        void nullNewOwnerId_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/game-groups/{id}/transfer-ownership", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(TransferOwnershipRequest.builder().newOwnerId(null).build())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("403 when caller is not the current owner")
        void notOwner_returns403() throws Exception {
            doThrow(new ForbiddenException("Only the owner can transfer ownership"))
                    .when(gameGroupService).transferOwnership(any(), any(), any());

            mockMvc.perform(post("/api/v1/game-groups/{id}/transfer-ownership", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("400 when new owner is not an approved member")
        void newOwnerNotApproved_returns400() throws Exception {
            doThrow(new ValidationException("New owner must be an approved member"))
                    .when(gameGroupService).transferOwnership(any(), any(), any());

            mockMvc.perform(post("/api/v1/game-groups/{id}/transfer-ownership", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRequest())))
                    .andExpect(status().isBadRequest());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 15. POST /api/v1/game-groups/accounts/link — linkGameAccount
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /accounts/link — linkGameAccount")
    class LinkGameAccount {

        private LinkGameAccountRequest validRequest() {
            return LinkGameAccountRequest.builder()
                    .gameTag("valorant").gameAccountId("player#1234")
                    .gameAccountName("PlayerOne").build();
        }

        @Test
        @DisplayName("201 when account linked successfully")
        void success_returns201() throws Exception {
            GameAccountLinkResponse resp = GameAccountLinkResponse.builder()
                    .id(UUID.randomUUID()).userId(USER_ID).gameTag("valorant")
                    .gameAccountId("player#1234").verificationStatus("VERIFIED")
                    .createdAt(LocalDateTime.of(2025, 1, 1, 12, 0)).build();

            when(gameAccountLinkService.linkAccount(eq(USER_ID), any())).thenReturn(resp);

            mockMvc.perform(post("/api/v1/game-groups/accounts/link")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));
        }

        @Test
        @DisplayName("400 when gameTag is blank — @NotBlank fires")
        void blankGameTag_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/game-groups/accounts/link")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(LinkGameAccountRequest.builder()
                                    .gameTag("").gameAccountId("player#1234").build())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 when gameAccountId is blank — @NotBlank fires")
        void blankGameAccountId_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/game-groups/accounts/link")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(LinkGameAccountRequest.builder()
                                    .gameTag("valorant").gameAccountId("").build())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 when account is already verified")
        void alreadyVerified_returns400() throws Exception {
            when(gameAccountLinkService.linkAccount(any(), any()))
                    .thenThrow(new ValidationException(
                            "A verified game account for valorant is already linked"));

            mockMvc.perform(post("/api/v1/game-groups/accounts/link")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRequest())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value(Matchers.containsString("already linked")));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 16. GET /api/v1/game-groups/accounts/me — getMyGameAccounts
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /accounts/me — getMyGameAccounts")
    class GetMyGameAccounts {

        @Test
        @DisplayName("200 with account list")
        void success_returns200() throws Exception {
            List<GameAccountLinkResponse> accounts = List.of(
                    GameAccountLinkResponse.builder()
                            .id(UUID.randomUUID()).userId(USER_ID).gameTag("valorant")
                            .gameAccountId("p#1").verificationStatus("VERIFIED")
                            .createdAt(LocalDateTime.of(2025, 1, 1, 12, 0)).build()
            );

            when(gameAccountLinkService.getUserAccounts(USER_ID)).thenReturn(accounts);

            mockMvc.perform(get("/api/v1/game-groups/accounts/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].gameTag").value("valorant"));
        }

        @Test
        @DisplayName("200 with empty list when no accounts linked")
        void noAccounts_returnsEmptyList() throws Exception {
            when(gameAccountLinkService.getUserAccounts(USER_ID)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/game-groups/accounts/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 17. GET /api/v1/game-groups/{groupId}/audit-log — getAuditLog
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /{groupId}/audit-log — getAuditLog")
    class GetAuditLog {

        @Test
        @DisplayName("200 with default pagination")
        void success_returns200() throws Exception {
            PageResponse<AuditLogResponse> page = PageResponse.<AuditLogResponse>builder()
                    .content(List.of(AuditLogResponse.builder()
                            .id(UUID.randomUUID()).groupId(GROUP_ID)
                            .actorUserId(USER_ID).action("GROUP_CREATED")
                            .createdAt(LocalDateTime.of(2025, 1, 1, 12, 0)).build()))
                    .page(0).size(20).totalElements(1).totalPages(1)
                    .first(true).last(true).empty(false).build();

            when(auditService.getAuditLog(USER_ID, GROUP_ID, 0, 20)).thenReturn(page);

            mockMvc.perform(get("/api/v1/game-groups/{id}/audit-log", GROUP_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].action").value("GROUP_CREATED"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("200 with custom pagination params forwarded to service")
        void customPagination_returns200() throws Exception {
            when(auditService.getAuditLog(USER_ID, GROUP_ID, 2, 5))
                    .thenReturn(PageResponse.<AuditLogResponse>builder()
                            .content(List.of()).page(2).size(5)
                            .totalElements(0).totalPages(0)
                            .first(false).last(true).empty(true).build());

            mockMvc.perform(get("/api/v1/game-groups/{id}/audit-log", GROUP_ID)
                            .param("page", "2").param("size", "5"))
                    .andExpect(status().isOk());

            verify(auditService).getAuditLog(USER_ID, GROUP_ID, 2, 5);
        }

        @Test
        @DisplayName("403 when caller is not admin or owner")
        void notAdmin_returns403() throws Exception {
            when(auditService.getAuditLog(any(), any(), anyInt(), anyInt()))
                    .thenThrow(new ForbiddenException("Admin access required to view audit log"));

            mockMvc.perform(get("/api/v1/game-groups/{id}/audit-log", GROUP_ID))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message")
                            .value("Admin access required to view audit log"));
        }

    }

    // ══════════════════════════════════════════════════════════════
    // 18. GET /api/v1/game-groups/{groupId}/member-ids — getMemberIds
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /{groupId}/member-ids — getMemberIds")
    class GetMemberIds {

        @Test
        @DisplayName("200 with member ID list")
        void success_returns200() throws Exception {
            when(gameGroupService.getMemberIds(GROUP_ID)).thenReturn(List.of(1L, 2L, 3L));

            mockMvc.perform(get("/api/v1/game-groups/{id}/member-ids", GROUP_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3));
        }

        @Test
        @DisplayName("200 with empty list when group has no members")
        void noMembers_returnsEmptyList() throws Exception {
            when(gameGroupService.getMemberIds(GROUP_ID)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/game-groups/{id}/member-ids", GROUP_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 19. POST /api/v1/game-groups/{groupId}/join — joinGameGroup
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /{groupId}/join — joinGameGroup")
    class JoinGameGroup {

        private JoinGroupRequest validRequest() {
            return JoinGroupRequest.builder().message("Please let me join").build();
        }

        @Test
        @DisplayName("200 when join request succeeds")
        void success_returns200() throws Exception {
            GroupMemberResponse resp = GroupMemberResponse.builder()
                    .userId(USER_ID)
                    .groupId(GROUP_ID)
                    .role(MemberRole.MEMBER)
                    .status(MemberStatus.APPROVED)
                    .build();

            when(gameGroupService.joinGameGroup(eq(USER_ID), eq(GROUP_ID), any()))
                    .thenReturn(resp);

            mockMvc.perform(post("/api/v1/game-groups/{id}/join", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"));
        }

        @Test
        @DisplayName("400 when rank does not meet minimum requirement")
        void rankTooLow_returns400() throws Exception {
            when(gameGroupService.joinGameGroup(any(), any(), any()))
                    .thenThrow(new ValidationException(
                            "Your rank does not meet the minimum requirement for this group"));

            mockMvc.perform(post("/api/v1/game-groups/{id}/join", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRequest())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value(Matchers.containsString("minimum requirement")));
        }

        @Test
        @DisplayName("400 when rank exceeds maximum allowed")
        void rankTooHigh_returns400() throws Exception {
            when(gameGroupService.joinGameGroup(any(), any(), any()))
                    .thenThrow(new ValidationException(
                            "Your rank exceeds the maximum allowed for this group"));

            mockMvc.perform(post("/api/v1/game-groups/{id}/join", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRequest())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 when verified game account is required but not linked")
        void noGameAccount_returns400() throws Exception {
            when(gameGroupService.joinGameGroup(any(), any(), any()))
                    .thenThrow(new ValidationException(
                            "A verified game account for valorant is required to join this group"));

            mockMvc.perform(post("/api/v1/game-groups/{id}/join", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validRequest())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 when message exceeds 500 characters — @Size fires")
        void messageTooLong_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/game-groups/{id}/join", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(JoinGroupRequest.builder()
                                    .message("M".repeat(501)).build())))
                    .andExpect(status().isBadRequest());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 20. GET /api/v1/game-groups/lfs/search — searchLfsGroups (PUBLIC)
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /lfs/search — searchLfsGroups (public)")
    class SearchLfsGroups {

        @Test
        @DisplayName("200 with all filter params")
        void withAllParams_returns200() throws Exception {
            when(gameGroupService.searchLfsGroups("valorant", "NA", "5v5", 1000, 2000, 0, 20))
                    .thenReturn(emptyPage());

            mockMvc.perform(get("/api/v1/game-groups/lfs/search")
                            .param("gameTag", "valorant").param("region", "NA")
                            .param("format", "5v5")
                            .param("minElo", "1000").param("maxElo", "2000"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("200 with no params — service receives empty strings and nulls")
        void noParams_usesDefaults_returns200() throws Exception {
            when(gameGroupService.searchLfsGroups("", "", "", null, null, 0, 20))
                    .thenReturn(emptyPage());

            mockMvc.perform(get("/api/v1/game-groups/lfs/search"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("200 without ELO params — null passed for minElo and maxElo")
        void withoutEloParams_passesNulls() throws Exception {
            when(gameGroupService.searchLfsGroups("valorant", "", "", null, null, 0, 20))
                    .thenReturn(emptyPage());

            mockMvc.perform(get("/api/v1/game-groups/lfs/search")
                            .param("gameTag", "valorant"))
                    .andExpect(status().isOk());

            verify(gameGroupService).searchLfsGroups("valorant", "", "", null, null, 0, 20);
        }

    }

    // ══════════════════════════════════════════════════════════════
    // 21. GET /api/v1/game-groups/leaderboard — getLeaderboard (PUBLIC)
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /leaderboard — getLeaderboard (public)")
    class GetLeaderboard {

        @Test
        @DisplayName("200 with default WIN_RATE sort")
        void defaultSort_returns200() throws Exception {
            when(gameGroupService.getLeaderboard("valorant", "WIN_RATE", 0, 20))
                    .thenReturn(emptyPage());

            mockMvc.perform(get("/api/v1/game-groups/leaderboard")
                            .param("gameTag", "valorant"))
                    .andExpect(status().isOk());

            verify(gameGroupService).getLeaderboard("valorant", "WIN_RATE", 0, 20);
        }

        @Test
        @DisplayName("200 with SCRIM_COUNT sort")
        void scrimCountSort_returns200() throws Exception {
            when(gameGroupService.getLeaderboard("", "SCRIM_COUNT", 0, 20))
                    .thenReturn(emptyPage());

            mockMvc.perform(get("/api/v1/game-groups/leaderboard")
                            .param("sortBy", "SCRIM_COUNT"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("200 with AVERAGE_ELO sort")
        void averageEloSort_returns200() throws Exception {
            when(gameGroupService.getLeaderboard("", "AVERAGE_ELO", 0, 20))
                    .thenReturn(emptyPage());

            mockMvc.perform(get("/api/v1/game-groups/leaderboard")
                            .param("sortBy", "AVERAGE_ELO"))
                    .andExpect(status().isOk());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 22. GET /api/v1/game-groups/{groupId}/chat-channel — getChatChannel
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /{groupId}/chat-channel — getChatChannel")
    class GetChatChannel {

        @Test
        @DisplayName("200 when channel has been provisioned")
        void channelProvisioned_returns200() throws Exception {
            ChatChannelResponse resp = ChatChannelResponse.builder()
                    .groupId(GROUP_ID).chatChannelId("channel-abc-123").build();

            when(gameGroupService.getChatChannel(GROUP_ID)).thenReturn(resp);

            mockMvc.perform(get("/api/v1/game-groups/{id}/chat-channel", GROUP_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.chatChannelId").value("channel-abc-123"))
                    .andExpect(jsonPath("$.groupId").value(GROUP_ID.toString()));
        }

        @Test
        @DisplayName("400 when channel not yet provisioned")
        void channelNotProvisioned_returns400() throws Exception {
            when(gameGroupService.getChatChannel(GROUP_ID))
                    .thenThrow(new ResourceNotFoundException("ChatChannel", "groupId", GROUP_ID));

            mockMvc.perform(get("/api/v1/game-groups/{id}/chat-channel", GROUP_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 when game group profile not found")
        void groupProfileNotFound_returns400() throws Exception {
            when(gameGroupService.getChatChannel(GROUP_ID))
                    .thenThrow(new ResourceNotFoundException("GameGroupProfile", "groupId", GROUP_ID));

            mockMvc.perform(get("/api/v1/game-groups/{id}/chat-channel", GROUP_ID))
                    .andExpect(status().isBadRequest());
        }
    }
}