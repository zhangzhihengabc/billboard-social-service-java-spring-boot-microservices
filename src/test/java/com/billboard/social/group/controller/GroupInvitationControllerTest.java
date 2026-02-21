package com.billboard.social.group.controller;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.exception.ForbiddenException;
import com.billboard.social.common.exception.GlobalExceptionHandler;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.group.dto.request.GroupRequests.CreateInviteLinkRequest;
import com.billboard.social.group.dto.request.GroupRequests.InviteMemberRequest;
import com.billboard.social.group.dto.response.GroupResponses.GroupMemberResponse;
import com.billboard.social.group.dto.response.GroupResponses.InvitationResponse;
import com.billboard.social.group.entity.enums.MemberRole;
import com.billboard.social.group.entity.enums.MemberStatus;
import com.billboard.social.group.service.GroupInvitationService;
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

@WebMvcTest(GroupInvitationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class GroupInvitationControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID GROUP_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID INVITATION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GroupInvitationService invitationService;

    private InvitationResponse testInvitationResponse;
    private GroupMemberResponse testGroupMemberResponse;

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

        testInvitationResponse = InvitationResponse.builder()
                .id(INVITATION_ID)
                .groupId(GROUP_ID)
                .groupName("Test Group")
                .groupIconUrl("https://example.com/icon.jpg")
                .inviterId(USER_ID)
                .inviterName("testuser")
                .inviteeId(OTHER_USER_ID)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .inviter(UserSummary.builder()
                        .id(USER_ID)
                        .username("testuser")
                        .email("test@gmail.com")
                        .build())
                .build();

        testGroupMemberResponse = GroupMemberResponse.builder()
                .id(UUID.randomUUID())
                .groupId(GROUP_ID)
                .userId(OTHER_USER_ID)
                .role(MemberRole.MEMBER)
                .status(MemberStatus.APPROVED)
                .joinedAt(LocalDateTime.now())
                .postCount(0)
                .contributionScore(0)
                .notificationsEnabled(true)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==================== INVITE MEMBER ====================

    @Nested
    @DisplayName("POST /groups/{groupId}/invitations - inviteMember")
    class InviteMemberTests {

        @Test
        @DisplayName("Success - returns 201")
        void inviteMember_Success() throws Exception {
            InviteMemberRequest request = InviteMemberRequest.builder()
                    .userId(OTHER_USER_ID)
                    .message("Welcome to the group!")
                    .build();

            when(invitationService.inviteMember(eq(USER_ID), eq(GROUP_ID), any(InviteMemberRequest.class)))
                    .thenReturn(testInvitationResponse);

            mockMvc.perform(post("/groups/{groupId}/invitations", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(INVITATION_ID.toString()))
                    .andExpect(jsonPath("$.groupId").value(GROUP_ID.toString()))
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        @DisplayName("Success - without message")
        void inviteMember_WithoutMessage() throws Exception {
            InviteMemberRequest request = InviteMemberRequest.builder()
                    .userId(OTHER_USER_ID)
                    .build();

            when(invitationService.inviteMember(eq(USER_ID), eq(GROUP_ID), any(InviteMemberRequest.class)))
                    .thenReturn(testInvitationResponse);

            mockMvc.perform(post("/groups/{groupId}/invitations", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("User already member - returns 400")
        void inviteMember_AlreadyMember() throws Exception {
            InviteMemberRequest request = InviteMemberRequest.builder()
                    .userId(OTHER_USER_ID)
                    .build();

            when(invitationService.inviteMember(eq(USER_ID), eq(GROUP_ID), any(InviteMemberRequest.class)))
                    .thenThrow(new ValidationException("User is already a member of this group"));

            mockMvc.perform(post("/groups/{groupId}/invitations", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("User is already a member of this group"));
        }

        @Test
        @DisplayName("Group not found - returns 400")
        void inviteMember_GroupNotFound() throws Exception {
            InviteMemberRequest request = InviteMemberRequest.builder()
                    .userId(OTHER_USER_ID)
                    .build();

            when(invitationService.inviteMember(eq(USER_ID), eq(GROUP_ID), any(InviteMemberRequest.class)))
                    .thenThrow(new ValidationException("Group not found"));

            mockMvc.perform(post("/groups/{groupId}/invitations", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Insufficient permissions - returns 403")
        void inviteMember_InsufficientPermissions() throws Exception {
            InviteMemberRequest request = InviteMemberRequest.builder()
                    .userId(OTHER_USER_ID)
                    .build();

            when(invitationService.inviteMember(eq(USER_ID), eq(GROUP_ID), any(InviteMemberRequest.class)))
                    .thenThrow(new ForbiddenException("You do not have permission to invite members"));

            mockMvc.perform(post("/groups/{groupId}/invitations", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid group UUID - returns 400")
        void inviteMember_InvalidGroupUuid() throws Exception {
            InviteMemberRequest request = InviteMemberRequest.builder()
                    .userId(OTHER_USER_ID)
                    .build();

            mockMvc.perform(post("/groups/{groupId}/invitations", "invalid-uuid")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(invitationService);
        }

        @Test
        @DisplayName("Missing request body - returns 400")
        void inviteMember_MissingBody() throws Exception {
            mockMvc.perform(post("/groups/{groupId}/invitations", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(invitationService);
        }

        @Test
        @DisplayName("Malformed JSON - returns 400")
        void inviteMember_MalformedJson() throws Exception {
            mockMvc.perform(post("/groups/{groupId}/invitations", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\": }"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(invitationService);
        }
    }

    // ==================== CREATE INVITE LINK ====================

    @Nested
    @DisplayName("POST /groups/{groupId}/invitations/link - createInviteLink")
    class CreateInviteLinkTests {

        @Test
        @DisplayName("Success - with request body")
        void createInviteLink_SuccessWithBody() throws Exception {
            CreateInviteLinkRequest request = CreateInviteLinkRequest.builder()
                    .expirationDays(7)
                    .maxUses(100)
                    .build();

            testInvitationResponse.setInviteCode("abc123xyz");

            when(invitationService.createInviteLink(eq(USER_ID), eq(GROUP_ID), any(CreateInviteLinkRequest.class)))
                    .thenReturn(testInvitationResponse);

            mockMvc.perform(post("/groups/{groupId}/invitations/link", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.inviteCode").value("abc123xyz"));
        }

        @Test
        @DisplayName("Success - without request body (uses defaults)")
        void createInviteLink_SuccessWithoutBody() throws Exception {
            testInvitationResponse.setInviteCode("abc123xyz");

            when(invitationService.createInviteLink(eq(USER_ID), eq(GROUP_ID), any()))
                    .thenReturn(testInvitationResponse);

            mockMvc.perform(post("/groups/{groupId}/invitations/link", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("Insufficient permissions - returns 403")
        void createInviteLink_InsufficientPermissions() throws Exception {
            when(invitationService.createInviteLink(eq(USER_ID), eq(GROUP_ID), any()))
                    .thenThrow(new ForbiddenException("Admin access required"));

            mockMvc.perform(post("/groups/{groupId}/invitations/link", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid group UUID - returns 400")
        void createInviteLink_InvalidGroupUuid() throws Exception {
            mockMvc.perform(post("/groups/{groupId}/invitations/link", "invalid-uuid")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(invitationService);
        }
    }

    // ==================== GET GROUP INVITATIONS ====================

    @Nested
    @DisplayName("GET /groups/{groupId}/invitations - getGroupInvitations")
    class GetGroupInvitationsTests {

        @Test
        @DisplayName("Success - returns invitations")
        void getGroupInvitations_Success() throws Exception {
            PageResponse<InvitationResponse> pageResponse = PageResponse.<InvitationResponse>builder()
                    .content(List.of(testInvitationResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(invitationService.getGroupInvitations(USER_ID, GROUP_ID, 0, 20))
                    .thenReturn(pageResponse);

            mockMvc.perform(get("/groups/{groupId}/invitations", GROUP_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].id").value(INVITATION_ID.toString()));
        }

        @Test
        @DisplayName("Success - empty list")
        void getGroupInvitations_Empty() throws Exception {
            PageResponse<InvitationResponse> pageResponse = PageResponse.<InvitationResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(invitationService.getGroupInvitations(USER_ID, GROUP_ID, 0, 20))
                    .thenReturn(pageResponse);

            mockMvc.perform(get("/groups/{groupId}/invitations", GROUP_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Success - custom pagination")
        void getGroupInvitations_CustomPagination() throws Exception {
            PageResponse<InvitationResponse> pageResponse = PageResponse.<InvitationResponse>builder()
                    .content(Collections.emptyList())
                    .page(5)
                    .size(50)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(invitationService.getGroupInvitations(USER_ID, GROUP_ID, 5, 50))
                    .thenReturn(pageResponse);

            mockMvc.perform(get("/groups/{groupId}/invitations", GROUP_ID)
                            .param("page", "5")
                            .param("size", "50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(5))
                    .andExpect(jsonPath("$.size").value(50));
        }

        @Test
        @DisplayName("Not moderator - returns 403")
        void getGroupInvitations_NotModerator() throws Exception {
            when(invitationService.getGroupInvitations(USER_ID, GROUP_ID, 0, 20))
                    .thenThrow(new ForbiddenException("Moderator access required"));

            mockMvc.perform(get("/groups/{groupId}/invitations", GROUP_ID))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid group UUID - returns 400")
        void getGroupInvitations_InvalidGroupUuid() throws Exception {
            mockMvc.perform(get("/groups/{groupId}/invitations", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(invitationService);
        }

        @Test
        @DisplayName("Page below minimum - returns 400")
        void getGroupInvitations_PageBelowMin() throws Exception {
            mockMvc.perform(get("/groups/{groupId}/invitations", GROUP_ID)
                            .param("page", "-1"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(invitationService);
        }

        @Test
        @DisplayName("Page above maximum - returns 400")
        void getGroupInvitations_PageAboveMax() throws Exception {
            mockMvc.perform(get("/groups/{groupId}/invitations", GROUP_ID)
                            .param("page", "1001"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(invitationService);
        }

        @Test
        @DisplayName("Size below minimum - returns 400")
        void getGroupInvitations_SizeBelowMin() throws Exception {
            mockMvc.perform(get("/groups/{groupId}/invitations", GROUP_ID)
                            .param("size", "0"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(invitationService);
        }

        @Test
        @DisplayName("Size above maximum - returns 400")
        void getGroupInvitations_SizeAboveMax() throws Exception {
            mockMvc.perform(get("/groups/{groupId}/invitations", GROUP_ID)
                            .param("size", "101"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(invitationService);
        }
    }

    // ==================== GET INVITATION ====================

    @Nested
    @DisplayName("GET /groups/{groupId}/invitations/{invitationId} - getInvitation")
    class GetInvitationTests {

        @Test
        @DisplayName("Success - returns invitation")
        void getInvitation_Success() throws Exception {
            when(invitationService.getInvitation(INVITATION_ID)).thenReturn(testInvitationResponse);

            mockMvc.perform(get("/groups/{groupId}/invitations/{invitationId}", GROUP_ID, INVITATION_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(INVITATION_ID.toString()))
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        @DisplayName("Invitation not found - returns 400")
        void getInvitation_NotFound() throws Exception {
            when(invitationService.getInvitation(INVITATION_ID))
                    .thenThrow(new ValidationException("Invitation not found"));

            mockMvc.perform(get("/groups/{groupId}/invitations/{invitationId}", GROUP_ID, INVITATION_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Invitation not found"));
        }

        @Test
        @DisplayName("Invalid group UUID - returns 400")
        void getInvitation_InvalidGroupUuid() throws Exception {
            mockMvc.perform(get("/groups/{groupId}/invitations/{invitationId}", "invalid-uuid", INVITATION_ID))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(invitationService);
        }

        @Test
        @DisplayName("Invalid invitation UUID - returns 400")
        void getInvitation_InvalidInvitationUuid() throws Exception {
            mockMvc.perform(get("/groups/{groupId}/invitations/{invitationId}", GROUP_ID, "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(invitationService);
        }
    }

    // ==================== CANCEL INVITATION ====================

    @Nested
    @DisplayName("DELETE /groups/{groupId}/invitations/{invitationId} - cancelInvitation")
    class CancelInvitationTests {

        @Test
        @DisplayName("Success - returns 204")
        void cancelInvitation_Success() throws Exception {
            doNothing().when(invitationService).cancelInvitation(USER_ID, GROUP_ID, INVITATION_ID);

            mockMvc.perform(delete("/groups/{groupId}/invitations/{invitationId}", GROUP_ID, INVITATION_ID))
                    .andExpect(status().isNoContent());

            verify(invitationService).cancelInvitation(USER_ID, GROUP_ID, INVITATION_ID);
        }

        @Test
        @DisplayName("Invitation not found - returns 400")
        void cancelInvitation_NotFound() throws Exception {
            doThrow(new ValidationException("Invitation not found"))
                    .when(invitationService).cancelInvitation(USER_ID, GROUP_ID, INVITATION_ID);

            mockMvc.perform(delete("/groups/{groupId}/invitations/{invitationId}", GROUP_ID, INVITATION_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Not moderator - returns 403")
        void cancelInvitation_NotModerator() throws Exception {
            doThrow(new ForbiddenException("Moderator access required"))
                    .when(invitationService).cancelInvitation(USER_ID, GROUP_ID, INVITATION_ID);

            mockMvc.perform(delete("/groups/{groupId}/invitations/{invitationId}", GROUP_ID, INVITATION_ID))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid group UUID - returns 400")
        void cancelInvitation_InvalidGroupUuid() throws Exception {
            mockMvc.perform(delete("/groups/{groupId}/invitations/{invitationId}", "invalid-uuid", INVITATION_ID))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(invitationService);
        }

        @Test
        @DisplayName("Invalid invitation UUID - returns 400")
        void cancelInvitation_InvalidInvitationUuid() throws Exception {
            mockMvc.perform(delete("/groups/{groupId}/invitations/{invitationId}", GROUP_ID, "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(invitationService);
        }
    }

    // ==================== GET MY INVITATIONS ====================

    @Nested
    @DisplayName("GET /invitations - getMyInvitations")
    class GetMyInvitationsTests {

        @Test
        @DisplayName("Success - returns invitations")
        void getMyInvitations_Success() throws Exception {
            PageResponse<InvitationResponse> pageResponse = PageResponse.<InvitationResponse>builder()
                    .content(List.of(testInvitationResponse))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(invitationService.getMyPendingInvitations(USER_ID, 0, 20))
                    .thenReturn(pageResponse);

            mockMvc.perform(get("/invitations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));
        }

        @Test
        @DisplayName("Success - empty list")
        void getMyInvitations_Empty() throws Exception {
            PageResponse<InvitationResponse> pageResponse = PageResponse.<InvitationResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(invitationService.getMyPendingInvitations(USER_ID, 0, 20))
                    .thenReturn(pageResponse);

            mockMvc.perform(get("/invitations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Custom pagination")
        void getMyInvitations_CustomPagination() throws Exception {
            PageResponse<InvitationResponse> pageResponse = PageResponse.<InvitationResponse>builder()
                    .content(Collections.emptyList())
                    .page(3)
                    .size(10)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(invitationService.getMyPendingInvitations(USER_ID, 3, 10))
                    .thenReturn(pageResponse);

            mockMvc.perform(get("/invitations")
                            .param("page", "3")
                            .param("size", "10"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Page below minimum - returns 400")
        void getMyInvitations_PageBelowMin() throws Exception {
            mockMvc.perform(get("/invitations")
                            .param("page", "-1"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(invitationService);
        }

        @Test
        @DisplayName("Page above maximum - returns 400")
        void getMyInvitations_PageAboveMax() throws Exception {
            mockMvc.perform(get("/invitations")
                            .param("page", "1001"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(invitationService);
        }

        @Test
        @DisplayName("Size below minimum - returns 400")
        void getMyInvitations_SizeBelowMin() throws Exception {
            mockMvc.perform(get("/invitations")
                            .param("size", "0"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(invitationService);
        }

        @Test
        @DisplayName("Size above maximum - returns 400")
        void getMyInvitations_SizeAboveMax() throws Exception {
            mockMvc.perform(get("/invitations")
                            .param("size", "101"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(invitationService);
        }

        @Test
        @DisplayName("Boundary - page at max (1000)")
        void getMyInvitations_PageAtMax() throws Exception {
            PageResponse<InvitationResponse> pageResponse = PageResponse.<InvitationResponse>builder()
                    .content(Collections.emptyList())
                    .page(1000)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(invitationService.getMyPendingInvitations(USER_ID, 1000, 20))
                    .thenReturn(pageResponse);

            mockMvc.perform(get("/invitations")
                            .param("page", "1000"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Boundary - size at max (100)")
        void getMyInvitations_SizeAtMax() throws Exception {
            PageResponse<InvitationResponse> pageResponse = PageResponse.<InvitationResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(100)
                    .totalElements(0)
                    .totalPages(0)
                    .build();

            when(invitationService.getMyPendingInvitations(USER_ID, 0, 100))
                    .thenReturn(pageResponse);

            mockMvc.perform(get("/invitations")
                            .param("size", "100"))
                    .andExpect(status().isOk());
        }
    }

    // ==================== GET INVITATION COUNT ====================

    @Nested
    @DisplayName("GET /invitations/count - getInvitationCount")
    class GetInvitationCountTests {

        @Test
        @DisplayName("Success - returns count")
        void getInvitationCount_Success() throws Exception {
            when(invitationService.countPendingInvitations(USER_ID)).thenReturn(5L);

            mockMvc.perform(get("/invitations/count"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("5"));
        }

        @Test
        @DisplayName("Success - zero count")
        void getInvitationCount_Zero() throws Exception {
            when(invitationService.countPendingInvitations(USER_ID)).thenReturn(0L);

            mockMvc.perform(get("/invitations/count"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("0"));
        }

        @Test
        @DisplayName("Success - large count")
        void getInvitationCount_LargeCount() throws Exception {
            when(invitationService.countPendingInvitations(USER_ID)).thenReturn(999999L);

            mockMvc.perform(get("/invitations/count"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("999999"));
        }
    }

    // ==================== ACCEPT INVITATION ====================

    @Nested
    @DisplayName("POST /invitations/{invitationId}/accept - acceptInvitation")
    class AcceptInvitationTests {

        @Test
        @DisplayName("Success - returns group member response")
        void acceptInvitation_Success() throws Exception {
            when(invitationService.acceptInvitation(USER_ID, INVITATION_ID))
                    .thenReturn(testGroupMemberResponse);

            mockMvc.perform(post("/invitations/{invitationId}/accept", INVITATION_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.groupId").value(GROUP_ID.toString()))
                    .andExpect(jsonPath("$.role").value("MEMBER"))
                    .andExpect(jsonPath("$.status").value("APPROVED"));
        }

        @Test
        @DisplayName("Invitation not found - returns 400")
        void acceptInvitation_NotFound() throws Exception {
            when(invitationService.acceptInvitation(USER_ID, INVITATION_ID))
                    .thenThrow(new ValidationException("Invitation not found"));

            mockMvc.perform(post("/invitations/{invitationId}/accept", INVITATION_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Invitation expired - returns 400")
        void acceptInvitation_Expired() throws Exception {
            when(invitationService.acceptInvitation(USER_ID, INVITATION_ID))
                    .thenThrow(new ValidationException("Invitation has expired"));

            mockMvc.perform(post("/invitations/{invitationId}/accept", INVITATION_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Invitation has expired"));
        }

        @Test
        @DisplayName("Invitation not for this user - returns 403")
        void acceptInvitation_NotForUser() throws Exception {
            when(invitationService.acceptInvitation(USER_ID, INVITATION_ID))
                    .thenThrow(new ForbiddenException("This invitation is not for you"));

            mockMvc.perform(post("/invitations/{invitationId}/accept", INVITATION_ID))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid invitation UUID - returns 400")
        void acceptInvitation_InvalidUuid() throws Exception {
            mockMvc.perform(post("/invitations/{invitationId}/accept", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(invitationService);
        }
    }

    // ==================== DECLINE INVITATION ====================

    @Nested
    @DisplayName("POST /invitations/{invitationId}/decline - declineInvitation")
    class DeclineInvitationTests {

        @Test
        @DisplayName("Success - returns 204")
        void declineInvitation_Success() throws Exception {
            doNothing().when(invitationService).declineInvitation(USER_ID, INVITATION_ID);

            mockMvc.perform(post("/invitations/{invitationId}/decline", INVITATION_ID))
                    .andExpect(status().isNoContent());

            verify(invitationService).declineInvitation(USER_ID, INVITATION_ID);
        }

        @Test
        @DisplayName("Invitation not found - returns 400")
        void declineInvitation_NotFound() throws Exception {
            doThrow(new ValidationException("Invitation not found"))
                    .when(invitationService).declineInvitation(USER_ID, INVITATION_ID);

            mockMvc.perform(post("/invitations/{invitationId}/decline", INVITATION_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Invitation expired - returns 400")
        void declineInvitation_Expired() throws Exception {
            doThrow(new ValidationException("Invitation has expired"))
                    .when(invitationService).declineInvitation(USER_ID, INVITATION_ID);

            mockMvc.perform(post("/invitations/{invitationId}/decline", INVITATION_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Invitation not for this user - returns 403")
        void declineInvitation_NotForUser() throws Exception {
            doThrow(new ForbiddenException("This invitation is not for you"))
                    .when(invitationService).declineInvitation(USER_ID, INVITATION_ID);

            mockMvc.perform(post("/invitations/{invitationId}/decline", INVITATION_ID))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid invitation UUID - returns 400")
        void declineInvitation_InvalidUuid() throws Exception {
            mockMvc.perform(post("/invitations/{invitationId}/decline", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(invitationService);
        }
    }

    // ==================== JOIN BY CODE ====================

    @Nested
    @DisplayName("POST /invitations/join - joinByCode")
    class JoinByCodeTests {

        @Test
        @DisplayName("Success - returns group member response")
        void joinByCode_Success() throws Exception {
            when(invitationService.acceptByCode(USER_ID, "abc123xyz"))
                    .thenReturn(testGroupMemberResponse);

            mockMvc.perform(post("/invitations/join")
                            .param("code", "abc123xyz"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.groupId").value(GROUP_ID.toString()))
                    .andExpect(jsonPath("$.status").value("APPROVED"));
        }

        @Test
        @DisplayName("Invalid code - returns 400")
        void joinByCode_InvalidCode() throws Exception {
            when(invitationService.acceptByCode(USER_ID, "invalidcode"))
                    .thenThrow(new ValidationException("Invalid invite code"));

            mockMvc.perform(post("/invitations/join")
                            .param("code", "invalidcode"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Invalid invite code"));
        }

        @Test
        @DisplayName("Code expired - returns 400")
        void joinByCode_Expired() throws Exception {
            when(invitationService.acceptByCode(USER_ID, "expiredcode"))
                    .thenThrow(new ValidationException("Invite code has expired"));

            mockMvc.perform(post("/invitations/join")
                            .param("code", "expiredcode"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Already a member - returns 400")
        void joinByCode_AlreadyMember() throws Exception {
            when(invitationService.acceptByCode(USER_ID, "abc123xyz"))
                    .thenThrow(new ValidationException("You are already a member of this group"));

            mockMvc.perform(post("/invitations/join")
                            .param("code", "abc123xyz"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Missing code - returns 400")
        void joinByCode_MissingCode() throws Exception {
            mockMvc.perform(post("/invitations/join"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(invitationService);
        }

        @Test
        @DisplayName("Blank code - returns 400")
        void joinByCode_BlankCode() throws Exception {
            mockMvc.perform(post("/invitations/join")
                            .param("code", "   "))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(invitationService);
        }

        @Test
        @DisplayName("Empty code - returns 400")
        void joinByCode_EmptyCode() throws Exception {
            mockMvc.perform(post("/invitations/join")
                            .param("code", ""))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(invitationService);
        }
    }

    // ==================== PREVIEW INVITATION ====================

    @Nested
    @DisplayName("GET /invitations/preview - previewInvitation")
    class PreviewInvitationTests {

        @Test
        @DisplayName("Success - returns invitation preview")
        void previewInvitation_Success() throws Exception {
            when(invitationService.getInvitationByCode("abc123xyz"))
                    .thenReturn(testInvitationResponse);

            mockMvc.perform(get("/invitations/preview")
                            .param("code", "abc123xyz"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.groupId").value(GROUP_ID.toString()))
                    .andExpect(jsonPath("$.groupName").value("Test Group"));
        }

        @Test
        @DisplayName("Invalid code - returns 400")
        void previewInvitation_InvalidCode() throws Exception {
            when(invitationService.getInvitationByCode("invalidcode"))
                    .thenThrow(new ValidationException("Invalid invite code"));

            mockMvc.perform(get("/invitations/preview")
                            .param("code", "invalidcode"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Expired code - returns 400")
        void previewInvitation_ExpiredCode() throws Exception {
            when(invitationService.getInvitationByCode("expiredcode"))
                    .thenThrow(new ValidationException("Invite code has expired"));

            mockMvc.perform(get("/invitations/preview")
                            .param("code", "expiredcode"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Missing code - returns 400")
        void previewInvitation_MissingCode() throws Exception {
            mockMvc.perform(get("/invitations/preview"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(invitationService);
        }

        @Test
        @DisplayName("Blank code - returns 400")
        void previewInvitation_BlankCode() throws Exception {
            mockMvc.perform(get("/invitations/preview")
                            .param("code", "   "))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(invitationService);
        }

        @Test
        @DisplayName("Empty code - returns 400")
        void previewInvitation_EmptyCode() throws Exception {
            mockMvc.perform(get("/invitations/preview")
                            .param("code", ""))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(invitationService);
        }
    }
}