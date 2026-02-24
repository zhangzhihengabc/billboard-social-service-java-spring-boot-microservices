package com.billboard.social.group.service;

import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.exception.ForbiddenException;
import com.billboard.social.common.exception.ResourceNotFoundException;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.group.dto.request.GroupRequests.*;
import com.billboard.social.group.dto.response.GroupResponses.*;
import com.billboard.social.group.entity.Group;
import com.billboard.social.group.entity.GroupMember;
import com.billboard.social.group.entity.enums.GroupType;
import com.billboard.social.group.entity.enums.MemberRole;
import com.billboard.social.group.entity.enums.MemberStatus;
import com.billboard.social.group.event.GroupEventPublisher;
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
class GroupMemberServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository memberRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private GroupEventPublisher eventPublisher;

    @InjectMocks
    private GroupMemberService memberService;

    // Test constants
    private static final Long USER_ID = 1L;
    private static final Long ADMIN_ID = 2L;
    private static final Long OWNER_ID = 3L;
    private static final Long TARGET_USER_ID = 4L;
    private static final UUID GROUP_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID MEMBER_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    private Group testGroup;
    private GroupMember ownerMember;
    private GroupMember adminMember;
    private GroupMember moderatorMember;
    private GroupMember regularMember;
    private GroupMember targetMember;
    private UserSummary testUserSummary;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(memberService, "maxMembers", 100000);
        ReflectionTestUtils.setField(memberService, "maxAdmins", 100);

        testGroup = Group.builder()
                .id(GROUP_ID)
                .name("Test Group")
                .slug("test-group")
                .groupType(GroupType.PUBLIC)
                .ownerId(OWNER_ID)
                .memberCount(100)
                .requireJoinApproval(false)
                .build();
        testGroup.setCreatedAt(LocalDateTime.now());

        ownerMember = GroupMember.builder()
                .id(UUID.randomUUID())
                .group(testGroup)
                .userId(OWNER_ID)
                .role(MemberRole.OWNER)
                .status(MemberStatus.APPROVED)
                .postCount(50)
                .contributionScore(500)
                .notificationsEnabled(true)
                .build();
        ownerMember.setJoinedAt(LocalDateTime.now());

        adminMember = GroupMember.builder()
                .id(UUID.randomUUID())
                .group(testGroup)
                .userId(ADMIN_ID)
                .role(MemberRole.ADMIN)
                .status(MemberStatus.APPROVED)
                .postCount(30)
                .contributionScore(300)
                .notificationsEnabled(true)
                .build();
        adminMember.setJoinedAt(LocalDateTime.now());

        moderatorMember = GroupMember.builder()
                .id(UUID.randomUUID())
                .group(testGroup)
                .userId(USER_ID)
                .role(MemberRole.MODERATOR)
                .status(MemberStatus.APPROVED)
                .postCount(20)
                .contributionScore(200)
                .notificationsEnabled(true)
                .build();
        moderatorMember.setJoinedAt(LocalDateTime.now());

        regularMember = GroupMember.builder()
                .id(MEMBER_ID)
                .group(testGroup)
                .userId(USER_ID)
                .role(MemberRole.MEMBER)
                .status(MemberStatus.APPROVED)
                .postCount(10)
                .contributionScore(100)
                .notificationsEnabled(true)
                .build();
        regularMember.setJoinedAt(LocalDateTime.now());

        targetMember = GroupMember.builder()
                .id(UUID.randomUUID())
                .group(testGroup)
                .userId(TARGET_USER_ID)
                .role(MemberRole.MEMBER)
                .status(MemberStatus.APPROVED)
                .postCount(5)
                .contributionScore(50)
                .notificationsEnabled(true)
                .build();
        targetMember.setJoinedAt(LocalDateTime.now());

        testUserSummary = UserSummary.builder()
                .id(USER_ID)
                .username("testuser")
                .email("test@gmail.com")
                .build();
    }

    // ==================== JOIN GROUP ====================

    @Nested
    @DisplayName("joinGroup")
    class JoinGroupTests {

        @Test
        @DisplayName("Success - public group (no approval required)")
        void joinGroup_SuccessPublicGroup() {
            JoinGroupRequest request = new JoinGroupRequest();
            testGroup.setRequireJoinApproval(false);

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.empty());
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> {
                GroupMember saved = invocation.getArgument(0);
                saved.setId(MEMBER_ID);
                saved.setJoinedAt(LocalDateTime.now());
                return saved;
            });
            when(groupRepository.save(any(Group.class))).thenReturn(testGroup);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            GroupMemberResponse response = memberService.joinGroup(USER_ID, GROUP_ID, request);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(MemberStatus.APPROVED);
            verify(eventPublisher).publishMemberJoined(any(GroupMember.class));
            verify(groupRepository).save(testGroup);
        }

        @Test
        @DisplayName("Success - private group (requires approval)")
        void joinGroup_SuccessPrivateGroup() {
            JoinGroupRequest request = new JoinGroupRequest();
            testGroup.setRequireJoinApproval(true);

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.empty());
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> {
                GroupMember saved = invocation.getArgument(0);
                saved.setId(MEMBER_ID);
                return saved;
            });
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            GroupMemberResponse response = memberService.joinGroup(USER_ID, GROUP_ID, request);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(MemberStatus.PENDING);
            verify(eventPublisher).publishJoinRequested(any(GroupMember.class));
            verify(groupRepository, never()).save(any()); // Member count not incremented for pending
        }

        @Test
        @DisplayName("Success - with message")
        void joinGroup_WithMessage() {
            JoinGroupRequest request = JoinGroupRequest.builder()
                    .message("Please let me join!")
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.empty());
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> {
                GroupMember saved = invocation.getArgument(0);
                saved.setId(MEMBER_ID);
                saved.setJoinedAt(LocalDateTime.now());
                return saved;
            });
            when(groupRepository.save(any(Group.class))).thenReturn(testGroup);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            GroupMemberResponse response = memberService.joinGroup(USER_ID, GROUP_ID, request);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Success - null request")
        void joinGroup_NullRequest() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.empty());
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> {
                GroupMember saved = invocation.getArgument(0);
                saved.setId(MEMBER_ID);
                saved.setJoinedAt(LocalDateTime.now());
                return saved;
            });
            when(groupRepository.save(any(Group.class))).thenReturn(testGroup);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            GroupMemberResponse response = memberService.joinGroup(USER_ID, GROUP_ID, null);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Success - request with null message")
        void joinGroup_NullMessage() {
            JoinGroupRequest request = JoinGroupRequest.builder()
                    .message(null)
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.empty());
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> {
                GroupMember saved = invocation.getArgument(0);
                saved.setId(MEMBER_ID);
                saved.setJoinedAt(LocalDateTime.now());
                return saved;
            });
            when(groupRepository.save(any(Group.class))).thenReturn(testGroup);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            GroupMemberResponse response = memberService.joinGroup(USER_ID, GROUP_ID, request);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Group not found - throws ResourceNotFoundException")
        void joinGroup_GroupNotFound() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.joinGroup(USER_ID, GROUP_ID, new JoinGroupRequest()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("User is banned - throws ValidationException")
        void joinGroup_UserBanned() {
            GroupMember bannedMember = GroupMember.builder()
                    .userId(USER_ID)
                    .status(MemberStatus.BANNED)
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(bannedMember));

            assertThatThrownBy(() -> memberService.joinGroup(USER_ID, GROUP_ID, new JoinGroupRequest()))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("You are banned from this group");
        }

        @Test
        @DisplayName("Already a member - throws ValidationException")
        void joinGroup_AlreadyMember() {
            GroupMember existingMember = GroupMember.builder()
                    .userId(USER_ID)
                    .status(MemberStatus.APPROVED)
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(existingMember));

            assertThatThrownBy(() -> memberService.joinGroup(USER_ID, GROUP_ID, new JoinGroupRequest()))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Already a member or pending request exists");
        }

        @Test
        @DisplayName("Max members reached - throws ValidationException")
        void joinGroup_MaxMembersReached() {
            testGroup.setMemberCount(100000);

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.joinGroup(USER_ID, GROUP_ID, new JoinGroupRequest()))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Group has reached maximum member capacity");
        }
    }

    // ==================== LEAVE GROUP ====================

    @Nested
    @DisplayName("leaveGroup")
    class LeaveGroupTests {

        @Test
        @DisplayName("Success - member leaves")
        void leaveGroup_Success() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(regularMember));
            when(groupRepository.save(any(Group.class))).thenReturn(testGroup);

            memberService.leaveGroup(USER_ID, GROUP_ID);

            verify(memberRepository).delete(regularMember);
            verify(eventPublisher).publishMemberLeft(regularMember);
            verify(groupRepository).save(testGroup);
        }

        @Test
        @DisplayName("Owner cannot leave - throws ValidationException")
        void leaveGroup_OwnerCannotLeave() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, OWNER_ID)).thenReturn(Optional.of(ownerMember));

            assertThatThrownBy(() -> memberService.leaveGroup(OWNER_ID, GROUP_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Owner cannot leave");
        }

        @Test
        @DisplayName("Member not found - throws ResourceNotFoundException")
        void leaveGroup_NotFound() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.leaveGroup(USER_ID, GROUP_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== GET MEMBER ====================

    @Nested
    @DisplayName("getMember")
    class GetMemberTests {

        @Test
        @DisplayName("Success - returns member")
        void getMember_Success() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(regularMember));
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            GroupMemberResponse response = memberService.getMember(GROUP_ID, USER_ID);

            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("Member not found - throws ResourceNotFoundException")
        void getMember_NotFound() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.getMember(GROUP_ID, USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== GET MEMBERS ====================

    @Nested
    @DisplayName("getMembers")
    class GetMembersTests {

        @Test
        @DisplayName("Success - returns members")
        void getMembers_Success() {
            Page<GroupMember> page = new PageImpl<>(List.of(regularMember), PageRequest.of(0, 20), 1);
            when(memberRepository.findApprovedMembers(eq(GROUP_ID), any(Pageable.class))).thenReturn(page);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            PageResponse<GroupMemberResponse> response = memberService.getMembers(GROUP_ID, 0, 20);

            assertThat(response.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Success - empty list")
        void getMembers_Empty() {
            Page<GroupMember> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
            when(memberRepository.findApprovedMembers(eq(GROUP_ID), any(Pageable.class))).thenReturn(emptyPage);

            PageResponse<GroupMemberResponse> response = memberService.getMembers(GROUP_ID, 0, 20);

            assertThat(response.getContent()).isEmpty();
        }
    }

    // ==================== GET PENDING MEMBERS ====================

    @Nested
    @DisplayName("getPendingMembers")
    class GetPendingMembersTests {

        @Test
        @DisplayName("Success - returns pending members")
        void getPendingMembers_Success() {
            GroupMember pendingMember = GroupMember.builder()
                    .id(MEMBER_ID)
                    .group(testGroup)
                    .userId(TARGET_USER_ID)
                    .role(MemberRole.MEMBER)
                    .status(MemberStatus.PENDING)
                    .build();

            Page<GroupMember> page = new PageImpl<>(List.of(pendingMember), PageRequest.of(0, 20), 1);
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findPendingMembers(eq(GROUP_ID), any(Pageable.class))).thenReturn(page);
            when(userServiceClient.getUserSummary(TARGET_USER_ID)).thenReturn(testUserSummary);

            PageResponse<GroupMemberResponse> response = memberService.getPendingMembers(ADMIN_ID, GROUP_ID, 0, 20);

            assertThat(response.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Not moderator - throws ForbiddenException")
        void getPendingMembers_NotModerator() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(regularMember));

            assertThatThrownBy(() -> memberService.getPendingMembers(USER_ID, GROUP_ID, 0, 20))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Moderator access required");
        }

        @Test
        @DisplayName("Not a member - throws ForbiddenException")
        void getPendingMembers_NotMember() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.getPendingMembers(USER_ID, GROUP_ID, 0, 20))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("You are not a member of this group");
        }
    }

    // ==================== GET BANNED MEMBERS ====================

    @Nested
    @DisplayName("getBannedMembers")
    class GetBannedMembersTests {

        @Test
        @DisplayName("Success - returns banned members")
        void getBannedMembers_Success() {
            GroupMember bannedMember = GroupMember.builder()
                    .id(MEMBER_ID)
                    .group(testGroup)
                    .userId(TARGET_USER_ID)
                    .role(MemberRole.MEMBER)
                    .status(MemberStatus.BANNED)
                    .build();

            Page<GroupMember> page = new PageImpl<>(List.of(bannedMember), PageRequest.of(0, 20), 1);
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findBannedMembers(eq(GROUP_ID), any(Pageable.class))).thenReturn(page);
            when(userServiceClient.getUserSummary(TARGET_USER_ID)).thenReturn(testUserSummary);

            PageResponse<GroupMemberResponse> response = memberService.getBannedMembers(ADMIN_ID, GROUP_ID, 0, 20);

            assertThat(response.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Not moderator - throws ForbiddenException")
        void getBannedMembers_NotModerator() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(regularMember));

            assertThatThrownBy(() -> memberService.getBannedMembers(USER_ID, GROUP_ID, 0, 20))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    // ==================== GET ADMINS AND MODERATORS ====================

    @Nested
    @DisplayName("getAdminsAndModerators")
    class GetAdminsAndModeratorsTests {

        @Test
        @DisplayName("Success - returns admins and moderators")
        void getAdminsAndModerators_Success() {
            when(memberRepository.findModerators(GROUP_ID)).thenReturn(List.of(adminMember, moderatorMember));
            when(userServiceClient.getUserSummary(any(Long.class))).thenReturn(testUserSummary);

            List<GroupMemberResponse> response = memberService.getAdminsAndModerators(GROUP_ID);

            assertThat(response).hasSize(2);
        }

        @Test
        @DisplayName("Success - empty list")
        void getAdminsAndModerators_Empty() {
            when(memberRepository.findModerators(GROUP_ID)).thenReturn(Collections.emptyList());

            List<GroupMemberResponse> response = memberService.getAdminsAndModerators(GROUP_ID);

            assertThat(response).isEmpty();
        }
    }

    // ==================== GET MEMBER IDS ====================

    @Nested
    @DisplayName("getMemberIds")
    class GetMemberIdsTests {

        @Test
        @DisplayName("Success - returns member IDs")
        void getMemberIds_Success() {
            when(memberRepository.findMemberUserIds(GROUP_ID)).thenReturn(List.of(USER_ID, TARGET_USER_ID));

            List<Long> response = memberService.getMemberIds(GROUP_ID);

            assertThat(response).hasSize(2);
        }

        @Test
        @DisplayName("Success - empty list")
        void getMemberIds_Empty() {
            when(memberRepository.findMemberUserIds(GROUP_ID)).thenReturn(Collections.emptyList());

            List<Long> response = memberService.getMemberIds(GROUP_ID);

            assertThat(response).isEmpty();
        }
    }

    // ==================== GET MEMBERSHIP STATS ====================

    @Nested
    @DisplayName("getMembershipStats")
    class GetMembershipStatsTests {

        @Test
        @DisplayName("Success - returns stats")
        void getMembershipStats_Success() {
            when(memberRepository.countApprovedMembers(GROUP_ID)).thenReturn(100L);
            when(memberRepository.countPendingMembers(GROUP_ID)).thenReturn(5L);
            when(memberRepository.countAdmins(GROUP_ID)).thenReturn(3L);
            when(memberRepository.countModerators(GROUP_ID)).thenReturn(5L);
            when(memberRepository.countBannedMembers(GROUP_ID)).thenReturn(2L);

            MembershipStatsResponse response = memberService.getMembershipStats(GROUP_ID);

            assertThat(response.getTotalMembers()).isEqualTo(100L);
            assertThat(response.getPendingRequests()).isEqualTo(5L);
        }
    }

    // ==================== APPROVE JOIN REQUEST ====================

    @Nested
    @DisplayName("approveJoinRequest")
    class ApproveJoinRequestTests {

        @Test
        @DisplayName("Success - approves join request")
        void approveJoinRequest_Success() {
            GroupMember pendingMember = GroupMember.builder()
                    .id(MEMBER_ID)
                    .group(testGroup)
                    .userId(TARGET_USER_ID)
                    .role(MemberRole.MEMBER)
                    .status(MemberStatus.PENDING)
                    .build();

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(pendingMember));
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(groupRepository.save(any(Group.class))).thenReturn(testGroup);
            when(userServiceClient.getUserSummary(TARGET_USER_ID)).thenReturn(testUserSummary);

            GroupMemberResponse response = memberService.approveJoinRequest(ADMIN_ID, GROUP_ID, MEMBER_ID);

            assertThat(response).isNotNull();
            verify(eventPublisher).publishMemberApproved(any(GroupMember.class));
        }

        @Test
        @DisplayName("Member belongs to different group - throws ValidationException")
        void approveJoinRequest_DifferentGroup() {
            Group otherGroup = Group.builder().id(UUID.randomUUID()).build();
            GroupMember pendingMember = GroupMember.builder()
                    .id(MEMBER_ID)
                    .group(otherGroup)
                    .userId(TARGET_USER_ID)
                    .status(MemberStatus.PENDING)
                    .build();

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(pendingMember));

            assertThatThrownBy(() -> memberService.approveJoinRequest(ADMIN_ID, GROUP_ID, MEMBER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Member does not belong to this group");
        }

        @Test
        @DisplayName("Request not pending - throws ValidationException")
        void approveJoinRequest_NotPending() {
            GroupMember approvedMember = GroupMember.builder()
                    .id(MEMBER_ID)
                    .group(testGroup)
                    .userId(TARGET_USER_ID)
                    .status(MemberStatus.APPROVED)
                    .build();

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(approvedMember));

            assertThatThrownBy(() -> memberService.approveJoinRequest(ADMIN_ID, GROUP_ID, MEMBER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Request is not pending");
        }

        @Test
        @DisplayName("Member not found - throws ResourceNotFoundException")
        void approveJoinRequest_MemberNotFound() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.approveJoinRequest(ADMIN_ID, GROUP_ID, MEMBER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== REJECT JOIN REQUEST ====================

    @Nested
    @DisplayName("rejectJoinRequest")
    class RejectJoinRequestTests {

        @Test
        @DisplayName("Success - rejects join request")
        void rejectJoinRequest_Success() {
            GroupMember pendingMember = GroupMember.builder()
                    .id(MEMBER_ID)
                    .group(testGroup)
                    .userId(TARGET_USER_ID)
                    .role(MemberRole.MEMBER)
                    .status(MemberStatus.PENDING)
                    .build();

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(pendingMember));

            memberService.rejectJoinRequest(ADMIN_ID, GROUP_ID, MEMBER_ID);

            verify(memberRepository).delete(pendingMember);
        }

        @Test
        @DisplayName("Member belongs to different group - throws ValidationException")
        void rejectJoinRequest_DifferentGroup() {
            Group otherGroup = Group.builder().id(UUID.randomUUID()).build();
            GroupMember pendingMember = GroupMember.builder()
                    .id(MEMBER_ID)
                    .group(otherGroup)
                    .status(MemberStatus.PENDING)
                    .build();

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(pendingMember));

            assertThatThrownBy(() -> memberService.rejectJoinRequest(ADMIN_ID, GROUP_ID, MEMBER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Member does not belong to this group");
        }

        @Test
        @DisplayName("Request not pending - throws ValidationException")
        void rejectJoinRequest_NotPending() {
            GroupMember approvedMember = GroupMember.builder()
                    .id(MEMBER_ID)
                    .group(testGroup)
                    .status(MemberStatus.APPROVED)
                    .build();

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(approvedMember));

            assertThatThrownBy(() -> memberService.rejectJoinRequest(ADMIN_ID, GROUP_ID, MEMBER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Request is not pending");
        }
    }

    // ==================== REMOVE MEMBER ====================

    @Nested
    @DisplayName("removeMember")
    class RemoveMemberTests {

        @Test
        @DisplayName("Success - admin removes member")
        void removeMember_Success() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.of(targetMember));
            when(groupRepository.save(any(Group.class))).thenReturn(testGroup);

            memberService.removeMember(ADMIN_ID, GROUP_ID, TARGET_USER_ID);

            verify(memberRepository).delete(targetMember);
            verify(eventPublisher).publishMemberRemoved(targetMember, ADMIN_ID);
        }

        @Test
        @DisplayName("Cannot remove owner - throws ForbiddenException")
        void removeMember_CannotRemoveOwner() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, OWNER_ID)).thenReturn(Optional.of(ownerMember));

            assertThatThrownBy(() -> memberService.removeMember(ADMIN_ID, GROUP_ID, OWNER_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Cannot remove the owner");
        }

        @Test
        @DisplayName("Owner can remove member with equal ordinal (owner bypass)")
        void removeMember_OwnerBypassRoleCheck() {
            // Create a member that appears to have OWNER ordinal but isn't actually OWNER
            // Use spy to make the condition reachable
            GroupMember spyMember = spy(GroupMember.builder()
                    .id(UUID.randomUUID())
                    .group(testGroup)
                    .userId(TARGET_USER_ID)
                    .role(MemberRole.ADMIN)  // Actually ADMIN
                    .status(MemberStatus.APPROVED)
                    .build());

            // First call (OWNER check): return ADMIN (passes the check)
            // Second call (ordinal comparison): return role with ordinal 0
            doReturn(MemberRole.ADMIN)   // For OWNER check - passes
                    .doReturn(MemberRole.OWNER)  // For ordinal comparison - makes first condition TRUE
                    .doReturn(MemberRole.OWNER)  // For ordinal comparison again if called
                    .when(spyMember).getRole();

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, OWNER_ID)).thenReturn(Optional.of(ownerMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.of(spyMember));
            when(groupRepository.save(any(Group.class))).thenReturn(testGroup);

            // Should succeed because admin.getRole() == OWNER bypasses the check
            memberService.removeMember(OWNER_ID, GROUP_ID, TARGET_USER_ID);

            verify(memberRepository).delete(spyMember);
            verify(eventPublisher).publishMemberRemoved(spyMember, OWNER_ID);
        }

        @Test
        @DisplayName("Cannot remove member with equal role - throws ForbiddenException")
        void removeMember_CannotRemoveEqualRole() {
            GroupMember otherAdmin = GroupMember.builder()
                    .group(testGroup)
                    .userId(TARGET_USER_ID)
                    .role(MemberRole.ADMIN)
                    .status(MemberStatus.APPROVED)
                    .build();

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.of(otherAdmin));

            assertThatThrownBy(() -> memberService.removeMember(ADMIN_ID, GROUP_ID, TARGET_USER_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Cannot remove member with equal or higher role");
        }

        @Test
        @DisplayName("Owner can remove admin - success")
        void removeMember_OwnerCanRemoveAdmin() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, OWNER_ID)).thenReturn(Optional.of(ownerMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(groupRepository.save(any(Group.class))).thenReturn(testGroup);

            memberService.removeMember(OWNER_ID, GROUP_ID, ADMIN_ID);

            verify(memberRepository).delete(adminMember);
        }

        @Test
        @DisplayName("Member not found - throws ResourceNotFoundException")
        void removeMember_NotFound() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.removeMember(ADMIN_ID, GROUP_ID, TARGET_USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== UPDATE MEMBER ROLE ====================

    @Nested
    @DisplayName("updateMemberRole")
    class UpdateMemberRoleTests {

        @Test
        @DisplayName("Success - admin promotes to moderator")
        void updateMemberRole_SuccessToModerator() {
            UpdateMemberRoleRequest request = UpdateMemberRoleRequest.builder()
                    .role(MemberRole.MODERATOR)
                    .build();

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.of(targetMember));
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(userServiceClient.getUserSummary(TARGET_USER_ID)).thenReturn(testUserSummary);

            GroupMemberResponse response = memberService.updateMemberRole(ADMIN_ID, GROUP_ID, TARGET_USER_ID, request);

            assertThat(response.getRole()).isEqualTo(MemberRole.MODERATOR);
        }

        @Test
        @DisplayName("Only owner can promote to admin - throws ForbiddenException")
        void updateMemberRole_OnlyOwnerCanPromoteToAdmin() {
            UpdateMemberRoleRequest request = UpdateMemberRoleRequest.builder()
                    .role(MemberRole.ADMIN)
                    .build();

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.of(targetMember));

            assertThatThrownBy(() -> memberService.updateMemberRole(ADMIN_ID, GROUP_ID, TARGET_USER_ID, request))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Only owner can promote to admin");
        }

        @Test
        @DisplayName("Owner can promote to admin - success")
        void updateMemberRole_OwnerCanPromoteToAdmin() {
            UpdateMemberRoleRequest request = UpdateMemberRoleRequest.builder()
                    .role(MemberRole.ADMIN)
                    .build();

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, OWNER_ID)).thenReturn(Optional.of(ownerMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.of(targetMember));
            when(memberRepository.findAdmins(GROUP_ID)).thenReturn(List.of(adminMember));
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(userServiceClient.getUserSummary(TARGET_USER_ID)).thenReturn(testUserSummary);

            GroupMemberResponse response = memberService.updateMemberRole(OWNER_ID, GROUP_ID, TARGET_USER_ID, request);

            assertThat(response.getRole()).isEqualTo(MemberRole.ADMIN);
        }

        @Test
        @DisplayName("Cannot change owner's role - throws ForbiddenException")
        void updateMemberRole_CannotChangeOwnerRole() {
            UpdateMemberRoleRequest request = UpdateMemberRoleRequest.builder()
                    .role(MemberRole.ADMIN)
                    .build();

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, OWNER_ID)).thenReturn(Optional.of(ownerMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, OWNER_ID)).thenReturn(Optional.of(ownerMember));

            assertThatThrownBy(() -> memberService.updateMemberRole(OWNER_ID, GROUP_ID, OWNER_ID, request))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Cannot change owner's role");
        }

        @Test
        @DisplayName("Max admin limit reached - throws ValidationException")
        void updateMemberRole_MaxAdminLimitReached() {
            ReflectionTestUtils.setField(memberService, "maxAdmins", 1);
            UpdateMemberRoleRequest request = UpdateMemberRoleRequest.builder()
                    .role(MemberRole.ADMIN)
                    .build();

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, OWNER_ID)).thenReturn(Optional.of(ownerMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.of(targetMember));
            when(memberRepository.findAdmins(GROUP_ID)).thenReturn(List.of(adminMember));

            assertThatThrownBy(() -> memberService.updateMemberRole(OWNER_ID, GROUP_ID, TARGET_USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Maximum admin limit reached");
        }

        @Test
        @DisplayName("Not admin - throws ForbiddenException")
        void updateMemberRole_NotAdmin() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(regularMember));

            assertThatThrownBy(() -> memberService.updateMemberRole(USER_ID, GROUP_ID, TARGET_USER_ID,
                    UpdateMemberRoleRequest.builder().role(MemberRole.MODERATOR).build()))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Admin access required");
        }
    }

    // ==================== PROMOTE TO ADMIN ====================

    @Nested
    @DisplayName("promoteToAdmin")
    class PromoteToAdminTests {

        @Test
        @DisplayName("Success - owner promotes to admin")
        void promoteToAdmin_Success() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, OWNER_ID)).thenReturn(Optional.of(ownerMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.of(targetMember));
            when(memberRepository.findAdmins(GROUP_ID)).thenReturn(List.of(adminMember));
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(userServiceClient.getUserSummary(TARGET_USER_ID)).thenReturn(testUserSummary);

            GroupMemberResponse response = memberService.promoteToAdmin(OWNER_ID, GROUP_ID, TARGET_USER_ID);

            assertThat(response.getRole()).isEqualTo(MemberRole.ADMIN);
        }

        @Test
        @DisplayName("Not owner - throws ForbiddenException")
        void promoteToAdmin_NotOwner() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));

            assertThatThrownBy(() -> memberService.promoteToAdmin(ADMIN_ID, GROUP_ID, TARGET_USER_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Owner access required");
        }

        @Test
        @DisplayName("Member not approved - throws ValidationException")
        void promoteToAdmin_NotApproved() {
            GroupMember pendingMember = GroupMember.builder()
                    .group(testGroup)
                    .userId(TARGET_USER_ID)
                    .status(MemberStatus.PENDING)
                    .build();

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, OWNER_ID)).thenReturn(Optional.of(ownerMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.of(pendingMember));

            assertThatThrownBy(() -> memberService.promoteToAdmin(OWNER_ID, GROUP_ID, TARGET_USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Only approved members can be promoted");
        }

        @Test
        @DisplayName("Cannot promote owner - throws ValidationException")
        void promoteToAdmin_CannotPromoteOwner() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, OWNER_ID)).thenReturn(Optional.of(ownerMember));

            assertThatThrownBy(() -> memberService.promoteToAdmin(OWNER_ID, GROUP_ID, OWNER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Cannot change owner's role");
        }

        @Test
        @DisplayName("Already an admin - throws ValidationException")
        void promoteToAdmin_AlreadyAdmin() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, OWNER_ID)).thenReturn(Optional.of(ownerMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));

            assertThatThrownBy(() -> memberService.promoteToAdmin(OWNER_ID, GROUP_ID, ADMIN_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Member is already an admin");
        }

        @Test
        @DisplayName("Max admin limit reached - throws ValidationException")
        void promoteToAdmin_MaxAdminLimitReached() {
            ReflectionTestUtils.setField(memberService, "maxAdmins", 1);

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, OWNER_ID)).thenReturn(Optional.of(ownerMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.of(targetMember));
            when(memberRepository.findAdmins(GROUP_ID)).thenReturn(List.of(adminMember));

            assertThatThrownBy(() -> memberService.promoteToAdmin(OWNER_ID, GROUP_ID, TARGET_USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Maximum admin limit reached");
        }
    }

    // ==================== PROMOTE TO MODERATOR ====================

    @Nested
    @DisplayName("promoteToModerator")
    class PromoteToModeratorTests {

        @Test
        @DisplayName("Success - admin promotes to moderator")
        void promoteToModerator_Success() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.of(targetMember));
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(userServiceClient.getUserSummary(TARGET_USER_ID)).thenReturn(testUserSummary);

            GroupMemberResponse response = memberService.promoteToModerator(ADMIN_ID, GROUP_ID, TARGET_USER_ID);

            assertThat(response.getRole()).isEqualTo(MemberRole.MODERATOR);
        }

        @Test
        @DisplayName("Not approved - throws ValidationException")
        void promoteToModerator_NotApproved() {
            GroupMember pendingMember = GroupMember.builder()
                    .group(testGroup)
                    .userId(TARGET_USER_ID)
                    .status(MemberStatus.PENDING)
                    .build();

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.of(pendingMember));

            assertThatThrownBy(() -> memberService.promoteToModerator(ADMIN_ID, GROUP_ID, TARGET_USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Only approved members can be promoted");
        }

        @Test
        @DisplayName("Cannot demote admin to moderator - throws ValidationException")
        void promoteToModerator_CannotDemoteAdmin() {
            GroupMember adminTarget = GroupMember.builder()
                    .group(testGroup)
                    .userId(TARGET_USER_ID)
                    .role(MemberRole.ADMIN)
                    .status(MemberStatus.APPROVED)
                    .build();

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, OWNER_ID)).thenReturn(Optional.of(ownerMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.of(adminTarget));

            assertThatThrownBy(() -> memberService.promoteToModerator(OWNER_ID, GROUP_ID, TARGET_USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Cannot demote admin/owner to moderator using this endpoint");
        }

        @Test
        @DisplayName("Cannot demote owner to moderator - throws ValidationException")
        void promoteToModerator_CannotDemoteOwner() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, OWNER_ID)).thenReturn(Optional.of(ownerMember));

            assertThatThrownBy(() -> memberService.promoteToModerator(OWNER_ID, GROUP_ID, OWNER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Cannot demote admin/owner to moderator using this endpoint");
        }

        @Test
        @DisplayName("Already a moderator - throws ValidationException")
        void promoteToModerator_AlreadyModerator() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(moderatorMember));

            assertThatThrownBy(() -> memberService.promoteToModerator(ADMIN_ID, GROUP_ID, USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Member is already a moderator");
        }
    }

    // ==================== DEMOTE ADMIN ====================

    @Nested
    @DisplayName("demoteAdmin")
    class DemoteAdminTests {

        @Test
        @DisplayName("Success - owner demotes admin")
        void demoteAdmin_Success() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, OWNER_ID)).thenReturn(Optional.of(ownerMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(userServiceClient.getUserSummary(ADMIN_ID)).thenReturn(testUserSummary);

            GroupMemberResponse response = memberService.demoteAdmin(OWNER_ID, GROUP_ID, ADMIN_ID);

            assertThat(response.getRole()).isEqualTo(MemberRole.MEMBER);
        }

        @Test
        @DisplayName("Not owner - throws ForbiddenException")
        void demoteAdmin_NotOwner() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));

            assertThatThrownBy(() -> memberService.demoteAdmin(ADMIN_ID, GROUP_ID, TARGET_USER_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Owner access required");
        }

        @Test
        @DisplayName("Member is not an admin - throws ValidationException")
        void demoteAdmin_NotAnAdmin() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, OWNER_ID)).thenReturn(Optional.of(ownerMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.of(targetMember));

            assertThatThrownBy(() -> memberService.demoteAdmin(OWNER_ID, GROUP_ID, TARGET_USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Member is not an admin");
        }
    }

    // ==================== DEMOTE MODERATOR ====================

    @Nested
    @DisplayName("demoteModerator")
    class DemoteModeratorTests {

        @Test
        @DisplayName("Success - admin demotes moderator")
        void demoteModerator_Success() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(moderatorMember));
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            GroupMemberResponse response = memberService.demoteModerator(ADMIN_ID, GROUP_ID, USER_ID);

            assertThat(response.getRole()).isEqualTo(MemberRole.MEMBER);
        }

        @Test
        @DisplayName("Not admin - throws ForbiddenException")
        void demoteModerator_NotAdmin() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(regularMember));

            assertThatThrownBy(() -> memberService.demoteModerator(USER_ID, GROUP_ID, TARGET_USER_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Admin access required");
        }

        @Test
        @DisplayName("Member is not a moderator - throws ValidationException")
        void demoteModerator_NotAModerator() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.of(targetMember));

            assertThatThrownBy(() -> memberService.demoteModerator(ADMIN_ID, GROUP_ID, TARGET_USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Member is not a moderator");
        }
    }

    // ==================== MUTE MEMBER ====================

    @Nested
    @DisplayName("muteMember")
    class MuteMemberTests {

        @Test
        @DisplayName("Success - mutes member")
        void muteMember_Success() {
            MuteMemberRequest request = MuteMemberRequest.builder()
                    .durationHours(24)
                    .reason("Spam")
                    .build();

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.of(targetMember));
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(userServiceClient.getUserSummary(TARGET_USER_ID)).thenReturn(testUserSummary);

            GroupMemberResponse response = memberService.muteMember(ADMIN_ID, GROUP_ID, TARGET_USER_ID, request);

            assertThat(response.getMutedUntil()).isNotNull();
        }

        @Test
        @DisplayName("Null request - throws ValidationException")
        void muteMember_NullRequest() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));

            assertThatThrownBy(() -> memberService.muteMember(ADMIN_ID, GROUP_ID, TARGET_USER_ID, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Request body is required");
        }

        @Test
        @DisplayName("Duration null - throws ValidationException")
        void muteMember_DurationNull() {
            MuteMemberRequest request = MuteMemberRequest.builder()
                    .durationHours(null)
                    .build();

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));

            assertThatThrownBy(() -> memberService.muteMember(ADMIN_ID, GROUP_ID, TARGET_USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Duration must be at least 1 hour");
        }

        @Test
        @DisplayName("Duration less than 1 - throws ValidationException")
        void muteMember_DurationLessThanOne() {
            MuteMemberRequest request = MuteMemberRequest.builder()
                    .durationHours(0)
                    .build();

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));

            assertThatThrownBy(() -> memberService.muteMember(ADMIN_ID, GROUP_ID, TARGET_USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Duration must be at least 1 hour");
        }

        @Test
        @DisplayName("Success - without reason (reason is null)")
        void muteMember_WithoutReason() {
            MuteMemberRequest request = MuteMemberRequest.builder()
                    .durationHours(24)
                    .reason(null)
                    .build();

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.of(targetMember));
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(userServiceClient.getUserSummary(TARGET_USER_ID)).thenReturn(testUserSummary);

            GroupMemberResponse response = memberService.muteMember(ADMIN_ID, GROUP_ID, TARGET_USER_ID, request);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Cannot mute admin - throws ForbiddenException")
        void muteMember_CannotMuteAdmin() {
            MuteMemberRequest request = MuteMemberRequest.builder()
                    .durationHours(24)
                    .build();

            GroupMember adminTarget = GroupMember.builder()
                    .group(testGroup)
                    .userId(TARGET_USER_ID)
                    .role(MemberRole.ADMIN)
                    .status(MemberStatus.APPROVED)
                    .build();

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, OWNER_ID)).thenReturn(Optional.of(ownerMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.of(adminTarget));

            assertThatThrownBy(() -> memberService.muteMember(OWNER_ID, GROUP_ID, TARGET_USER_ID, request))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Cannot mute admins");
        }
    }

    // ==================== UNMUTE MEMBER ====================

    @Nested
    @DisplayName("unmuteMember")
    class UnmuteMemberTests {

        @Test
        @DisplayName("Success - unmutes member")
        void unmuteMember_Success() {
            targetMember.setMutedUntil(LocalDateTime.now().plusDays(1));

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.of(targetMember));
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(userServiceClient.getUserSummary(TARGET_USER_ID)).thenReturn(testUserSummary);

            GroupMemberResponse response = memberService.unmuteMember(ADMIN_ID, GROUP_ID, TARGET_USER_ID);

            assertThat(response.getMutedUntil()).isNull();
        }

        @Test
        @DisplayName("Member not found - throws ResourceNotFoundException")
        void unmuteMember_MemberNotFound() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.unmuteMember(ADMIN_ID, GROUP_ID, TARGET_USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== BAN MEMBER ====================

    @Nested
    @DisplayName("banMember")
    class BanMemberTests {

        @Test
        @DisplayName("Cannot ban owner - throws ForbiddenException")
        void banMember_CannotBanOwner() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, OWNER_ID)).thenReturn(Optional.of(ownerMember));

            assertThatThrownBy(() -> memberService.banMember(ADMIN_ID, GROUP_ID, OWNER_ID, null))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Cannot ban the owner");
        }

        @Test
        @DisplayName("Success - moderator bans member with lower role")
        void banMember_ModeratorBansLowerRole() {
            // Create FRESH moderator - don't use shared moderatorMember
            GroupMember freshModerator = GroupMember.builder()
                    .id(UUID.randomUUID())
                    .group(testGroup)
                    .userId(USER_ID)
                    .role(MemberRole.MODERATOR)
                    .status(MemberStatus.APPROVED)
                    .build();

            // Create FRESH member to ban
            GroupMember memberToBan = GroupMember.builder()
                    .id(UUID.randomUUID())
                    .group(testGroup)
                    .userId(TARGET_USER_ID)
                    .role(MemberRole.MEMBER)
                    .status(MemberStatus.APPROVED)
                    .build();

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(freshModerator));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.of(memberToBan));
            when(memberRepository.save(any(GroupMember.class))).thenReturn(memberToBan);
            when(groupRepository.save(any(Group.class))).thenReturn(testGroup);

            memberService.banMember(USER_ID, GROUP_ID, TARGET_USER_ID, null);

            verify(memberRepository).save(any(GroupMember.class));
            verify(eventPublisher).publishMemberBanned(any(GroupMember.class), eq(USER_ID), isNull());
        }

        @Test
        @DisplayName("Success - bans member with reason (validates reason)")
        void banMember_SuccessWithReason() {
            BanMemberRequest request = BanMemberRequest.builder()
                    .reason("Violation of community guidelines")  // reason != null
                    .build();

            GroupMember memberToBan = GroupMember.builder()
                    .id(UUID.randomUUID())
                    .group(testGroup)
                    .userId(TARGET_USER_ID)
                    .role(MemberRole.MEMBER)
                    .status(MemberStatus.APPROVED)
                    .build();

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.of(memberToBan));
            when(memberRepository.save(any(GroupMember.class))).thenReturn(memberToBan);
            when(groupRepository.save(any(Group.class))).thenReturn(testGroup);

            memberService.banMember(ADMIN_ID, GROUP_ID, TARGET_USER_ID, request);

            verify(memberRepository).save(any(GroupMember.class));
            verify(eventPublisher).publishMemberBanned(any(GroupMember.class), eq(ADMIN_ID), eq("Violation of community guidelines"));
        }

        @Test
        @DisplayName("Success - bans member with request but null reason")
        void banMember_SuccessWithRequestButNullReason() {
            BanMemberRequest request = BanMemberRequest.builder()
                    .reason(null)  // request != null, but reason == null
                    .build();

            GroupMember memberToBan = GroupMember.builder()
                    .id(UUID.randomUUID())
                    .group(testGroup)
                    .userId(TARGET_USER_ID)
                    .role(MemberRole.MEMBER)
                    .status(MemberStatus.APPROVED)
                    .build();

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.of(memberToBan));
            when(memberRepository.save(any(GroupMember.class))).thenReturn(memberToBan);
            when(groupRepository.save(any(Group.class))).thenReturn(testGroup);

            memberService.banMember(ADMIN_ID, GROUP_ID, TARGET_USER_ID, request);

            verify(memberRepository).save(any(GroupMember.class));
            verify(eventPublisher).publishMemberBanned(any(GroupMember.class), eq(ADMIN_ID), isNull());
        }

        @Test
        @DisplayName("Cannot ban member with equal role - throws ForbiddenException")
        void banMember_CannotBanEqualRole() {
            GroupMember otherAdmin = GroupMember.builder()
                    .group(testGroup)
                    .userId(TARGET_USER_ID)
                    .role(MemberRole.ADMIN)
                    .status(MemberStatus.APPROVED)
                    .build();

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.of(otherAdmin));

            assertThatThrownBy(() -> memberService.banMember(ADMIN_ID, GROUP_ID, TARGET_USER_ID, null))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Cannot ban member with equal or higher role");
        }
    }

    // ==================== UNBAN MEMBER ====================

    @Nested
    @DisplayName("unbanMember")
    class UnbanMemberTests {

        @Test
        @DisplayName("Success - unbans member")
        void unbanMember_Success() {
            GroupMember bannedMember = GroupMember.builder()
                    .id(MEMBER_ID)
                    .group(testGroup)
                    .userId(TARGET_USER_ID)
                    .role(MemberRole.MEMBER)
                    .status(MemberStatus.BANNED)
                    .build();

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.of(bannedMember));

            memberService.unbanMember(ADMIN_ID, GROUP_ID, TARGET_USER_ID);

            verify(memberRepository).delete(bannedMember);
        }

        @Test
        @DisplayName("Member is not banned - throws ValidationException")
        void unbanMember_NotBanned() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, ADMIN_ID)).thenReturn(Optional.of(adminMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.of(targetMember));

            assertThatThrownBy(() -> memberService.unbanMember(ADMIN_ID, GROUP_ID, TARGET_USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Member is not banned");
        }
    }

    // ==================== UPDATE MEMBER SETTINGS ====================

    @Nested
    @DisplayName("updateMemberSettings")
    class UpdateMemberSettingsTests {

        @Test
        @DisplayName("Success - updates notifications")
        void updateMemberSettings_Success() {
            UpdateMemberSettingsRequest request = UpdateMemberSettingsRequest.builder()
                    .notificationsEnabled(false)
                    .build();

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(regularMember));
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            GroupMemberResponse response = memberService.updateMemberSettings(USER_ID, GROUP_ID, request);

            assertThat(response.getNotificationsEnabled()).isFalse();
        }

        @Test
        @DisplayName("Success - notificationsEnabled is null (no update)")
        void updateMemberSettings_NotificationsNull() {
            UpdateMemberSettingsRequest request = UpdateMemberSettingsRequest.builder()
                    .notificationsEnabled(null)
                    .build();

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(regularMember));
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            GroupMemberResponse response = memberService.updateMemberSettings(USER_ID, GROUP_ID, request);

            assertThat(response.getNotificationsEnabled()).isTrue(); // Original value
        }

        @Test
        @DisplayName("Member not approved - throws ValidationException")
        void updateMemberSettings_NotApproved() {
            GroupMember pendingMember = GroupMember.builder()
                    .group(testGroup)
                    .userId(USER_ID)
                    .status(MemberStatus.PENDING)
                    .build();

            UpdateMemberSettingsRequest request = UpdateMemberSettingsRequest.builder()
                    .notificationsEnabled(false)
                    .build();

            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(pendingMember));

            assertThatThrownBy(() -> memberService.updateMemberSettings(USER_ID, GROUP_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Only approved members can update settings");
        }

        @Test
        @DisplayName("Member not found - throws ResourceNotFoundException")
        void updateMemberSettings_NotFound() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.updateMemberSettings(USER_ID, GROUP_ID,
                    UpdateMemberSettingsRequest.builder().build()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== TRANSFER OWNERSHIP ====================

    @Nested
    @DisplayName("transferOwnership")
    class TransferOwnershipTests {

        @Test
        @DisplayName("Success - transfers ownership")
        void transferOwnership_Success() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, OWNER_ID)).thenReturn(Optional.of(ownerMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.of(targetMember));
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(groupRepository.save(any(Group.class))).thenReturn(testGroup);

            memberService.transferOwnership(OWNER_ID, GROUP_ID, TARGET_USER_ID);

            verify(groupRepository).save(testGroup);
            assertThat(testGroup.getOwnerId()).isEqualTo(TARGET_USER_ID);
        }

        @Test
        @DisplayName("Not the owner - throws ForbiddenException")
        void transferOwnership_NotOwner() {
            testGroup.setOwnerId(10L); // Different owner

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));

            assertThatThrownBy(() -> memberService.transferOwnership(OWNER_ID, GROUP_ID, TARGET_USER_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Only the owner can transfer ownership");
        }

        @Test
        @DisplayName("New owner not a member - throws ResourceNotFoundException")
        void transferOwnership_NewOwnerNotMember() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, OWNER_ID)).thenReturn(Optional.of(ownerMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.transferOwnership(OWNER_ID, GROUP_ID, TARGET_USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("New owner must be a member");
        }

        @Test
        @DisplayName("New owner not approved - throws ValidationException")
        void transferOwnership_NewOwnerNotApproved() {
            GroupMember pendingMember = GroupMember.builder()
                    .group(testGroup)
                    .userId(TARGET_USER_ID)
                    .status(MemberStatus.PENDING)
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, OWNER_ID)).thenReturn(Optional.of(ownerMember));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, TARGET_USER_ID)).thenReturn(Optional.of(pendingMember));

            assertThatThrownBy(() -> memberService.transferOwnership(OWNER_ID, GROUP_ID, TARGET_USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("New owner must be an approved member");
        }

        @Test
        @DisplayName("Group not found - throws ResourceNotFoundException")
        void transferOwnership_GroupNotFound() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.transferOwnership(OWNER_ID, GROUP_ID, TARGET_USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Owner membership not found - throws ResourceNotFoundException")
        void transferOwnership_OwnerMembershipNotFound() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, OWNER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.transferOwnership(OWNER_ID, GROUP_ID, TARGET_USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Owner membership not found");
        }
    }

    // ==================== FETCH USER SUMMARY (private method via mapToMemberResponse) ====================

    @Nested
    @DisplayName("fetchUserSummary (via getMember)")
    class FetchUserSummaryTests {

        @Test
        @DisplayName("Success - returns user summary")
        void fetchUserSummary_Success() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(regularMember));
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            GroupMemberResponse response = memberService.getMember(GROUP_ID, USER_ID);

            assertThat(response.getUser().getUsername()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("User service fails - returns fallback")
        void fetchUserSummary_FallsBack() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(regularMember));
            when(userServiceClient.getUserSummary(USER_ID)).thenThrow(new RuntimeException("Service unavailable"));

            GroupMemberResponse response = memberService.getMember(GROUP_ID, USER_ID);

            assertThat(response.getUser()).isNotNull();
            assertThat(response.getUser().getUsername()).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("FeignException - returns fallback")
        void fetchUserSummary_FeignExceptionFallsBack() {
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(regularMember));
            when(userServiceClient.getUserSummary(USER_ID)).thenThrow(mock(FeignException.class));

            GroupMemberResponse response = memberService.getMember(GROUP_ID, USER_ID);

            assertThat(response.getUser()).isNotNull();
            assertThat(response.getUser().getUsername()).isEqualTo("Unknown");
        }
    }
}