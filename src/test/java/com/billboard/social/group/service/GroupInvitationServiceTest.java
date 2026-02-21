package com.billboard.social.group.service;

import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.exception.ForbiddenException;
import com.billboard.social.common.exception.ResourceNotFoundException;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.group.dto.request.GroupRequests.CreateInviteLinkRequest;
import com.billboard.social.group.dto.request.GroupRequests.InviteMemberRequest;
import com.billboard.social.group.dto.response.GroupResponses.GroupMemberResponse;
import com.billboard.social.group.dto.response.GroupResponses.InvitationResponse;
import com.billboard.social.group.entity.Group;
import com.billboard.social.group.entity.GroupInvitation;
import com.billboard.social.group.entity.GroupMember;
import com.billboard.social.group.entity.enums.GroupType;
import com.billboard.social.group.entity.enums.MemberRole;
import com.billboard.social.group.entity.enums.MemberStatus;
import com.billboard.social.group.repository.GroupInvitationRepository;
import com.billboard.social.group.repository.GroupMemberRepository;
import com.billboard.social.group.repository.GroupRepository;
import feign.FeignException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupInvitationServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository memberRepository;

    @Mock
    private GroupInvitationRepository invitationRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private GroupInvitationService invitationService;

    // Test constants
    private static final UUID INVITER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID INVITEE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID GROUP_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID INVITATION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private Group testGroup;
    private GroupMember inviterMember;
    private GroupMember moderatorMember;
    private GroupMember regularMember;
    private GroupInvitation testInvitation;
    private UserSummary inviterSummary;
    private UserSummary inviteeSummary;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(invitationService, "defaultExpirationDays", 7);
        ReflectionTestUtils.setField(invitationService, "maxPendingPerUser", 100);

        testGroup = Group.builder()
                .id(GROUP_ID)
                .name("Test Group")
                .slug("test-group")
                .groupType(GroupType.PUBLIC)
                .ownerId(INVITER_ID)
                .memberCount(100)
                .allowMemberPosts(true)
                .allowMemberInvites(true)
                .build();
        testGroup.setCreatedAt(LocalDateTime.now());

        inviterMember = GroupMember.builder()
                .id(UUID.randomUUID())
                .group(testGroup)
                .userId(INVITER_ID)
                .role(MemberRole.OWNER)
                .status(MemberStatus.APPROVED)
                .build();

        moderatorMember = GroupMember.builder()
                .id(UUID.randomUUID())
                .group(testGroup)
                .userId(INVITER_ID)
                .role(MemberRole.MODERATOR)
                .status(MemberStatus.APPROVED)
                .build();

        regularMember = GroupMember.builder()
                .id(UUID.randomUUID())
                .group(testGroup)
                .userId(INVITER_ID)
                .role(MemberRole.MEMBER)
                .status(MemberStatus.APPROVED)
                .build();

        testInvitation = GroupInvitation.builder()
                .id(INVITATION_ID)
                .group(testGroup)
                .inviterId(INVITER_ID)
                .inviteeId(INVITEE_ID)
                .inviteeEmail("invitee@example.com")
                .message("Welcome!")
                .status("PENDING")
                .inviteCode("abc123xyz")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        testInvitation.setCreatedAt(LocalDateTime.now());

        inviterSummary = UserSummary.builder()
                .id(INVITER_ID)
                .username("inviter")
                .email("inviter@example.com")
                .build();

        inviteeSummary = UserSummary.builder()
                .id(INVITEE_ID)
                .username("invitee")
                .email("invitee@example.com")
                .build();
    }

    // ==================== INVITE MEMBER ====================

    @Nested
    @DisplayName("inviteMember")
    class InviteMemberTests {

        @Test
        @DisplayName("Success - invite by userId")
        void inviteMember_SuccessByUserId() {
            InviteMemberRequest request = InviteMemberRequest.builder()
                    .userId(INVITEE_ID)
                    .message("Welcome to the group!")
                    .expirationDays(7)
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(inviterMember));
            when(userServiceClient.getUserSummary(INVITEE_ID)).thenReturn(inviteeSummary);
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.empty());
            when(invitationRepository.findByGroupIdAndInviteeId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.empty());
            when(invitationRepository.save(any(GroupInvitation.class))).thenAnswer(invocation -> {
                GroupInvitation saved = invocation.getArgument(0);
                saved.setId(INVITATION_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(INVITER_ID)).thenReturn(inviterSummary);

            InvitationResponse response = invitationService.inviteMember(INVITER_ID, GROUP_ID, request);

            assertThat(response).isNotNull();
            assertThat(response.getGroupId()).isEqualTo(GROUP_ID);
            assertThat(response.getInviteeId()).isEqualTo(INVITEE_ID);
        }

        @Test
        @DisplayName("Invitee has LEFT status - continues with invitation")
        void inviteMember_InviteeHasLeftStatus() {
            InviteMemberRequest request = InviteMemberRequest.builder()
                    .userId(INVITEE_ID)
                    .build();

            GroupMember leftMember = GroupMember.builder()
                    .userId(INVITEE_ID)
                    .status(MemberStatus.LEFT)  // Not BANNED, not APPROVED, not PENDING
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(inviterMember));
            when(userServiceClient.getUserSummary(INVITEE_ID)).thenReturn(inviteeSummary);
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.of(leftMember));
            when(invitationRepository.findByGroupIdAndInviteeId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.empty());
            when(invitationRepository.save(any(GroupInvitation.class))).thenAnswer(invocation -> {
                GroupInvitation saved = invocation.getArgument(0);
                saved.setId(INVITATION_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(INVITER_ID)).thenReturn(inviterSummary);

            InvitationResponse response = invitationService.inviteMember(INVITER_ID, GROUP_ID, request);

            assertThat(response).isNotNull();
            assertThat(response.getInviteeId()).isEqualTo(INVITEE_ID);
        }

        @Test
        @DisplayName("Success - invite by email only")
        void inviteMember_SuccessByEmail() {
            InviteMemberRequest request = InviteMemberRequest.builder()
                    .email("newuser@example.com")
                    .message("Welcome!")
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(inviterMember));
            when(invitationRepository.save(any(GroupInvitation.class))).thenAnswer(invocation -> {
                GroupInvitation saved = invocation.getArgument(0);
                saved.setId(INVITATION_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(INVITER_ID)).thenReturn(inviterSummary);

            InvitationResponse response = invitationService.inviteMember(INVITER_ID, GROUP_ID, request);

            assertThat(response).isNotNull();
            assertThat(response.getInviteeId()).isNull();
            assertThat(response.getInviteeEmail()).isEqualTo("newuser@example.com");
        }

        @Test
        @DisplayName("Success - without message")
        void inviteMember_WithoutMessage() {
            InviteMemberRequest request = InviteMemberRequest.builder()
                    .userId(INVITEE_ID)
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(inviterMember));
            when(userServiceClient.getUserSummary(INVITEE_ID)).thenReturn(inviteeSummary);
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.empty());
            when(invitationRepository.findByGroupIdAndInviteeId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.empty());
            when(invitationRepository.save(any(GroupInvitation.class))).thenAnswer(invocation -> {
                GroupInvitation saved = invocation.getArgument(0);
                saved.setId(INVITATION_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(INVITER_ID)).thenReturn(inviterSummary);

            InvitationResponse response = invitationService.inviteMember(INVITER_ID, GROUP_ID, request);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Success - expirationDays null (uses default)")
        void inviteMember_ExpirationDaysNull() {
            InviteMemberRequest request = InviteMemberRequest.builder()
                    .userId(INVITEE_ID)
                    .expirationDays(null)
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(inviterMember));
            when(userServiceClient.getUserSummary(INVITEE_ID)).thenReturn(inviteeSummary);
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.empty());
            when(invitationRepository.findByGroupIdAndInviteeId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.empty());
            when(invitationRepository.save(any(GroupInvitation.class))).thenAnswer(invocation -> {
                GroupInvitation saved = invocation.getArgument(0);
                saved.setId(INVITATION_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(INVITER_ID)).thenReturn(inviterSummary);

            InvitationResponse response = invitationService.inviteMember(INVITER_ID, GROUP_ID, request);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Success - regular member when allowMemberInvites is true")
        void inviteMember_RegularMemberAllowed() {
            testGroup.setAllowMemberInvites(true);
            InviteMemberRequest request = InviteMemberRequest.builder()
                    .userId(INVITEE_ID)
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(regularMember));
            when(userServiceClient.getUserSummary(INVITEE_ID)).thenReturn(inviteeSummary);
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.empty());
            when(invitationRepository.findByGroupIdAndInviteeId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.empty());
            when(invitationRepository.save(any(GroupInvitation.class))).thenAnswer(invocation -> {
                GroupInvitation saved = invocation.getArgument(0);
                saved.setId(INVITATION_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(INVITER_ID)).thenReturn(inviterSummary);

            InvitationResponse response = invitationService.inviteMember(INVITER_ID, GROUP_ID, request);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Success - moderator can invite when allowMemberInvites is false")
        void inviteMember_ModeratorCanInvite() {
            testGroup.setAllowMemberInvites(false);
            InviteMemberRequest request = InviteMemberRequest.builder()
                    .userId(INVITEE_ID)
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(moderatorMember));
            when(userServiceClient.getUserSummary(INVITEE_ID)).thenReturn(inviteeSummary);
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.empty());
            when(invitationRepository.findByGroupIdAndInviteeId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.empty());
            when(invitationRepository.save(any(GroupInvitation.class))).thenAnswer(invocation -> {
                GroupInvitation saved = invocation.getArgument(0);
                saved.setId(INVITATION_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(INVITER_ID)).thenReturn(inviterSummary);

            InvitationResponse response = invitationService.inviteMember(INVITER_ID, GROUP_ID, request);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Success - deletes old non-pending invitation to allow re-invite")
        void inviteMember_DeletesOldInvitation() {
            InviteMemberRequest request = InviteMemberRequest.builder()
                    .userId(INVITEE_ID)
                    .build();

            GroupInvitation oldInvitation = GroupInvitation.builder()
                    .id(UUID.randomUUID())
                    .group(testGroup)
                    .inviterId(INVITER_ID)
                    .inviteeId(INVITEE_ID)
                    .status("DECLINED")
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(inviterMember));
            when(userServiceClient.getUserSummary(INVITEE_ID)).thenReturn(inviteeSummary);
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.empty());
            when(invitationRepository.findByGroupIdAndInviteeId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.of(oldInvitation));
            when(invitationRepository.save(any(GroupInvitation.class))).thenAnswer(invocation -> {
                GroupInvitation saved = invocation.getArgument(0);
                saved.setId(INVITATION_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(INVITER_ID)).thenReturn(inviterSummary);

            InvitationResponse response = invitationService.inviteMember(INVITER_ID, GROUP_ID, request);

            assertThat(response).isNotNull();
            verify(invitationRepository).delete(oldInvitation);
        }

        @Test
        @DisplayName("Success - invitee has no email (skips email sending)")
        void inviteMember_InviteeNoEmail() {
            inviteeSummary.setEmail(null);
            InviteMemberRequest request = InviteMemberRequest.builder()
                    .userId(INVITEE_ID)
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(inviterMember));
            when(userServiceClient.getUserSummary(INVITEE_ID)).thenReturn(inviteeSummary);
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.empty());
            when(invitationRepository.findByGroupIdAndInviteeId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.empty());
            when(invitationRepository.save(any(GroupInvitation.class))).thenAnswer(invocation -> {
                GroupInvitation saved = invocation.getArgument(0);
                saved.setId(INVITATION_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(INVITER_ID)).thenReturn(inviterSummary);

            InvitationResponse response = invitationService.inviteMember(INVITER_ID, GROUP_ID, request);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Success - invitee has blank email (skips email sending)")
        void inviteMember_InviteeBlankEmail() {
            inviteeSummary.setEmail("   ");
            InviteMemberRequest request = InviteMemberRequest.builder()
                    .userId(INVITEE_ID)
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(inviterMember));
            when(userServiceClient.getUserSummary(INVITEE_ID)).thenReturn(inviteeSummary);
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.empty());
            when(invitationRepository.findByGroupIdAndInviteeId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.empty());
            when(invitationRepository.save(any(GroupInvitation.class))).thenAnswer(invocation -> {
                GroupInvitation saved = invocation.getArgument(0);
                saved.setId(INVITATION_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(INVITER_ID)).thenReturn(inviterSummary);

            InvitationResponse response = invitationService.inviteMember(INVITER_ID, GROUP_ID, request);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Request is null - throws ValidationException")
        void inviteMember_RequestNull() {
            assertThatThrownBy(() -> invitationService.inviteMember(INVITER_ID, GROUP_ID, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Request body is required");
        }

        @Test
        @DisplayName("Both userId and email are null - throws ValidationException")
        void inviteMember_BothUserIdAndEmailNull() {
            InviteMemberRequest request = InviteMemberRequest.builder().build();

            assertThatThrownBy(() -> invitationService.inviteMember(INVITER_ID, GROUP_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Either userId or email is required");
        }

        @Test
        @DisplayName("Group not found - throws ResourceNotFoundException")
        void inviteMember_GroupNotFound() {
            InviteMemberRequest request = InviteMemberRequest.builder()
                    .userId(INVITEE_ID)
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> invitationService.inviteMember(INVITER_ID, GROUP_ID, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Group");
        }

        @Test
        @DisplayName("Inviter not a member - throws ForbiddenException")
        void inviteMember_InviterNotMember() {
            InviteMemberRequest request = InviteMemberRequest.builder()
                    .userId(INVITEE_ID)
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> invitationService.inviteMember(INVITER_ID, GROUP_ID, request))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("You are not a member of this group");
        }

        @Test
        @DisplayName("Regular member when allowMemberInvites is false - throws ForbiddenException")
        void inviteMember_RegularMemberNotAllowed() {
            testGroup.setAllowMemberInvites(false);
            InviteMemberRequest request = InviteMemberRequest.builder()
                    .userId(INVITEE_ID)
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(regularMember));

            assertThatThrownBy(() -> invitationService.inviteMember(INVITER_ID, GROUP_ID, request))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Only moderators can invite members to this group");
        }

        @Test
        @DisplayName("Invitee is banned - throws ValidationException")
        void inviteMember_InviteeIsBanned() {
            InviteMemberRequest request = InviteMemberRequest.builder()
                    .userId(INVITEE_ID)
                    .build();

            GroupMember bannedMember = GroupMember.builder()
                    .userId(INVITEE_ID)
                    .status(MemberStatus.BANNED)
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(inviterMember));
            when(userServiceClient.getUserSummary(INVITEE_ID)).thenReturn(inviteeSummary);
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.of(bannedMember));

            assertThatThrownBy(() -> invitationService.inviteMember(INVITER_ID, GROUP_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("This user is banned from the group");
        }

        @Test
        @DisplayName("Invitee already a member (APPROVED) - throws ValidationException")
        void inviteMember_InviteeAlreadyMember() {
            InviteMemberRequest request = InviteMemberRequest.builder()
                    .userId(INVITEE_ID)
                    .build();

            GroupMember existingMember = GroupMember.builder()
                    .userId(INVITEE_ID)
                    .status(MemberStatus.APPROVED)
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(inviterMember));
            when(userServiceClient.getUserSummary(INVITEE_ID)).thenReturn(inviteeSummary);
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.of(existingMember));

            assertThatThrownBy(() -> invitationService.inviteMember(INVITER_ID, GROUP_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("User is already a member of this group");
        }

        @Test
        @DisplayName("Invitee has pending join request - throws ValidationException")
        void inviteMember_InviteeHasPendingRequest() {
            InviteMemberRequest request = InviteMemberRequest.builder()
                    .userId(INVITEE_ID)
                    .build();

            GroupMember pendingMember = GroupMember.builder()
                    .userId(INVITEE_ID)
                    .status(MemberStatus.PENDING)
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(inviterMember));
            when(userServiceClient.getUserSummary(INVITEE_ID)).thenReturn(inviteeSummary);
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.of(pendingMember));

            assertThatThrownBy(() -> invitationService.inviteMember(INVITER_ID, GROUP_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("User already has a pending join request");
        }

        @Test
        @DisplayName("Pending invitation already exists - throws ValidationException")
        void inviteMember_PendingInvitationExists() {
            InviteMemberRequest request = InviteMemberRequest.builder()
                    .userId(INVITEE_ID)
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(inviterMember));
            when(userServiceClient.getUserSummary(INVITEE_ID)).thenReturn(inviteeSummary);
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.empty());
            when(invitationRepository.findByGroupIdAndInviteeId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.of(testInvitation));

            assertThatThrownBy(() -> invitationService.inviteMember(INVITER_ID, GROUP_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("A pending invitation already exists for this user");
        }

        @Test
        @DisplayName("Expiration days below minimum - throws ValidationException")
        void inviteMember_ExpirationDaysBelowMin() {
            InviteMemberRequest request = InviteMemberRequest.builder()
                    .userId(INVITEE_ID)
                    .expirationDays(0)
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(inviterMember));
            when(userServiceClient.getUserSummary(INVITEE_ID)).thenReturn(inviteeSummary);
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.empty());
            when(invitationRepository.findByGroupIdAndInviteeId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> invitationService.inviteMember(INVITER_ID, GROUP_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Expiration days must be between 1 and 30");
        }

        @Test
        @DisplayName("Expiration days above maximum - throws ValidationException")
        void inviteMember_ExpirationDaysAboveMax() {
            InviteMemberRequest request = InviteMemberRequest.builder()
                    .userId(INVITEE_ID)
                    .expirationDays(31)
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(inviterMember));
            when(userServiceClient.getUserSummary(INVITEE_ID)).thenReturn(inviteeSummary);
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.empty());
            when(invitationRepository.findByGroupIdAndInviteeId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> invitationService.inviteMember(INVITER_ID, GROUP_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Expiration days must be between 1 and 30");
        }
    }

    // ==================== CREATE INVITE LINK ====================

    @Nested
    @DisplayName("createInviteLink")
    class CreateInviteLinkTests {

        @Test
        @DisplayName("Success - with request body")
        void createInviteLink_SuccessWithBody() {
            CreateInviteLinkRequest request = CreateInviteLinkRequest.builder()
                    .expirationDays(7)
                    .message("Join our group!")
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(inviterMember));
            when(invitationRepository.save(any(GroupInvitation.class))).thenAnswer(invocation -> {
                GroupInvitation saved = invocation.getArgument(0);
                saved.setId(INVITATION_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(INVITER_ID)).thenReturn(inviterSummary);

            InvitationResponse response = invitationService.createInviteLink(INVITER_ID, GROUP_ID, request);

            assertThat(response).isNotNull();
            assertThat(response.getInviteeId()).isNull();
            assertThat(response.getInviteCode()).isNotNull();
        }

        @Test
        @DisplayName("Success - with null request (uses defaults)")
        void createInviteLink_NullRequest() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(inviterMember));
            when(invitationRepository.save(any(GroupInvitation.class))).thenAnswer(invocation -> {
                GroupInvitation saved = invocation.getArgument(0);
                saved.setId(INVITATION_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(INVITER_ID)).thenReturn(inviterSummary);

            InvitationResponse response = invitationService.createInviteLink(INVITER_ID, GROUP_ID, null);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Success - expirationDays null in request (uses default)")
        void createInviteLink_ExpirationDaysNull() {
            CreateInviteLinkRequest request = CreateInviteLinkRequest.builder()
                    .expirationDays(null)
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(inviterMember));
            when(invitationRepository.save(any(GroupInvitation.class))).thenAnswer(invocation -> {
                GroupInvitation saved = invocation.getArgument(0);
                saved.setId(INVITATION_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(INVITER_ID)).thenReturn(inviterSummary);

            InvitationResponse response = invitationService.createInviteLink(INVITER_ID, GROUP_ID, request);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Success - regular member when allowMemberInvites is true")
        void createInviteLink_RegularMemberAllowed() {
            testGroup.setAllowMemberInvites(true);

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(regularMember));
            when(invitationRepository.save(any(GroupInvitation.class))).thenAnswer(invocation -> {
                GroupInvitation saved = invocation.getArgument(0);
                saved.setId(INVITATION_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(INVITER_ID)).thenReturn(inviterSummary);

            InvitationResponse response = invitationService.createInviteLink(INVITER_ID, GROUP_ID, null);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Group not found - throws ResourceNotFoundException")
        void createInviteLink_GroupNotFound() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> invitationService.createInviteLink(INVITER_ID, GROUP_ID, null))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Inviter not a member - throws ForbiddenException")
        void createInviteLink_InviterNotMember() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> invitationService.createInviteLink(INVITER_ID, GROUP_ID, null))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("You are not a member of this group");
        }

        @Test
        @DisplayName("Regular member when allowMemberInvites is false - throws ForbiddenException")
        void createInviteLink_RegularMemberNotAllowed() {
            testGroup.setAllowMemberInvites(false);

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(regularMember));

            assertThatThrownBy(() -> invitationService.createInviteLink(INVITER_ID, GROUP_ID, null))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Only moderators can create invite links for this group");
        }

        @Test
        @DisplayName("Expiration days below minimum - throws ValidationException")
        void createInviteLink_ExpirationDaysBelowMin() {
            CreateInviteLinkRequest request = CreateInviteLinkRequest.builder()
                    .expirationDays(0)
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(inviterMember));

            assertThatThrownBy(() -> invitationService.createInviteLink(INVITER_ID, GROUP_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Expiration days must be between 1 and 30");
        }

        @Test
        @DisplayName("Expiration days above maximum - throws ValidationException")
        void createInviteLink_ExpirationDaysAboveMax() {
            CreateInviteLinkRequest request = CreateInviteLinkRequest.builder()
                    .expirationDays(31)
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(inviterMember));

            assertThatThrownBy(() -> invitationService.createInviteLink(INVITER_ID, GROUP_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Expiration days must be between 1 and 30");
        }
    }

    // ==================== ACCEPT INVITATION ====================

    @Nested
    @DisplayName("acceptInvitation")
    class AcceptInvitationTests {

        @Test
        @DisplayName("Success - accepts and joins group")
        void acceptInvitation_Success() {
            testInvitation.setInviteeId(INVITEE_ID);

            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(testInvitation));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.empty());
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> {
                GroupMember saved = invocation.getArgument(0);
                saved.setId(UUID.randomUUID());
                saved.setJoinedAt(LocalDateTime.now());
                return saved;
            });
            when(groupRepository.save(any(Group.class))).thenReturn(testGroup);
            when(userServiceClient.getUserSummary(INVITEE_ID)).thenReturn(inviteeSummary);

            GroupMemberResponse response = invitationService.acceptInvitation(INVITEE_ID, INVITATION_ID);

            assertThat(response).isNotNull();
            assertThat(response.getRole()).isEqualTo(MemberRole.MEMBER);
            assertThat(response.getStatus()).isEqualTo(MemberStatus.APPROVED);
            verify(invitationRepository).delete(testInvitation);
            verify(groupRepository).save(testGroup);
        }

        @Test
        @DisplayName("User has LEFT status - continues and creates new membership")
        void acceptInvitation_UserHasLeftStatus() {
            testInvitation.setInviteeId(INVITEE_ID);
            GroupMember leftMember = GroupMember.builder()
                    .userId(INVITEE_ID)
                    .status(MemberStatus.LEFT)  // Passes all three checks (not BANNED, not APPROVED, not PENDING)
                    .build();

            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(testInvitation));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.of(leftMember));
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> {
                GroupMember saved = invocation.getArgument(0);
                saved.setId(UUID.randomUUID());
                saved.setJoinedAt(LocalDateTime.now());
                return saved;
            });
            when(groupRepository.save(any(Group.class))).thenReturn(testGroup);
            when(userServiceClient.getUserSummary(INVITEE_ID)).thenReturn(inviteeSummary);

            GroupMemberResponse response = invitationService.acceptInvitation(INVITEE_ID, INVITATION_ID);

            assertThat(response).isNotNull();
            verify(invitationRepository).delete(testInvitation);
        }

        @Test
        @DisplayName("Invitee has pending join request - throws ValidationException")
        void inviteMember_InviteeHasPendingRequest() {
            InviteMemberRequest request = InviteMemberRequest.builder()
                    .userId(INVITEE_ID)
                    .build();

            GroupMember pendingMember = GroupMember.builder()
                    .userId(INVITEE_ID)
                    .status(MemberStatus.PENDING)
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(inviterMember));
            when(userServiceClient.getUserSummary(INVITEE_ID)).thenReturn(inviteeSummary);
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.of(pendingMember));

            assertThatThrownBy(() -> invitationService.inviteMember(INVITER_ID, GROUP_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("User already has a pending join request");
        }

        @Test
        @DisplayName("Success - public link invitation (inviteeId is null)")
        void acceptInvitation_PublicLink() {
            testInvitation.setInviteeId(null);

            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(testInvitation));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.empty());
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> {
                GroupMember saved = invocation.getArgument(0);
                saved.setId(UUID.randomUUID());
                saved.setJoinedAt(LocalDateTime.now());
                return saved;
            });
            when(groupRepository.save(any(Group.class))).thenReturn(testGroup);
            when(userServiceClient.getUserSummary(INVITEE_ID)).thenReturn(inviteeSummary);

            GroupMemberResponse response = invitationService.acceptInvitation(INVITEE_ID, INVITATION_ID);

            assertThat(response).isNotNull();
            verify(invitationRepository).delete(testInvitation);
        }

        @Test
        @DisplayName("Invitation is pending but expired - throws 'Invitation has expired'")
        void acceptInvitation_PendingButExpired() {
            GroupInvitation spyInvitation = spy(testInvitation);
            spyInvitation.setInviteeId(INVITEE_ID);
            spyInvitation.setExpiresAt(LocalDateTime.now().minusDays(1));

            // Force isPending() to return true (bypassing its internal expiration check)
            doReturn(true).when(spyInvitation).isPending();
            // isExpired() will return true naturally since expiresAt is in the past

            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(spyInvitation));

            assertThatThrownBy(() -> invitationService.acceptInvitation(INVITEE_ID, INVITATION_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Invitation has expired");
        }

        @Test
        @DisplayName("Invitation not found - throws ResourceNotFoundException")
        void acceptInvitation_NotFound() {
            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> invitationService.acceptInvitation(INVITEE_ID, INVITATION_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Invitation not for this user - throws ForbiddenException")
        void acceptInvitation_NotForUser() {
            testInvitation.setInviteeId(UUID.randomUUID()); // Different user

            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(testInvitation));

            assertThatThrownBy(() -> invitationService.acceptInvitation(INVITEE_ID, INVITATION_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("This invitation is not for you");
        }

        @Test
        @DisplayName("Invitation not pending - throws ValidationException")
        void acceptInvitation_NotPending() {
            testInvitation.setStatus("ACCEPTED");
            testInvitation.setInviteeId(INVITEE_ID);

            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(testInvitation));

            assertThatThrownBy(() -> invitationService.acceptInvitation(INVITEE_ID, INVITATION_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Invitation is no longer valid");
        }

        @Test
        @DisplayName("Invitation expired (isPending returns false) - throws ValidationException")
        void acceptInvitation_Expired() {
            testInvitation.setExpiresAt(LocalDateTime.now().minusDays(1));
            testInvitation.setInviteeId(INVITEE_ID);

            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(testInvitation));

            assertThatThrownBy(() -> invitationService.acceptInvitation(INVITEE_ID, INVITATION_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Invitation is no longer valid");
        }

        @Test
        @DisplayName("User is banned - throws ValidationException")
        void acceptInvitation_UserBanned() {
            testInvitation.setInviteeId(INVITEE_ID);
            GroupMember bannedMember = GroupMember.builder()
                    .userId(INVITEE_ID)
                    .status(MemberStatus.BANNED)
                    .build();

            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(testInvitation));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.of(bannedMember));

            assertThatThrownBy(() -> invitationService.acceptInvitation(INVITEE_ID, INVITATION_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("You are banned from this group");
        }

        @Test
        @DisplayName("User already a member - throws ValidationException")
        void acceptInvitation_AlreadyMember() {
            testInvitation.setInviteeId(INVITEE_ID);
            GroupMember existingMember = GroupMember.builder()
                    .userId(INVITEE_ID)
                    .status(MemberStatus.APPROVED)
                    .build();

            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(testInvitation));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.of(existingMember));

            assertThatThrownBy(() -> invitationService.acceptInvitation(INVITEE_ID, INVITATION_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("You are already a member of this group");
        }
    }

    // ==================== DECLINE INVITATION ====================

    @Nested
    @DisplayName("declineInvitation")
    class DeclineInvitationTests {

        @Test
        @DisplayName("Success - declines and deletes invitation")
        void declineInvitation_Success() {
            testInvitation.setInviteeId(INVITEE_ID);

            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(testInvitation));

            invitationService.declineInvitation(INVITEE_ID, INVITATION_ID);

            verify(invitationRepository).delete(testInvitation);
        }

        @Test
        @DisplayName("Success - public link invitation (inviteeId is null)")
        void declineInvitation_PublicLink() {
            testInvitation.setInviteeId(null);

            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(testInvitation));

            invitationService.declineInvitation(INVITEE_ID, INVITATION_ID);

            verify(invitationRepository).delete(testInvitation);
        }

        @Test
        @DisplayName("Invitation not found - throws ResourceNotFoundException")
        void declineInvitation_NotFound() {
            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> invitationService.declineInvitation(INVITEE_ID, INVITATION_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Invitation not for this user - throws ForbiddenException")
        void declineInvitation_NotForUser() {
            testInvitation.setInviteeId(UUID.randomUUID());

            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(testInvitation));

            assertThatThrownBy(() -> invitationService.declineInvitation(INVITEE_ID, INVITATION_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("This invitation is not for you");
        }

        @Test
        @DisplayName("Invitation not pending - throws ValidationException")
        void declineInvitation_NotPending() {
            testInvitation.setStatus("DECLINED");
            testInvitation.setInviteeId(INVITEE_ID);

            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(testInvitation));

            assertThatThrownBy(() -> invitationService.declineInvitation(INVITEE_ID, INVITATION_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Invitation is no longer valid");
        }
    }

    // ==================== ACCEPT BY CODE ====================

    @Nested
    @DisplayName("acceptByCode")
    class AcceptByCodeTests {

        @Test
        @DisplayName("Success - user-specific invite (deletes after accepting)")
        void acceptByCode_SuccessUserSpecific() {
            testInvitation.setInviteeId(INVITEE_ID);

            when(invitationRepository.findByInviteCode("abc123xyz")).thenReturn(Optional.of(testInvitation));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.empty());
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> {
                GroupMember saved = invocation.getArgument(0);
                saved.setId(UUID.randomUUID());
                saved.setJoinedAt(LocalDateTime.now());
                return saved;
            });
            when(groupRepository.save(any(Group.class))).thenReturn(testGroup);
            when(userServiceClient.getUserSummary(INVITEE_ID)).thenReturn(inviteeSummary);

            GroupMemberResponse response = invitationService.acceptByCode(INVITEE_ID, "abc123xyz");

            assertThat(response).isNotNull();
            verify(invitationRepository).delete(testInvitation);
        }

        @Test
        @DisplayName("User has LEFT status - continues and creates new membership")
        void acceptByCode_UserHasLeftStatus() {
            testInvitation.setInviteeId(null); // Link-based
            GroupMember leftMember = GroupMember.builder()
                    .userId(INVITEE_ID)
                    .status(MemberStatus.LEFT)
                    .build();

            when(invitationRepository.findByInviteCode("abc123xyz")).thenReturn(Optional.of(testInvitation));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.of(leftMember));
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> {
                GroupMember saved = invocation.getArgument(0);
                saved.setId(UUID.randomUUID());
                saved.setJoinedAt(LocalDateTime.now());
                return saved;
            });
            when(groupRepository.save(any(Group.class))).thenReturn(testGroup);
            when(userServiceClient.getUserSummary(INVITEE_ID)).thenReturn(inviteeSummary);

            GroupMemberResponse response = invitationService.acceptByCode(INVITEE_ID, "abc123xyz");

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Invitation is pending but expired - throws 'Invitation has expired'")
        void acceptByCode_PendingButExpired() {
            GroupInvitation spyInvitation = spy(testInvitation);
            spyInvitation.setExpiresAt(LocalDateTime.now().minusDays(1));

            // Force isPending() to return true (bypassing its internal expiration check)
            doReturn(true).when(spyInvitation).isPending();

            when(invitationRepository.findByInviteCode("abc123xyz")).thenReturn(Optional.of(spyInvitation));

            assertThatThrownBy(() -> invitationService.acceptByCode(INVITEE_ID, "abc123xyz"))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Invitation has expired");
        }

        @Test
        @DisplayName("Success - link-based invite (keeps invitation for others)")
        void acceptByCode_SuccessLinkBased() {
            testInvitation.setInviteeId(null); // Link-based

            when(invitationRepository.findByInviteCode("abc123xyz")).thenReturn(Optional.of(testInvitation));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.empty());
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> {
                GroupMember saved = invocation.getArgument(0);
                saved.setId(UUID.randomUUID());
                saved.setJoinedAt(LocalDateTime.now());
                return saved;
            });
            when(groupRepository.save(any(Group.class))).thenReturn(testGroup);
            when(userServiceClient.getUserSummary(INVITEE_ID)).thenReturn(inviteeSummary);

            GroupMemberResponse response = invitationService.acceptByCode(INVITEE_ID, "abc123xyz");

            assertThat(response).isNotNull();
            verify(invitationRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Success - trims whitespace from code")
        void acceptByCode_TrimsCode() {
            testInvitation.setInviteeId(null);

            when(invitationRepository.findByInviteCode("abc123xyz")).thenReturn(Optional.of(testInvitation));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.empty());
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> {
                GroupMember saved = invocation.getArgument(0);
                saved.setId(UUID.randomUUID());
                saved.setJoinedAt(LocalDateTime.now());
                return saved;
            });
            when(groupRepository.save(any(Group.class))).thenReturn(testGroup);
            when(userServiceClient.getUserSummary(INVITEE_ID)).thenReturn(inviteeSummary);

            GroupMemberResponse response = invitationService.acceptByCode(INVITEE_ID, "  abc123xyz  ");

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Code is null - throws ValidationException")
        void acceptByCode_NullCode() {
            assertThatThrownBy(() -> invitationService.acceptByCode(INVITEE_ID, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Invite code is required");
        }

        @Test
        @DisplayName("Code is blank - throws ValidationException")
        void acceptByCode_BlankCode() {
            assertThatThrownBy(() -> invitationService.acceptByCode(INVITEE_ID, "   "))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Invite code is required");
        }

        @Test
        @DisplayName("Invalid code - throws ResourceNotFoundException")
        void acceptByCode_InvalidCode() {
            when(invitationRepository.findByInviteCode("invalidcode")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> invitationService.acceptByCode(INVITEE_ID, "invalidcode"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Invitation not pending - throws ValidationException")
        void acceptByCode_NotPending() {
            testInvitation.setStatus("EXPIRED");

            when(invitationRepository.findByInviteCode("abc123xyz")).thenReturn(Optional.of(testInvitation));

            assertThatThrownBy(() -> invitationService.acceptByCode(INVITEE_ID, "abc123xyz"))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Invitation is no longer valid");
        }

        @Test
        @DisplayName("Invitation expired - throws ValidationException")
        void acceptByCode_Expired() {
            testInvitation.setExpiresAt(LocalDateTime.now().minusDays(1));

            when(invitationRepository.findByInviteCode("abc123xyz")).thenReturn(Optional.of(testInvitation));

            assertThatThrownBy(() -> invitationService.acceptByCode(INVITEE_ID, "abc123xyz"))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Invitation is no longer valid");
        }

        @Test
        @DisplayName("User is banned - throws ValidationException")
        void acceptByCode_UserBanned() {
            GroupMember bannedMember = GroupMember.builder()
                    .userId(INVITEE_ID)
                    .status(MemberStatus.BANNED)
                    .build();

            when(invitationRepository.findByInviteCode("abc123xyz")).thenReturn(Optional.of(testInvitation));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.of(bannedMember));

            assertThatThrownBy(() -> invitationService.acceptByCode(INVITEE_ID, "abc123xyz"))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("You are banned from this group");
        }

        @Test
        @DisplayName("User already a member - throws ValidationException")
        void acceptByCode_AlreadyMember() {
            GroupMember existingMember = GroupMember.builder()
                    .userId(INVITEE_ID)
                    .status(MemberStatus.APPROVED)
                    .build();

            when(invitationRepository.findByInviteCode("abc123xyz")).thenReturn(Optional.of(testInvitation));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITEE_ID)).thenReturn(Optional.of(existingMember));

            assertThatThrownBy(() -> invitationService.acceptByCode(INVITEE_ID, "abc123xyz"))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("You are already a member of this group");
        }
    }

    // ==================== CANCEL INVITATION ====================

    @Nested
    @DisplayName("cancelInvitation")
    class CancelInvitationTests {

        @Test
        @DisplayName("Success - cancels and deletes invitation")
        void cancelInvitation_Success() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(moderatorMember));
            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(testInvitation));

            invitationService.cancelInvitation(INVITER_ID, GROUP_ID, INVITATION_ID);

            verify(invitationRepository).delete(testInvitation);
        }

        @Test
        @DisplayName("Not a member - throws ForbiddenException")
        void cancelInvitation_NotMember() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> invitationService.cancelInvitation(INVITER_ID, GROUP_ID, INVITATION_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("You are not a member of this group");
        }

        @Test
        @DisplayName("Not a moderator - throws ForbiddenException")
        void cancelInvitation_NotModerator() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(regularMember));

            assertThatThrownBy(() -> invitationService.cancelInvitation(INVITER_ID, GROUP_ID, INVITATION_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Moderator access required");
        }

        @Test
        @DisplayName("Invitation not found - throws ResourceNotFoundException")
        void cancelInvitation_InvitationNotFound() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(moderatorMember));
            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> invitationService.cancelInvitation(INVITER_ID, GROUP_ID, INVITATION_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Invitation belongs to different group - throws ValidationException")
        void cancelInvitation_DifferentGroup() {
            Group otherGroup = Group.builder().id(UUID.randomUUID()).build();
            testInvitation.setGroup(otherGroup);

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(moderatorMember));
            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(testInvitation));

            assertThatThrownBy(() -> invitationService.cancelInvitation(INVITER_ID, GROUP_ID, INVITATION_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Invitation does not belong to this group");
        }
    }

    // ==================== GET INVITATION ====================

    @Nested
    @DisplayName("getInvitation")
    class GetInvitationTests {

        @Test
        @DisplayName("Success - returns invitation")
        void getInvitation_Success() {
            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(testInvitation));
            when(userServiceClient.getUserSummary(INVITER_ID)).thenReturn(inviterSummary);

            InvitationResponse response = invitationService.getInvitation(INVITATION_ID);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(INVITATION_ID);
        }

        @Test
        @DisplayName("Invitation not found - throws ResourceNotFoundException")
        void getInvitation_NotFound() {
            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> invitationService.getInvitation(INVITATION_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== GET INVITATION BY CODE ====================

    @Nested
    @DisplayName("getInvitationByCode")
    class GetInvitationByCodeTests {

        @Test
        @DisplayName("Success - returns invitation")
        void getInvitationByCode_Success() {
            when(invitationRepository.findByInviteCode("abc123xyz")).thenReturn(Optional.of(testInvitation));
            when(userServiceClient.getUserSummary(INVITER_ID)).thenReturn(inviterSummary);

            InvitationResponse response = invitationService.getInvitationByCode("abc123xyz");

            assertThat(response).isNotNull();
            assertThat(response.getInviteCode()).isEqualTo("abc123xyz");
        }

        @Test
        @DisplayName("Success - trims whitespace from code")
        void getInvitationByCode_TrimsCode() {
            when(invitationRepository.findByInviteCode("abc123xyz")).thenReturn(Optional.of(testInvitation));
            when(userServiceClient.getUserSummary(INVITER_ID)).thenReturn(inviterSummary);

            InvitationResponse response = invitationService.getInvitationByCode("  abc123xyz  ");

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Code is null - throws ValidationException")
        void getInvitationByCode_NullCode() {
            assertThatThrownBy(() -> invitationService.getInvitationByCode(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Invite code is required");
        }

        @Test
        @DisplayName("Code is blank - throws ValidationException")
        void getInvitationByCode_BlankCode() {
            assertThatThrownBy(() -> invitationService.getInvitationByCode("   "))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Invite code is required");
        }

        @Test
        @DisplayName("Invalid code - throws ResourceNotFoundException")
        void getInvitationByCode_InvalidCode() {
            when(invitationRepository.findByInviteCode("invalidcode")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> invitationService.getInvitationByCode("invalidcode"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== GET MY PENDING INVITATIONS ====================

    @Nested
    @DisplayName("getMyPendingInvitations")
    class GetMyPendingInvitationsTests {

        @Test
        @DisplayName("Success - returns invitations")
        void getMyPendingInvitations_Success() {
            Page<GroupInvitation> page = new PageImpl<>(List.of(testInvitation), PageRequest.of(0, 20), 1);
            when(invitationRepository.findPendingInvitationsForUser(eq(INVITEE_ID), any(LocalDateTime.class), any(Pageable.class)))
                    .thenReturn(page);
            when(userServiceClient.getUserSummary(INVITER_ID)).thenReturn(inviterSummary);

            PageResponse<InvitationResponse> response = invitationService.getMyPendingInvitations(INVITEE_ID, 0, 20);

            assertThat(response.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Success - empty list")
        void getMyPendingInvitations_Empty() {
            Page<GroupInvitation> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
            when(invitationRepository.findPendingInvitationsForUser(eq(INVITEE_ID), any(LocalDateTime.class), any(Pageable.class)))
                    .thenReturn(emptyPage);

            PageResponse<InvitationResponse> response = invitationService.getMyPendingInvitations(INVITEE_ID, 0, 20);

            assertThat(response.getContent()).isEmpty();
        }
    }

    // ==================== GET GROUP INVITATIONS ====================

    @Nested
    @DisplayName("getGroupInvitations")
    class GetGroupInvitationsTests {

        @Test
        @DisplayName("Success - returns invitations")
        void getGroupInvitations_Success() {
            Page<GroupInvitation> page = new PageImpl<>(List.of(testInvitation), PageRequest.of(0, 20), 1);
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(moderatorMember));
            when(invitationRepository.findPendingInvitationsByGroup(eq(GROUP_ID), any(LocalDateTime.class), any(Pageable.class)))
                    .thenReturn(page);
            when(userServiceClient.getUserSummary(INVITER_ID)).thenReturn(inviterSummary);

            PageResponse<InvitationResponse> response = invitationService.getGroupInvitations(INVITER_ID, GROUP_ID, 0, 20);

            assertThat(response.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Not a member - throws ForbiddenException")
        void getGroupInvitations_NotMember() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> invitationService.getGroupInvitations(INVITER_ID, GROUP_ID, 0, 20))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("You are not a member of this group");
        }

        @Test
        @DisplayName("Not a moderator - throws ForbiddenException")
        void getGroupInvitations_NotModerator() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, INVITER_ID)).thenReturn(Optional.of(regularMember));

            assertThatThrownBy(() -> invitationService.getGroupInvitations(INVITER_ID, GROUP_ID, 0, 20))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Moderator access required");
        }
    }

    // ==================== COUNT PENDING INVITATIONS ====================

    @Nested
    @DisplayName("countPendingInvitations")
    class CountPendingInvitationsTests {

        @Test
        @DisplayName("Success - returns count")
        void countPendingInvitations_Success() {
            when(invitationRepository.countPendingInvitationsForUser(eq(INVITEE_ID), any(LocalDateTime.class)))
                    .thenReturn(5L);

            long count = invitationService.countPendingInvitations(INVITEE_ID);

            assertThat(count).isEqualTo(5L);
        }

        @Test
        @DisplayName("Success - zero count")
        void countPendingInvitations_Zero() {
            when(invitationRepository.countPendingInvitationsForUser(eq(INVITEE_ID), any(LocalDateTime.class)))
                    .thenReturn(0L);

            long count = invitationService.countPendingInvitations(INVITEE_ID);

            assertThat(count).isEqualTo(0L);
        }
    }

    // ==================== FETCH USER SUMMARY (Private Method via getInvitation) ====================

    @Nested
    @DisplayName("fetchUserSummary (via getInvitation)")
    class FetchUserSummaryTests {

        @Test
        @DisplayName("Success - returns user summary")
        void fetchUserSummary_Success() {
            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(testInvitation));
            when(userServiceClient.getUserSummary(INVITER_ID)).thenReturn(inviterSummary);

            InvitationResponse response = invitationService.getInvitation(INVITATION_ID);

            assertThat(response.getInviterName()).isEqualTo("inviter");
        }

        @Test
        @DisplayName("User service fails - returns fallback")
        void fetchUserSummary_FallsBack() {
            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(testInvitation));
            when(userServiceClient.getUserSummary(INVITER_ID)).thenThrow(new RuntimeException("Service unavailable"));

            InvitationResponse response = invitationService.getInvitation(INVITATION_ID);

            assertThat(response.getInviter()).isNotNull();
            assertThat(response.getInviter().getUsername()).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("FeignException - returns fallback")
        void fetchUserSummary_FeignExceptionFallsBack() {
            when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(testInvitation));
            when(userServiceClient.getUserSummary(INVITER_ID)).thenThrow(mock(FeignException.class));

            InvitationResponse response = invitationService.getInvitation(INVITATION_ID);

            assertThat(response.getInviter()).isNotNull();
            assertThat(response.getInviter().getUsername()).isEqualTo("Unknown");
        }
    }
}