package com.billboard.social.group.controller;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.exception.ForbiddenException;
import com.billboard.social.common.exception.GlobalExceptionHandler;
import com.billboard.social.common.exception.ResourceNotFoundException;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.common.security.UserPrincipal;
import com.billboard.social.group.dto.request.GroupRequests.*;
import com.billboard.social.group.dto.response.GroupResponses.*;
import com.billboard.social.group.entity.enums.MemberRole;
import com.billboard.social.group.entity.enums.MemberStatus;
import com.billboard.social.group.service.GroupMemberService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GroupMemberController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class GroupMemberControllerTest {

    private static final Long USER_ID = 1L;
    private static final UUID GROUP_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID MEMBER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Long TARGET_USER_ID = 4L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GroupMemberService memberService;

    private GroupMemberResponse testMemberResponse;

    @BeforeEach
    void setUp() {
        UserPrincipal principal = UserPrincipal.builder()
                .id(USER_ID)
                .username("testuser")
                .email("test@example.com")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );

        UserSummary testUserSummary = UserSummary.builder()
                .id(USER_ID)
                .username("testuser")
                .email("test@gmail.com")
                .build();

        testMemberResponse = GroupMemberResponse.builder()
                .id(MEMBER_ID)
                .groupId(GROUP_ID)
                .userId(USER_ID)
                .role(MemberRole.MEMBER)
                .status(MemberStatus.APPROVED)
                .joinedAt(LocalDateTime.now())
                .postCount(10)
                .contributionScore(100)
                .notificationsEnabled(true)
                .user(testUserSummary)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==================== JOIN GROUP ====================

    @Nested
    @DisplayName("POST /api/v1/groups/{groupId}/members/join")
    class JoinGroupTests {

        @Test
        @DisplayName("Success - with request body")
        void joinGroup_SuccessWithBody() throws Exception {
            JoinGroupRequest request = new JoinGroupRequest();

            when(memberService.joinGroup(eq(USER_ID), eq(GROUP_ID), any(JoinGroupRequest.class)))
                    .thenReturn(testMemberResponse);

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/join", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(MEMBER_ID.toString()))
                    .andExpect(jsonPath("$.role").value("MEMBER"));
        }

        @Test
        @DisplayName("Success - without request body (uses default)")
        void joinGroup_SuccessWithoutBody() throws Exception {
            when(memberService.joinGroup(eq(USER_ID), eq(GROUP_ID), any(JoinGroupRequest.class)))
                    .thenReturn(testMemberResponse);

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/join", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(MEMBER_ID.toString()));
        }

        @Test
        @DisplayName("Group not found - returns 400")
        void joinGroup_GroupNotFound() throws Exception {
            when(memberService.joinGroup(eq(USER_ID), eq(GROUP_ID), any(JoinGroupRequest.class)))
                    .thenThrow(new ResourceNotFoundException("Group", "id", GROUP_ID));

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/join", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Already a member - returns 400")
        void joinGroup_AlreadyMember() throws Exception {
            when(memberService.joinGroup(eq(USER_ID), eq(GROUP_ID), any(JoinGroupRequest.class)))
                    .thenThrow(new ValidationException("You are already a member of this group"));

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/join", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void joinGroup_InvalidUuid() throws Exception {
            mockMvc.perform(post("/api/v1/groups/{groupId}/members/join", "invalid-uuid")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }
    }

    // ==================== LEAVE GROUP ====================

    @Nested
    @DisplayName("POST /api/v1/groups/{groupId}/members/leave")
    class LeaveGroupTests {

        @Test
        @DisplayName("Success - returns 204")
        void leaveGroup_Success() throws Exception {
            doNothing().when(memberService).leaveGroup(USER_ID, GROUP_ID);

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/leave", GROUP_ID))
                    .andExpect(status().isNoContent());

            verify(memberService).leaveGroup(USER_ID, GROUP_ID);
        }

        @Test
        @DisplayName("Not a member - returns 400")
        void leaveGroup_NotMember() throws Exception {
            doThrow(new ResourceNotFoundException("Member", "userId", USER_ID))
                    .when(memberService).leaveGroup(USER_ID, GROUP_ID);

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/leave", GROUP_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Owner cannot leave - returns 400")
        void leaveGroup_OwnerCannotLeave() throws Exception {
            doThrow(new ValidationException("Owner cannot leave the group"))
                    .when(memberService).leaveGroup(USER_ID, GROUP_ID);

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/leave", GROUP_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void leaveGroup_InvalidUuid() throws Exception {
            mockMvc.perform(post("/api/v1/groups/{groupId}/members/leave", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }
    }

    // ==================== GET MEMBERS ====================

    @Nested
    @DisplayName("GET /api/v1/groups/{groupId}/members")
    class GetMembersTests {

        @Test
        @DisplayName("Success - returns members")
        void getMembers_Success() throws Exception {
            PageResponse<GroupMemberResponse> pageResponse = PageResponse.from(
                    new PageImpl<>(List.of(testMemberResponse), PageRequest.of(0, 20), 1),
                    r -> r
            );

            when(memberService.getMembers(GROUP_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/groups/{groupId}/members", GROUP_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].id").value(MEMBER_ID.toString()));
        }

        @Test
        @DisplayName("Success - empty list")
        void getMembers_Empty() throws Exception {
            PageResponse<GroupMemberResponse> emptyResponse = PageResponse.from(
                    new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0),
                    r -> (GroupMemberResponse) r
            );

            when(memberService.getMembers(GROUP_ID, 0, 20)).thenReturn(emptyResponse);

            mockMvc.perform(get("/api/v1/groups/{groupId}/members", GROUP_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Success - custom pagination")
        void getMembers_CustomPagination() throws Exception {
            PageResponse<GroupMemberResponse> pageResponse = PageResponse.from(
                    new PageImpl<>(List.of(testMemberResponse), PageRequest.of(2, 10), 30),
                    r -> r
            );

            when(memberService.getMembers(GROUP_ID, 2, 10)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/groups/{groupId}/members", GROUP_ID)
                            .param("page", "2")
                            .param("size", "10"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Page below minimum - returns 400")
        void getMembers_PageBelowMin() throws Exception {
            mockMvc.perform(get("/api/v1/groups/{groupId}/members", GROUP_ID)
                            .param("page", "-1"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }

        @Test
        @DisplayName("Page above maximum - returns 400")
        void getMembers_PageAboveMax() throws Exception {
            mockMvc.perform(get("/api/v1/groups/{groupId}/members", GROUP_ID)
                            .param("page", "1001"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }

        @Test
        @DisplayName("Size below minimum - returns 400")
        void getMembers_SizeBelowMin() throws Exception {
            mockMvc.perform(get("/api/v1/groups/{groupId}/members", GROUP_ID)
                            .param("size", "0"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }

        @Test
        @DisplayName("Size above maximum - returns 400")
        void getMembers_SizeAboveMax() throws Exception {
            mockMvc.perform(get("/api/v1/groups/{groupId}/members", GROUP_ID)
                            .param("size", "101"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void getMembers_InvalidUuid() throws Exception {
            mockMvc.perform(get("/api/v1/groups/{groupId}/members", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }
    }

    // ==================== GET MY MEMBERSHIP ====================

    @Nested
    @DisplayName("GET /api/v1/groups/{groupId}/members/me")
    class GetMyMembershipTests {

        @Test
        @DisplayName("Success - returns membership")
        void getMyMembership_Success() throws Exception {
            when(memberService.getMember(GROUP_ID, USER_ID)).thenReturn(testMemberResponse);

            mockMvc.perform(get("/api/v1/groups/{groupId}/members/me", GROUP_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(MEMBER_ID.toString()))
                    .andExpect(jsonPath("$.userId").value(USER_ID.toString()));
        }

        @Test
        @DisplayName("Not a member - returns 400")
        void getMyMembership_NotMember() throws Exception {
            when(memberService.getMember(GROUP_ID, USER_ID))
                    .thenThrow(new ResourceNotFoundException("Member", "userId", USER_ID));

            mockMvc.perform(get("/api/v1/groups/{groupId}/members/me", GROUP_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void getMyMembership_InvalidUuid() throws Exception {
            mockMvc.perform(get("/api/v1/groups/{groupId}/members/me", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }
    }

    // ==================== GET MEMBER ====================

    @Nested
    @DisplayName("GET /api/v1/groups/{groupId}/members/{userId}")
    class GetMemberTests {

        @Test
        @DisplayName("Success - returns member")
        void getMember_Success() throws Exception {
            when(memberService.getMember(GROUP_ID, TARGET_USER_ID)).thenReturn(testMemberResponse);

            mockMvc.perform(get("/api/v1/groups/{groupId}/members/{userId}", GROUP_ID, TARGET_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(MEMBER_ID.toString()));
        }

        @Test
        @DisplayName("Member not found - returns 400")
        void getMember_NotFound() throws Exception {
            when(memberService.getMember(GROUP_ID, TARGET_USER_ID))
                    .thenThrow(new ResourceNotFoundException("Member", "userId", TARGET_USER_ID));

            mockMvc.perform(get("/api/v1/groups/{groupId}/members/{userId}", GROUP_ID, TARGET_USER_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Invalid group UUID - returns 400")
        void getMember_InvalidGroupUuid() throws Exception {
            mockMvc.perform(get("/api/v1/groups/{groupId}/members/{userId}", "invalid-uuid", TARGET_USER_ID))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }

        @Test
        @DisplayName("Invalid user UUID - returns 400")
        void getMember_InvalidUserUuid() throws Exception {
            mockMvc.perform(get("/api/v1/groups/{groupId}/members/{userId}", GROUP_ID, "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }
    }

    // ==================== GET PENDING MEMBERS ====================

    @Nested
    @DisplayName("GET /api/v1/groups/{groupId}/members/pending")
    class GetPendingMembersTests {

        @Test
        @DisplayName("Success - returns pending members")
        void getPendingMembers_Success() throws Exception {
            GroupMemberResponse pendingMember = GroupMemberResponse.builder()
                    .id(MEMBER_ID)
                    .groupId(GROUP_ID)
                    .userId(TARGET_USER_ID)
                    .role(MemberRole.MEMBER)
                    .status(MemberStatus.PENDING)
                    .build();

            PageResponse<GroupMemberResponse> pageResponse = PageResponse.from(
                    new PageImpl<>(List.of(pendingMember), PageRequest.of(0, 20), 1),
                    r -> r
            );

            when(memberService.getPendingMembers(USER_ID, GROUP_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/groups/{groupId}/members/pending", GROUP_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].status").value("PENDING"));
        }

        @Test
        @DisplayName("Success - empty list")
        void getPendingMembers_Empty() throws Exception {
            PageResponse<GroupMemberResponse> emptyResponse = PageResponse.from(
                    new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0),
                    r -> (GroupMemberResponse) r
            );

            when(memberService.getPendingMembers(USER_ID, GROUP_ID, 0, 20)).thenReturn(emptyResponse);

            mockMvc.perform(get("/api/v1/groups/{groupId}/members/pending", GROUP_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("Not moderator - returns 403")
        void getPendingMembers_NotModerator() throws Exception {
            when(memberService.getPendingMembers(USER_ID, GROUP_ID, 0, 20))
                    .thenThrow(new ForbiddenException("Moderator access required"));

            mockMvc.perform(get("/api/v1/groups/{groupId}/members/pending", GROUP_ID))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Page above maximum - returns 400")
        void getPendingMembers_PageAboveMax() throws Exception {
            mockMvc.perform(get("/api/v1/groups/{groupId}/members/pending", GROUP_ID)
                            .param("page", "1001"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }

        @Test
        @DisplayName("Size below minimum - returns 400")
        void getPendingMembers_SizeBelowMin() throws Exception {
            mockMvc.perform(get("/api/v1/groups/{groupId}/members/pending", GROUP_ID)
                            .param("size", "0"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void getPendingMembers_InvalidUuid() throws Exception {
            mockMvc.perform(get("/api/v1/groups/{groupId}/members/pending", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }
    }

    // ==================== GET BANNED MEMBERS ====================

    @Nested
    @DisplayName("GET /api/v1/groups/{groupId}/members/banned")
    class GetBannedMembersTests {

        @Test
        @DisplayName("Success - returns banned members")
        void getBannedMembers_Success() throws Exception {
            GroupMemberResponse bannedMember = GroupMemberResponse.builder()
                    .id(MEMBER_ID)
                    .groupId(GROUP_ID)
                    .userId(TARGET_USER_ID)
                    .role(MemberRole.MEMBER)
                    .status(MemberStatus.BANNED)
                    .build();

            PageResponse<GroupMemberResponse> pageResponse = PageResponse.from(
                    new PageImpl<>(List.of(bannedMember), PageRequest.of(0, 20), 1),
                    r -> r
            );

            when(memberService.getBannedMembers(USER_ID, GROUP_ID, 0, 20)).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/groups/{groupId}/members/banned", GROUP_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].status").value("BANNED"));
        }

        @Test
        @DisplayName("Not moderator - returns 403")
        void getBannedMembers_NotModerator() throws Exception {
            when(memberService.getBannedMembers(USER_ID, GROUP_ID, 0, 20))
                    .thenThrow(new ForbiddenException("Moderator access required"));

            mockMvc.perform(get("/api/v1/groups/{groupId}/members/banned", GROUP_ID))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void getBannedMembers_InvalidUuid() throws Exception {
            mockMvc.perform(get("/api/v1/groups/{groupId}/members/banned", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }
    }

    // ==================== GET ADMINS ====================

    @Nested
    @DisplayName("GET /api/v1/groups/{groupId}/members/admins")
    class GetAdminsTests {

        @Test
        @DisplayName("Success - returns admins and moderators")
        void getAdmins_Success() throws Exception {
            GroupMemberResponse adminMember = GroupMemberResponse.builder()
                    .id(MEMBER_ID)
                    .groupId(GROUP_ID)
                    .userId(USER_ID)
                    .role(MemberRole.ADMIN)
                    .status(MemberStatus.APPROVED)
                    .build();

            when(memberService.getAdminsAndModerators(GROUP_ID)).thenReturn(List.of(adminMember));

            mockMvc.perform(get("/api/v1/groups/{groupId}/members/admins", GROUP_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].role").value("ADMIN"));
        }

        @Test
        @DisplayName("Success - empty list")
        void getAdmins_Empty() throws Exception {
            when(memberService.getAdminsAndModerators(GROUP_ID)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/v1/groups/{groupId}/members/admins", GROUP_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void getAdmins_InvalidUuid() throws Exception {
            mockMvc.perform(get("/api/v1/groups/{groupId}/members/admins", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }
    }

    // ==================== GET MEMBER IDS ====================

    @Nested
    @DisplayName("GET /api/v1/groups/{groupId}/members/ids")
    class GetMemberIdsTests {

        @Test
        @DisplayName("Success - returns member IDs")
        void getMemberIds_Success() throws Exception {
            when(memberService.getMemberIds(GROUP_ID)).thenReturn(List.of(USER_ID, TARGET_USER_ID));

            mockMvc.perform(get("/api/v1/groups/{groupId}/members/ids", GROUP_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0]").value(USER_ID.toString()));
        }

        @Test
        @DisplayName("Success - empty list")
        void getMemberIds_Empty() throws Exception {
            when(memberService.getMemberIds(GROUP_ID)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/v1/groups/{groupId}/members/ids", GROUP_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void getMemberIds_InvalidUuid() throws Exception {
            mockMvc.perform(get("/api/v1/groups/{groupId}/members/ids", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }
    }

    // ==================== GET MEMBERSHIP STATS ====================

    @Nested
    @DisplayName("GET /api/v1/groups/{groupId}/members/stats")
    class GetMembershipStatsTests {

        @Test
        @DisplayName("Success - returns stats")
        void getMembershipStats_Success() throws Exception {
            MembershipStatsResponse stats = MembershipStatsResponse.builder()
                    .totalMembers(100L)
                    .pendingRequests(5L)
                    .bannedCount(2L)
                    .adminCount(3L)
                    .moderatorCount(5L)
                    .build();

            when(memberService.getMembershipStats(GROUP_ID)).thenReturn(stats);

            mockMvc.perform(get("/api/v1/groups/{groupId}/members/stats", GROUP_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalMembers").value(100))
                    .andExpect(jsonPath("$.pendingRequests").value(5));
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void getMembershipStats_InvalidUuid() throws Exception {
            mockMvc.perform(get("/api/v1/groups/{groupId}/members/stats", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }
    }

    // ==================== APPROVE JOIN REQUEST ====================

    @Nested
    @DisplayName("POST /api/v1/groups/{groupId}/members/{memberId}/approve")
    class ApproveJoinRequestTests {

        @Test
        @DisplayName("Success - returns approved member")
        void approveJoinRequest_Success() throws Exception {
            GroupMemberResponse approvedMember = GroupMemberResponse.builder()
                    .id(MEMBER_ID)
                    .groupId(GROUP_ID)
                    .userId(TARGET_USER_ID)
                    .role(MemberRole.MEMBER)
                    .status(MemberStatus.APPROVED)
                    .build();

            when(memberService.approveJoinRequest(USER_ID, GROUP_ID, MEMBER_ID)).thenReturn(approvedMember);

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{memberId}/approve", GROUP_ID, MEMBER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"));
        }

        @Test
        @DisplayName("Not pending - returns 400")
        void approveJoinRequest_NotPending() throws Exception {
            when(memberService.approveJoinRequest(USER_ID, GROUP_ID, MEMBER_ID))
                    .thenThrow(new ValidationException("Member is not pending approval"));

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{memberId}/approve", GROUP_ID, MEMBER_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Not moderator - returns 403")
        void approveJoinRequest_NotModerator() throws Exception {
            when(memberService.approveJoinRequest(USER_ID, GROUP_ID, MEMBER_ID))
                    .thenThrow(new ForbiddenException("Moderator access required"));

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{memberId}/approve", GROUP_ID, MEMBER_ID))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid group UUID - returns 400")
        void approveJoinRequest_InvalidGroupUuid() throws Exception {
            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{memberId}/approve", "invalid-uuid", MEMBER_ID))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }

        @Test
        @DisplayName("Invalid member UUID - returns 400")
        void approveJoinRequest_InvalidMemberUuid() throws Exception {
            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{memberId}/approve", GROUP_ID, "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }
    }

    // ==================== REJECT JOIN REQUEST ====================

    @Nested
    @DisplayName("POST /api/v1/groups/{groupId}/members/{memberId}/reject")
    class RejectJoinRequestTests {

        @Test
        @DisplayName("Success - returns 204")
        void rejectJoinRequest_Success() throws Exception {
            doNothing().when(memberService).rejectJoinRequest(USER_ID, GROUP_ID, MEMBER_ID);

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{memberId}/reject", GROUP_ID, MEMBER_ID))
                    .andExpect(status().isNoContent());

            verify(memberService).rejectJoinRequest(USER_ID, GROUP_ID, MEMBER_ID);
        }

        @Test
        @DisplayName("Not moderator - returns 403")
        void rejectJoinRequest_NotModerator() throws Exception {
            doThrow(new ForbiddenException("Moderator access required"))
                    .when(memberService).rejectJoinRequest(USER_ID, GROUP_ID, MEMBER_ID);

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{memberId}/reject", GROUP_ID, MEMBER_ID))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void rejectJoinRequest_InvalidUuid() throws Exception {
            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{memberId}/reject", "invalid-uuid", MEMBER_ID))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }
    }

    // ==================== REMOVE MEMBER ====================

    @Nested
    @DisplayName("DELETE /api/v1/groups/{groupId}/members/{userId}")
    class RemoveMemberTests {

        @Test
        @DisplayName("Success - returns 204")
        void removeMember_Success() throws Exception {
            doNothing().when(memberService).removeMember(USER_ID, GROUP_ID, TARGET_USER_ID);

            mockMvc.perform(delete("/api/v1/groups/{groupId}/members/{userId}", GROUP_ID, TARGET_USER_ID))
                    .andExpect(status().isNoContent());

            verify(memberService).removeMember(USER_ID, GROUP_ID, TARGET_USER_ID);
        }

        @Test
        @DisplayName("Cannot remove owner - returns 403")
        void removeMember_CannotRemoveOwner() throws Exception {
            doThrow(new ForbiddenException("Cannot remove the group owner"))
                    .when(memberService).removeMember(USER_ID, GROUP_ID, TARGET_USER_ID);

            mockMvc.perform(delete("/api/v1/groups/{groupId}/members/{userId}", GROUP_ID, TARGET_USER_ID))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void removeMember_InvalidUuid() throws Exception {
            mockMvc.perform(delete("/api/v1/groups/{groupId}/members/{userId}", "invalid-uuid", TARGET_USER_ID))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }
    }

    // ==================== UPDATE MEMBER ROLE ====================

    @Nested
    @DisplayName("PUT /api/v1/groups/{groupId}/members/{userId}/role")
    class UpdateMemberRoleTests {

        @Test
        @DisplayName("Success - returns updated member")
        void updateMemberRole_Success() throws Exception {
            UpdateMemberRoleRequest request = UpdateMemberRoleRequest.builder()
                    .role(MemberRole.MODERATOR)
                    .build();

            GroupMemberResponse updatedMember = GroupMemberResponse.builder()
                    .id(MEMBER_ID)
                    .groupId(GROUP_ID)
                    .userId(TARGET_USER_ID)
                    .role(MemberRole.MODERATOR)
                    .status(MemberStatus.APPROVED)
                    .build();

            when(memberService.updateMemberRole(eq(USER_ID), eq(GROUP_ID), eq(TARGET_USER_ID), any(UpdateMemberRoleRequest.class)))
                    .thenReturn(updatedMember);

            mockMvc.perform(put("/api/v1/groups/{groupId}/members/{userId}/role", GROUP_ID, TARGET_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("MODERATOR"));
        }

        @Test
        @DisplayName("Not admin - returns 403")
        void updateMemberRole_NotAdmin() throws Exception {
            UpdateMemberRoleRequest request = UpdateMemberRoleRequest.builder()
                    .role(MemberRole.MODERATOR)
                    .build();

            when(memberService.updateMemberRole(eq(USER_ID), eq(GROUP_ID), eq(TARGET_USER_ID), any(UpdateMemberRoleRequest.class)))
                    .thenThrow(new ForbiddenException("Admin access required"));

            mockMvc.perform(put("/api/v1/groups/{groupId}/members/{userId}/role", GROUP_ID, TARGET_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void updateMemberRole_InvalidUuid() throws Exception {
            UpdateMemberRoleRequest request = UpdateMemberRoleRequest.builder()
                    .role(MemberRole.MODERATOR)
                    .build();

            mockMvc.perform(put("/api/v1/groups/{groupId}/members/{userId}/role", "invalid-uuid", TARGET_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }
    }

    // ==================== PROMOTE TO ADMIN ====================

    @Nested
    @DisplayName("POST /api/v1/groups/{groupId}/members/{userId}/promote-to-admin")
    class PromoteToAdminTests {

        @Test
        @DisplayName("Success - returns promoted member")
        void promoteToAdmin_Success() throws Exception {
            GroupMemberResponse promotedMember = GroupMemberResponse.builder()
                    .id(MEMBER_ID)
                    .groupId(GROUP_ID)
                    .userId(TARGET_USER_ID)
                    .role(MemberRole.ADMIN)
                    .status(MemberStatus.APPROVED)
                    .build();

            when(memberService.promoteToAdmin(USER_ID, GROUP_ID, TARGET_USER_ID)).thenReturn(promotedMember);

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{userId}/promote-to-admin", GROUP_ID, TARGET_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("ADMIN"));
        }

        @Test
        @DisplayName("Not owner - returns 403")
        void promoteToAdmin_NotOwner() throws Exception {
            when(memberService.promoteToAdmin(USER_ID, GROUP_ID, TARGET_USER_ID))
                    .thenThrow(new ForbiddenException("Only owner can promote to admin"));

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{userId}/promote-to-admin", GROUP_ID, TARGET_USER_ID))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void promoteToAdmin_InvalidUuid() throws Exception {
            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{userId}/promote-to-admin", "invalid-uuid", TARGET_USER_ID))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }
    }

    // ==================== PROMOTE TO MODERATOR ====================

    @Nested
    @DisplayName("POST /api/v1/groups/{groupId}/members/{userId}/promote-to-moderator")
    class PromoteToModeratorTests {

        @Test
        @DisplayName("Success - returns promoted member")
        void promoteToModerator_Success() throws Exception {
            GroupMemberResponse promotedMember = GroupMemberResponse.builder()
                    .id(MEMBER_ID)
                    .groupId(GROUP_ID)
                    .userId(TARGET_USER_ID)
                    .role(MemberRole.MODERATOR)
                    .status(MemberStatus.APPROVED)
                    .build();

            when(memberService.promoteToModerator(USER_ID, GROUP_ID, TARGET_USER_ID)).thenReturn(promotedMember);

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{userId}/promote-to-moderator", GROUP_ID, TARGET_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("MODERATOR"));
        }

        @Test
        @DisplayName("Not admin - returns 403")
        void promoteToModerator_NotAdmin() throws Exception {
            when(memberService.promoteToModerator(USER_ID, GROUP_ID, TARGET_USER_ID))
                    .thenThrow(new ForbiddenException("Admin access required"));

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{userId}/promote-to-moderator", GROUP_ID, TARGET_USER_ID))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void promoteToModerator_InvalidUuid() throws Exception {
            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{userId}/promote-to-moderator", "invalid-uuid", TARGET_USER_ID))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }
    }

    // ==================== DEMOTE ADMIN ====================

    @Nested
    @DisplayName("POST /api/v1/groups/{groupId}/members/{userId}/demote-admin")
    class DemoteAdminTests {

        @Test
        @DisplayName("Success - returns demoted member")
        void demoteAdmin_Success() throws Exception {
            GroupMemberResponse demotedMember = GroupMemberResponse.builder()
                    .id(MEMBER_ID)
                    .groupId(GROUP_ID)
                    .userId(TARGET_USER_ID)
                    .role(MemberRole.MEMBER)
                    .status(MemberStatus.APPROVED)
                    .build();

            when(memberService.demoteAdmin(USER_ID, GROUP_ID, TARGET_USER_ID)).thenReturn(demotedMember);

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{userId}/demote-admin", GROUP_ID, TARGET_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("MEMBER"));
        }

        @Test
        @DisplayName("Not owner - returns 403")
        void demoteAdmin_NotOwner() throws Exception {
            when(memberService.demoteAdmin(USER_ID, GROUP_ID, TARGET_USER_ID))
                    .thenThrow(new ForbiddenException("Only owner can demote admin"));

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{userId}/demote-admin", GROUP_ID, TARGET_USER_ID))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void demoteAdmin_InvalidUuid() throws Exception {
            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{userId}/demote-admin", "invalid-uuid", TARGET_USER_ID))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }
    }

    // ==================== DEMOTE MODERATOR ====================

    @Nested
    @DisplayName("POST /api/v1/groups/{groupId}/members/{userId}/demote-moderator")
    class DemoteModeratorTests {

        @Test
        @DisplayName("Success - returns demoted member")
        void demoteModerator_Success() throws Exception {
            GroupMemberResponse demotedMember = GroupMemberResponse.builder()
                    .id(MEMBER_ID)
                    .groupId(GROUP_ID)
                    .userId(TARGET_USER_ID)
                    .role(MemberRole.MEMBER)
                    .status(MemberStatus.APPROVED)
                    .build();

            when(memberService.demoteModerator(USER_ID, GROUP_ID, TARGET_USER_ID)).thenReturn(demotedMember);

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{userId}/demote-moderator", GROUP_ID, TARGET_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("MEMBER"));
        }

        @Test
        @DisplayName("Not admin - returns 403")
        void demoteModerator_NotAdmin() throws Exception {
            when(memberService.demoteModerator(USER_ID, GROUP_ID, TARGET_USER_ID))
                    .thenThrow(new ForbiddenException("Admin access required"));

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{userId}/demote-moderator", GROUP_ID, TARGET_USER_ID))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void demoteModerator_InvalidUuid() throws Exception {
            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{userId}/demote-moderator", "invalid-uuid", TARGET_USER_ID))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }
    }

    // ==================== MUTE MEMBER ====================

    @Nested
    @DisplayName("POST /api/v1/groups/{groupId}/members/{userId}/mute")
    class MuteMemberTests {

        @Test
        @DisplayName("Success - returns muted member")
        void muteMember_Success() throws Exception {
            MuteMemberRequest request = MuteMemberRequest.builder()
                    .durationHours(1)
                    .reason("Spam")
                    .build();

            GroupMemberResponse mutedMember = GroupMemberResponse.builder()
                    .id(MEMBER_ID)
                    .groupId(GROUP_ID)
                    .userId(TARGET_USER_ID)
                    .role(MemberRole.MEMBER)
                    .status(MemberStatus.APPROVED)
                    .mutedUntil(LocalDateTime.now().plusMinutes(60))
                    .build();

            when(memberService.muteMember(eq(USER_ID), eq(GROUP_ID), eq(TARGET_USER_ID), any(MuteMemberRequest.class)))
                    .thenReturn(mutedMember);

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{userId}/mute", GROUP_ID, TARGET_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mutedUntil").exists());
        }

        @Test
        @DisplayName("Cannot mute admin - returns 403")
        void muteMember_CannotMuteAdmin() throws Exception {
            MuteMemberRequest request = MuteMemberRequest.builder()
                    .durationHours(1)
                    .build();

            when(memberService.muteMember(eq(USER_ID), eq(GROUP_ID), eq(TARGET_USER_ID), any(MuteMemberRequest.class)))
                    .thenThrow(new ForbiddenException("Cannot mute admins"));

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{userId}/mute", GROUP_ID, TARGET_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void muteMember_InvalidUuid() throws Exception {
            MuteMemberRequest request = MuteMemberRequest.builder()
                    .durationHours(1)
                    .build();

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{userId}/mute", "invalid-uuid", TARGET_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }
    }

    // ==================== UNMUTE MEMBER ====================

    @Nested
    @DisplayName("POST /api/v1/groups/{groupId}/members/{userId}/unmute")
    class UnmuteMemberTests {

        @Test
        @DisplayName("Success - returns unmuted member")
        void unmuteMember_Success() throws Exception {
            GroupMemberResponse unmutedMember = GroupMemberResponse.builder()
                    .id(MEMBER_ID)
                    .groupId(GROUP_ID)
                    .userId(TARGET_USER_ID)
                    .role(MemberRole.MEMBER)
                    .status(MemberStatus.APPROVED)
                    .mutedUntil(null)
                    .build();

            when(memberService.unmuteMember(USER_ID, GROUP_ID, TARGET_USER_ID)).thenReturn(unmutedMember);

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{userId}/unmute", GROUP_ID, TARGET_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mutedUntil").doesNotExist());
        }

        @Test
        @DisplayName("Not moderator - returns 403")
        void unmuteMember_NotModerator() throws Exception {
            when(memberService.unmuteMember(USER_ID, GROUP_ID, TARGET_USER_ID))
                    .thenThrow(new ForbiddenException("Moderator access required"));

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{userId}/unmute", GROUP_ID, TARGET_USER_ID))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void unmuteMember_InvalidUuid() throws Exception {
            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{userId}/unmute", "invalid-uuid", TARGET_USER_ID))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }
    }

    // ==================== BAN MEMBER ====================

    @Nested
    @DisplayName("POST /api/v1/groups/{groupId}/members/{userId}/ban")
    class BanMemberTests {

        @Test
        @DisplayName("Success - with request body")
        void banMember_SuccessWithBody() throws Exception {
            BanMemberRequest request = BanMemberRequest.builder()
                    .reason("Violation of rules")
                    .build();

            doNothing().when(memberService).banMember(eq(USER_ID), eq(GROUP_ID), eq(TARGET_USER_ID), any(BanMemberRequest.class));

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{userId}/ban", GROUP_ID, TARGET_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNoContent());

            verify(memberService).banMember(eq(USER_ID), eq(GROUP_ID), eq(TARGET_USER_ID), any(BanMemberRequest.class));
        }

        @Test
        @DisplayName("Success - without request body")
        void banMember_SuccessWithoutBody() throws Exception {
            doNothing().when(memberService).banMember(eq(USER_ID), eq(GROUP_ID), eq(TARGET_USER_ID), any());

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{userId}/ban", GROUP_ID, TARGET_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNoContent());

            verify(memberService).banMember(eq(USER_ID), eq(GROUP_ID), eq(TARGET_USER_ID), any());
        }

        @Test
        @DisplayName("Cannot ban owner - returns 403")
        void banMember_CannotBanOwner() throws Exception {
            doThrow(new ForbiddenException("Cannot ban the group owner"))
                    .when(memberService).banMember(eq(USER_ID), eq(GROUP_ID), eq(TARGET_USER_ID), any());

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{userId}/ban", GROUP_ID, TARGET_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void banMember_InvalidUuid() throws Exception {
            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{userId}/ban", "invalid-uuid", TARGET_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }
    }

    // ==================== UNBAN MEMBER ====================

    @Nested
    @DisplayName("POST /api/v1/groups/{groupId}/members/{userId}/unban")
    class UnbanMemberTests {

        @Test
        @DisplayName("Success - returns 204")
        void unbanMember_Success() throws Exception {
            doNothing().when(memberService).unbanMember(USER_ID, GROUP_ID, TARGET_USER_ID);

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{userId}/unban", GROUP_ID, TARGET_USER_ID))
                    .andExpect(status().isNoContent());

            verify(memberService).unbanMember(USER_ID, GROUP_ID, TARGET_USER_ID);
        }

        @Test
        @DisplayName("Member not banned - returns 400")
        void unbanMember_NotBanned() throws Exception {
            doThrow(new ValidationException("Member is not banned"))
                    .when(memberService).unbanMember(USER_ID, GROUP_ID, TARGET_USER_ID);

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{userId}/unban", GROUP_ID, TARGET_USER_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Not moderator - returns 403")
        void unbanMember_NotModerator() throws Exception {
            doThrow(new ForbiddenException("Moderator access required"))
                    .when(memberService).unbanMember(USER_ID, GROUP_ID, TARGET_USER_ID);

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{userId}/unban", GROUP_ID, TARGET_USER_ID))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void unbanMember_InvalidUuid() throws Exception {
            mockMvc.perform(post("/api/v1/groups/{groupId}/members/{userId}/unban", "invalid-uuid", TARGET_USER_ID))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }
    }

    // ==================== UPDATE MY SETTINGS ====================

    @Nested
    @DisplayName("PUT /api/v1/groups/{groupId}/members/me/settings")
    class UpdateMySettingsTests {

        @Test
        @DisplayName("Success - returns updated member")
        void updateMySettings_Success() throws Exception {
            UpdateMemberSettingsRequest request = UpdateMemberSettingsRequest.builder()
                    .notificationsEnabled(false)
                    .build();

            GroupMemberResponse updatedMember = GroupMemberResponse.builder()
                    .id(MEMBER_ID)
                    .groupId(GROUP_ID)
                    .userId(USER_ID)
                    .role(MemberRole.MEMBER)
                    .status(MemberStatus.APPROVED)
                    .notificationsEnabled(false)
                    .build();

            when(memberService.updateMemberSettings(eq(USER_ID), eq(GROUP_ID), any(UpdateMemberSettingsRequest.class)))
                    .thenReturn(updatedMember);

            mockMvc.perform(put("/api/v1/groups/{groupId}/members/me/settings", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.notificationsEnabled").value(false));
        }

        @Test
        @DisplayName("Not a member - returns 400")
        void updateMySettings_NotMember() throws Exception {
            UpdateMemberSettingsRequest request = UpdateMemberSettingsRequest.builder()
                    .notificationsEnabled(false)
                    .build();

            when(memberService.updateMemberSettings(eq(USER_ID), eq(GROUP_ID), any(UpdateMemberSettingsRequest.class)))
                    .thenThrow(new ResourceNotFoundException("Member", "userId", USER_ID));

            mockMvc.perform(put("/api/v1/groups/{groupId}/members/me/settings", GROUP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Invalid UUID - returns 400")
        void updateMySettings_InvalidUuid() throws Exception {
            UpdateMemberSettingsRequest request = UpdateMemberSettingsRequest.builder()
                    .notificationsEnabled(false)
                    .build();

            mockMvc.perform(put("/api/v1/groups/{groupId}/members/me/settings", "invalid-uuid")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }
    }

    // ==================== TRANSFER OWNERSHIP ====================

    @Nested
    @DisplayName("POST /api/v1/groups/{groupId}/members/transfer-ownership/{newOwnerId}")
    class TransferOwnershipTests {

        @Test
        @DisplayName("Success - returns 200")
        void transferOwnership_Success() throws Exception {
            doNothing().when(memberService).transferOwnership(USER_ID, GROUP_ID, TARGET_USER_ID);

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/transfer-ownership/{newOwnerId}", GROUP_ID, TARGET_USER_ID))
                    .andExpect(status().isOk());

            verify(memberService).transferOwnership(USER_ID, GROUP_ID, TARGET_USER_ID);
        }

        @Test
        @DisplayName("Not owner - returns 403")
        void transferOwnership_NotOwner() throws Exception {
            doThrow(new ForbiddenException("Only owner can transfer ownership"))
                    .when(memberService).transferOwnership(USER_ID, GROUP_ID, TARGET_USER_ID);

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/transfer-ownership/{newOwnerId}", GROUP_ID, TARGET_USER_ID))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("New owner not approved member - returns 400")
        void transferOwnership_NewOwnerNotMember() throws Exception {
            doThrow(new ValidationException("New owner must be an approved member"))
                    .when(memberService).transferOwnership(USER_ID, GROUP_ID, TARGET_USER_ID);

            mockMvc.perform(post("/api/v1/groups/{groupId}/members/transfer-ownership/{newOwnerId}", GROUP_ID, TARGET_USER_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Invalid group UUID - returns 400")
        void transferOwnership_InvalidGroupUuid() throws Exception {
            mockMvc.perform(post("/api/v1/groups/{groupId}/members/transfer-ownership/{newOwnerId}", "invalid-uuid", TARGET_USER_ID))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }

        @Test
        @DisplayName("Invalid new owner UUID - returns 400")
        void transferOwnership_InvalidNewOwnerUuid() throws Exception {
            mockMvc.perform(post("/api/v1/groups/{groupId}/members/transfer-ownership/{newOwnerId}", GROUP_ID, "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(memberService);
        }
    }
}