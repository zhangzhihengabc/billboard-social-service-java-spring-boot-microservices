package com.billboard.social.group.service;

import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.exception.ForbiddenException;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.group.dto.request.GroupRequests.CreateGroupRequest;
import com.billboard.social.group.dto.request.GroupRequests.UpdateGroupRequest;
import com.billboard.social.group.dto.response.GroupResponses.GroupResponse;
import com.billboard.social.group.dto.response.GroupResponses.GroupSummaryResponse;
import com.billboard.social.group.dto.response.GroupResponses.MembershipResponse;
import com.billboard.social.group.entity.Group;
import com.billboard.social.group.entity.GroupCategory;
import com.billboard.social.group.entity.GroupMember;
import com.billboard.social.group.entity.enums.GroupType;
import com.billboard.social.group.entity.enums.MemberRole;
import com.billboard.social.group.entity.enums.MemberStatus;
import com.billboard.social.group.event.GroupEventPublisher;
import com.billboard.social.group.repository.GroupCategoryRepository;
import com.billboard.social.group.repository.GroupMemberRepository;
import com.billboard.social.group.repository.GroupRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository memberRepository;

    @Mock
    private GroupCategoryRepository categoryRepository;

    @Mock
    private GroupEventPublisher eventPublisher;

    @InjectMocks
    private GroupService groupService;

    // Test constants
    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final UUID GROUP_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CATEGORY_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private Group testGroup;
    private GroupCategory testCategory;
    private GroupMember testMember;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(groupService, "maxUserGroups", 500);

        testGroup = Group.builder()
                .id(GROUP_ID)
                .name("Test Group")
                .slug("test-group")
                .description("A test group")
                .groupType(GroupType.PUBLIC)
                .ownerId(USER_ID)
                .categoryId(CATEGORY_ID)
                .memberCount(100)
                .postCount(50)
                .isVerified(false)
                .isFeatured(false)
                .allowMemberPosts(true)
                .requirePostApproval(false)
                .requireJoinApproval(false)
                .allowMemberInvites(true)
                .build();
        testGroup.setCreatedAt(LocalDateTime.now());

        testCategory = GroupCategory.builder()
                .id(CATEGORY_ID)
                .name("Technology")
                .slug("technology")
                .groupCount(10)
                .build();

        testMember = GroupMember.builder()
                .id(UUID.randomUUID())
                .group(testGroup)
                .userId(USER_ID)
                .role(MemberRole.OWNER)
                .status(MemberStatus.APPROVED)
                .notificationsEnabled(true)
                .build();
        testMember.setJoinedAt(LocalDateTime.now());
    }

    // ==================== CREATE GROUP ====================

    @Nested
    @DisplayName("createGroup")
    class CreateGroupTests {

        @Test
        @DisplayName("Success - with all fields")
        void createGroup_SuccessAllFields() {
            CreateGroupRequest request = CreateGroupRequest.builder()
                    .name("Test Group")
                    .description("A test group")
                    .groupType(GroupType.PUBLIC)
                    .categoryId(CATEGORY_ID)
                    .location("New York")
                    .website("https://example.com")
                    .rules("Be nice")
                    .allowMemberPosts(true)
                    .requirePostApproval(false)
                    .requireJoinApproval(false)
                    .allowMemberInvites(true)
                    .build();

            when(groupRepository.countGroupsByMember(USER_ID)).thenReturn(10L);
            when(groupRepository.existsBySlug("test-group")).thenReturn(false);
            when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> {
                Group saved = invocation.getArgument(0);
                saved.setId(GROUP_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));

            GroupResponse response = groupService.createGroup(USER_ID, request);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(GROUP_ID);
            assertThat(response.getName()).isEqualTo("Test Group");
            assertThat(response.getGroupType()).isEqualTo(GroupType.PUBLIC);
            verify(eventPublisher).publishGroupCreated(any(Group.class));
            verify(categoryRepository).save(any(GroupCategory.class));
        }

        @Test
        @DisplayName("Success - groupType null (defaults to PUBLIC)")
        void createGroup_GroupTypeNull() {
            CreateGroupRequest request = CreateGroupRequest.builder()
                    .name("Test Group")
                    .build();

            when(groupRepository.countGroupsByMember(USER_ID)).thenReturn(10L);
            when(groupRepository.existsBySlug("test-group")).thenReturn(false);
            when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> {
                Group saved = invocation.getArgument(0);
                saved.setId(GROUP_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupResponse response = groupService.createGroup(USER_ID, request);

            assertThat(response.getGroupType()).isEqualTo(GroupType.PUBLIC);
        }

        @Test
        @DisplayName("Success - allowMemberPosts null (defaults to true)")
        void createGroup_AllowMemberPostsNull() {
            CreateGroupRequest request = CreateGroupRequest.builder()
                    .name("Test Group")
                    .allowMemberPosts(null)
                    .build();

            when(groupRepository.countGroupsByMember(USER_ID)).thenReturn(10L);
            when(groupRepository.existsBySlug("test-group")).thenReturn(false);
            when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> {
                Group saved = invocation.getArgument(0);
                saved.setId(GROUP_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupResponse response = groupService.createGroup(USER_ID, request);

            assertThat(response.getAllowMemberPosts()).isTrue();
        }

        @Test
        @DisplayName("Success - requirePostApproval null (defaults to false)")
        void createGroup_RequirePostApprovalNull() {
            CreateGroupRequest request = CreateGroupRequest.builder()
                    .name("Test Group")
                    .requirePostApproval(null)
                    .build();

            when(groupRepository.countGroupsByMember(USER_ID)).thenReturn(10L);
            when(groupRepository.existsBySlug("test-group")).thenReturn(false);
            when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> {
                Group saved = invocation.getArgument(0);
                saved.setId(GROUP_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupResponse response = groupService.createGroup(USER_ID, request);

            assertThat(response.getRequirePostApproval()).isFalse();
        }

        @Test
        @DisplayName("Success - requireJoinApproval null (defaults to false)")
        void createGroup_RequireJoinApprovalNull() {
            CreateGroupRequest request = CreateGroupRequest.builder()
                    .name("Test Group")
                    .requireJoinApproval(null)
                    .build();

            when(groupRepository.countGroupsByMember(USER_ID)).thenReturn(10L);
            when(groupRepository.existsBySlug("test-group")).thenReturn(false);
            when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> {
                Group saved = invocation.getArgument(0);
                saved.setId(GROUP_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupResponse response = groupService.createGroup(USER_ID, request);

            assertThat(response.getRequireJoinApproval()).isFalse();
        }

        @Test
        @DisplayName("Success - allowMemberInvites null (defaults to true)")
        void createGroup_AllowMemberInvitesNull() {
            CreateGroupRequest request = CreateGroupRequest.builder()
                    .name("Test Group")
                    .allowMemberInvites(null)
                    .build();

            when(groupRepository.countGroupsByMember(USER_ID)).thenReturn(10L);
            when(groupRepository.existsBySlug("test-group")).thenReturn(false);
            when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> {
                Group saved = invocation.getArgument(0);
                saved.setId(GROUP_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupResponse response = groupService.createGroup(USER_ID, request);

            assertThat(response.getAllowMemberInvites()).isTrue();
        }

        @Test
        @DisplayName("Success - with explicit boolean values")
        void createGroup_ExplicitBooleanValues() {
            CreateGroupRequest request = CreateGroupRequest.builder()
                    .name("Test Group")
                    .allowMemberPosts(false)
                    .requirePostApproval(true)
                    .requireJoinApproval(true)
                    .allowMemberInvites(false)
                    .build();

            when(groupRepository.countGroupsByMember(USER_ID)).thenReturn(10L);
            when(groupRepository.existsBySlug("test-group")).thenReturn(false);
            when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> {
                Group saved = invocation.getArgument(0);
                saved.setId(GROUP_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupResponse response = groupService.createGroup(USER_ID, request);

            assertThat(response.getAllowMemberPosts()).isFalse();
            assertThat(response.getRequirePostApproval()).isTrue();
            assertThat(response.getRequireJoinApproval()).isTrue();
            assertThat(response.getAllowMemberInvites()).isFalse();
        }

        @Test
        @DisplayName("Success - without category (categoryId null)")
        void createGroup_WithoutCategory() {
            CreateGroupRequest request = CreateGroupRequest.builder()
                    .name("Test Group")
                    .categoryId(null)
                    .build();

            when(groupRepository.countGroupsByMember(USER_ID)).thenReturn(10L);
            when(groupRepository.existsBySlug("test-group")).thenReturn(false);
            when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> {
                Group saved = invocation.getArgument(0);
                saved.setId(GROUP_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupResponse response = groupService.createGroup(USER_ID, request);

            assertThat(response.getCategoryId()).isNull();
            verify(categoryRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Success - category not found (ifPresent skipped)")
        void createGroup_CategoryNotFound() {
            CreateGroupRequest request = CreateGroupRequest.builder()
                    .name("Test Group")
                    .categoryId(CATEGORY_ID)
                    .build();

            when(groupRepository.countGroupsByMember(USER_ID)).thenReturn(10L);
            when(groupRepository.existsBySlug("test-group")).thenReturn(false);
            when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> {
                Group saved = invocation.getArgument(0);
                saved.setId(GROUP_ID);
                saved.setCategoryId(CATEGORY_ID);  // Ensure categoryId is set
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

            GroupResponse response = groupService.createGroup(USER_ID, request);

            assertThat(response).isNotNull();
            // Called twice: once in createGroup (increment count), once in mapToGroupResponse (get name)
            verify(categoryRepository, times(2)).findById(CATEGORY_ID);
            verify(categoryRepository, never()).save(any());  // Never saves because category not found
        }

        @Test
        @DisplayName("Max group limit reached - throws ValidationException")
        void createGroup_MaxLimitReached() {
            CreateGroupRequest request = CreateGroupRequest.builder()
                    .name("Test Group")
                    .build();

            when(groupRepository.countGroupsByMember(USER_ID)).thenReturn(500L);

            assertThatThrownBy(() -> groupService.createGroup(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Maximum group limit reached");

            verify(groupRepository, never()).save(any());
        }

        @Test
        @DisplayName("Race condition (DataIntegrityViolationException) - throws ValidationException")
        void createGroup_RaceCondition() {
            CreateGroupRequest request = CreateGroupRequest.builder()
                    .name("Test Group")
                    .build();

            when(groupRepository.countGroupsByMember(USER_ID)).thenReturn(10L);
            when(groupRepository.existsBySlug("test-group")).thenReturn(false);
            when(groupRepository.save(any(Group.class)))
                    .thenThrow(new DataIntegrityViolationException("Duplicate entry"));

            assertThatThrownBy(() -> groupService.createGroup(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("A group with this name already exists");
        }

        @Test
        @DisplayName("Success - slug collision handled")
        void createGroup_SlugCollision() {
            CreateGroupRequest request = CreateGroupRequest.builder()
                    .name("Test Group")
                    .build();

            when(groupRepository.countGroupsByMember(USER_ID)).thenReturn(10L);
            when(groupRepository.existsBySlug("test-group")).thenReturn(true);
            when(groupRepository.existsBySlug("test-group-1")).thenReturn(false);
            when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> {
                Group saved = invocation.getArgument(0);
                saved.setId(GROUP_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(memberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupResponse response = groupService.createGroup(USER_ID, request);

            assertThat(response.getSlug()).isEqualTo("test-group-1");
        }
    }

    // ==================== UPDATE GROUP ====================

    @Nested
    @DisplayName("updateGroup")
    class UpdateGroupTests {

        @Test
        @DisplayName("Success - update all fields")
        void updateGroup_SuccessAllFields() {
            UpdateGroupRequest request = UpdateGroupRequest.builder()
                    .name("Updated Group")
                    .description("Updated description")
                    .groupType(GroupType.PRIVATE)
                    .categoryId(CATEGORY_ID)
                    .location("Los Angeles")
                    .website("https://updated.com")
                    .rules("Updated rules")
                    .allowMemberPosts(false)
                    .requirePostApproval(true)
                    .requireJoinApproval(true)
                    .allowMemberInvites(false)
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(testMember));
            when(groupRepository.existsBySlug("updated-group")).thenReturn(false);
            when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupResponse response = groupService.updateGroup(USER_ID, GROUP_ID, request);

            assertThat(response.getName()).isEqualTo("Updated Group");
            assertThat(response.getDescription()).isEqualTo("Updated description");
            assertThat(response.getGroupType()).isEqualTo(GroupType.PRIVATE);
        }

        @Test
        @DisplayName("Success - name is null (not updated)")
        void updateGroup_NameNull() {
            UpdateGroupRequest request = UpdateGroupRequest.builder()
                    .description("Updated description")
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(testMember));
            when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupResponse response = groupService.updateGroup(USER_ID, GROUP_ID, request);

            assertThat(response.getName()).isEqualTo("Test Group");
        }

        @Test
        @DisplayName("Success - description is null (not updated)")
        void updateGroup_DescriptionNull() {
            testGroup.setDescription("Original description");
            UpdateGroupRequest request = UpdateGroupRequest.builder()
                    .location("New York")
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(testMember));
            when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupResponse response = groupService.updateGroup(USER_ID, GROUP_ID, request);

            assertThat(response.getDescription()).isEqualTo("Original description");
        }

        @Test
        @DisplayName("Success - groupType is null (not updated)")
        void updateGroup_GroupTypeNull() {
            UpdateGroupRequest request = UpdateGroupRequest.builder()
                    .description("Updated")
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(testMember));
            when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupResponse response = groupService.updateGroup(USER_ID, GROUP_ID, request);

            assertThat(response.getGroupType()).isEqualTo(GroupType.PUBLIC);
        }

        @Test
        @DisplayName("Success - categoryId is null (not updated)")
        void updateGroup_CategoryIdNull() {
            testGroup.setCategoryId(CATEGORY_ID);
            UpdateGroupRequest request = UpdateGroupRequest.builder()
                    .description("Updated")
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(testMember));
            when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupResponse response = groupService.updateGroup(USER_ID, GROUP_ID, request);

            assertThat(response.getCategoryId()).isEqualTo(CATEGORY_ID);
        }

        @Test
        @DisplayName("Success - location is null (not updated)")
        void updateGroup_LocationNull() {
            testGroup.setLocation("Original location");
            UpdateGroupRequest request = UpdateGroupRequest.builder()
                    .description("Updated")
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(testMember));
            when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupResponse response = groupService.updateGroup(USER_ID, GROUP_ID, request);

            assertThat(response.getLocation()).isEqualTo("Original location");
        }

        @Test
        @DisplayName("Success - website is null (not updated)")
        void updateGroup_WebsiteNull() {
            testGroup.setWebsite("https://original.com");
            UpdateGroupRequest request = UpdateGroupRequest.builder()
                    .description("Updated")
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(testMember));
            when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupResponse response = groupService.updateGroup(USER_ID, GROUP_ID, request);

            assertThat(response.getWebsite()).isEqualTo("https://original.com");
        }

        @Test
        @DisplayName("Success - rules is null (not updated)")
        void updateGroup_RulesNull() {
            testGroup.setRules("Original rules");
            UpdateGroupRequest request = UpdateGroupRequest.builder()
                    .description("Updated")
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(testMember));
            when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupResponse response = groupService.updateGroup(USER_ID, GROUP_ID, request);

            assertThat(response.getRules()).isEqualTo("Original rules");
        }

        @Test
        @DisplayName("Success - allowMemberPosts is null (not updated)")
        void updateGroup_AllowMemberPostsNull() {
            testGroup.setAllowMemberPosts(true);
            UpdateGroupRequest request = UpdateGroupRequest.builder()
                    .description("Updated")
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(testMember));
            when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupResponse response = groupService.updateGroup(USER_ID, GROUP_ID, request);

            assertThat(response.getAllowMemberPosts()).isTrue();
        }

        @Test
        @DisplayName("Success - requirePostApproval is null (not updated)")
        void updateGroup_RequirePostApprovalNull() {
            testGroup.setRequirePostApproval(false);
            UpdateGroupRequest request = UpdateGroupRequest.builder()
                    .description("Updated")
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(testMember));
            when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupResponse response = groupService.updateGroup(USER_ID, GROUP_ID, request);

            assertThat(response.getRequirePostApproval()).isFalse();
        }

        @Test
        @DisplayName("Success - requireJoinApproval is null (not updated)")
        void updateGroup_RequireJoinApprovalNull() {
            testGroup.setRequireJoinApproval(false);
            UpdateGroupRequest request = UpdateGroupRequest.builder()
                    .description("Updated")
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(testMember));
            when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupResponse response = groupService.updateGroup(USER_ID, GROUP_ID, request);

            assertThat(response.getRequireJoinApproval()).isFalse();
        }

        @Test
        @DisplayName("Success - allowMemberInvites is null (not updated)")
        void updateGroup_AllowMemberInvitesNull() {
            testGroup.setAllowMemberInvites(true);
            UpdateGroupRequest request = UpdateGroupRequest.builder()
                    .description("Updated")
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(testMember));
            when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GroupResponse response = groupService.updateGroup(USER_ID, GROUP_ID, request);

            assertThat(response.getAllowMemberInvites()).isTrue();
        }

        @Test
        @DisplayName("Group not found - throws ValidationException")
        void updateGroup_NotFound() {
            UpdateGroupRequest request = UpdateGroupRequest.builder()
                    .name("Updated")
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> groupService.updateGroup(USER_ID, GROUP_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Group not found");
        }

        @Test
        @DisplayName("Not a member - throws ForbiddenException")
        void updateGroup_NotMember() {
            UpdateGroupRequest request = UpdateGroupRequest.builder()
                    .name("Updated")
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> groupService.updateGroup(USER_ID, GROUP_ID, request))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("You are not a member of this group");
        }

        @Test
        @DisplayName("Not admin - throws ForbiddenException")
        void updateGroup_NotAdmin() {
            UpdateGroupRequest request = UpdateGroupRequest.builder()
                    .name("Updated")
                    .build();

            GroupMember regularMember = GroupMember.builder()
                    .group(testGroup)
                    .userId(USER_ID)
                    .role(MemberRole.MEMBER)
                    .status(MemberStatus.APPROVED)
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(regularMember));

            assertThatThrownBy(() -> groupService.updateGroup(USER_ID, GROUP_ID, request))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Admin access required");
        }

        @Test
        @DisplayName("Race condition (DataIntegrityViolationException) - throws ValidationException")
        void updateGroup_RaceCondition() {
            UpdateGroupRequest request = UpdateGroupRequest.builder()
                    .name("New Name")
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(testMember));
            when(groupRepository.existsBySlug("new-name")).thenReturn(false);
            when(groupRepository.save(any(Group.class)))
                    .thenThrow(new DataIntegrityViolationException("Duplicate entry"));

            assertThatThrownBy(() -> groupService.updateGroup(USER_ID, GROUP_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("A group with this name already exists");
        }
    }

    // ==================== DELETE GROUP ====================

    @Nested
    @DisplayName("deleteGroup")
    class DeleteGroupTests {

        @Test
        @DisplayName("Success - deletes group and decrements category count")
        void deleteGroup_SuccessWithCategory() {
            testGroup.setCategoryId(CATEGORY_ID);
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));

            groupService.deleteGroup(USER_ID, GROUP_ID);

            verify(memberRepository).deleteByGroupId(GROUP_ID);
            verify(groupRepository).delete(testGroup);
            verify(eventPublisher).publishGroupDeleted(testGroup);
            verify(categoryRepository).save(any(GroupCategory.class));
        }

        @Test
        @DisplayName("Success - deletes group without category")
        void deleteGroup_SuccessWithoutCategory() {
            testGroup.setCategoryId(null);
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));

            groupService.deleteGroup(USER_ID, GROUP_ID);

            verify(memberRepository).deleteByGroupId(GROUP_ID);
            verify(groupRepository).delete(testGroup);
            verify(categoryRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Success - category not found (ifPresent skipped)")
        void deleteGroup_CategoryNotFound() {
            testGroup.setCategoryId(CATEGORY_ID);
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

            groupService.deleteGroup(USER_ID, GROUP_ID);

            verify(groupRepository).delete(testGroup);
            verify(categoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Success - category count doesn't go below 0")
        void deleteGroup_CategoryCountZero() {
            testGroup.setCategoryId(CATEGORY_ID);
            testCategory.setGroupCount(0);
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));

            groupService.deleteGroup(USER_ID, GROUP_ID);

            verify(categoryRepository).save(argThat(cat -> cat.getGroupCount() == 0));
        }

        @Test
        @DisplayName("Group not found - throws ValidationException")
        void deleteGroup_NotFound() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> groupService.deleteGroup(USER_ID, GROUP_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Group not found");
        }

        @Test
        @DisplayName("Not owner - throws ForbiddenException")
        void deleteGroup_NotOwner() {
            testGroup.setOwnerId(OTHER_USER_ID);
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));

            assertThatThrownBy(() -> groupService.deleteGroup(USER_ID, GROUP_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Only the owner can delete the group");

            verify(groupRepository, never()).delete(any());
        }
    }

    // ==================== GET GROUP ====================

    @Nested
    @DisplayName("getGroup")
    class GetGroupTests {

        @Test
        @DisplayName("Success - public group")
        void getGroup_SuccessPublic() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));

            GroupResponse response = groupService.getGroup(GROUP_ID, USER_ID);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(GROUP_ID);
        }

        @Test
        @DisplayName("Success - public group with null currentUserId")
        void getGroup_PublicWithNullUser() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));

            GroupResponse response = groupService.getGroup(GROUP_ID, null);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Success - private group, user is member")
        void getGroup_PrivateGroupMember() {
            testGroup.setGroupType(GroupType.PRIVATE);
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.existsByGroupIdAndUserIdAndStatus(GROUP_ID, USER_ID, MemberStatus.APPROVED))
                    .thenReturn(true);

            GroupResponse response = groupService.getGroup(GROUP_ID, USER_ID);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Success - public group with null currentUserId")
        void getGroupBySlug_PublicWithNullUser() {
            when(groupRepository.findBySlug("test-group")).thenReturn(Optional.of(testGroup));

            GroupResponse response = groupService.getGroupBySlug("test-group", null);

            assertThat(response).isNotNull();
            // isMember() should NOT be called for public groups
            verify(memberRepository, never()).existsByGroupIdAndUserIdAndStatus(any(), any(), any());
        }

        @Test
        @DisplayName("Success - private group, user is member")
        void getGroupBySlug_PrivateGroupMember() {
            testGroup.setGroupType(GroupType.PRIVATE);
            when(groupRepository.findBySlug("test-group")).thenReturn(Optional.of(testGroup));
            when(memberRepository.existsByGroupIdAndUserIdAndStatus(GROUP_ID, USER_ID, MemberStatus.APPROVED))
                    .thenReturn(true);

            GroupResponse response = groupService.getGroupBySlug("test-group", USER_ID);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Success - secret group, user is member")
        void getGroupBySlug_SecretGroupMember() {
            testGroup.setGroupType(GroupType.SECRET);
            when(groupRepository.findBySlug("test-group")).thenReturn(Optional.of(testGroup));
            when(memberRepository.existsByGroupIdAndUserIdAndStatus(GROUP_ID, USER_ID, MemberStatus.APPROVED))
                    .thenReturn(true);

            GroupResponse response = groupService.getGroupBySlug("test-group", USER_ID);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Secret group, currentUserId is null - throws ForbiddenException")
        void getGroupBySlug_SecretGroupNullUser() {
            testGroup.setGroupType(GroupType.SECRET);
            when(groupRepository.findBySlug("test-group")).thenReturn(Optional.of(testGroup));

            assertThatThrownBy(() -> groupService.getGroupBySlug("test-group", null))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("This group is private");

            verify(memberRepository, never()).existsByGroupIdAndUserIdAndStatus(any(), any(), any());
        }

        @Test
        @DisplayName("Secret group, user is not member - throws ForbiddenException")
        void getGroupBySlug_SecretGroupNotMember() {
            testGroup.setGroupType(GroupType.SECRET);
            when(groupRepository.findBySlug("test-group")).thenReturn(Optional.of(testGroup));
            when(memberRepository.existsByGroupIdAndUserIdAndStatus(GROUP_ID, USER_ID, MemberStatus.APPROVED))
                    .thenReturn(false);

            assertThatThrownBy(() -> groupService.getGroupBySlug("test-group", USER_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("This group is private");
        }

        @Test
        @DisplayName("Secret group, currentUserId is null - throws ForbiddenException")
        void getGroup_SecretGroupNullUser() {
            testGroup.setGroupType(GroupType.SECRET);
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));

            assertThatThrownBy(() -> groupService.getGroup(GROUP_ID, null))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("This group is private");

            // isMember() should NOT be called because currentUserId == null short-circuits
            verify(memberRepository, never()).existsByGroupIdAndUserIdAndStatus(any(), any(), any());
        }

        @Test
        @DisplayName("Success - secret group, user is member")
        void getGroup_SecretGroupMember() {
            testGroup.setGroupType(GroupType.SECRET);
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.existsByGroupIdAndUserIdAndStatus(GROUP_ID, USER_ID, MemberStatus.APPROVED))
                    .thenReturn(true);

            GroupResponse response = groupService.getGroup(GROUP_ID, USER_ID);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Private group, currentUserId is null - throws ForbiddenException")
        void getGroup_PrivateGroupNullUser() {
            testGroup.setGroupType(GroupType.PRIVATE);
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));

            assertThatThrownBy(() -> groupService.getGroup(GROUP_ID, null))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("This group is private");
        }

        @Test
        @DisplayName("Private group, user is not member - throws ForbiddenException")
        void getGroup_PrivateGroupNotMember() {
            testGroup.setGroupType(GroupType.PRIVATE);
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.existsByGroupIdAndUserIdAndStatus(GROUP_ID, USER_ID, MemberStatus.APPROVED))
                    .thenReturn(false);

            assertThatThrownBy(() -> groupService.getGroup(GROUP_ID, USER_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("This group is private");
        }

        @Test
        @DisplayName("Secret group, user is not member - throws ForbiddenException")
        void getGroup_SecretGroupNotMember() {
            testGroup.setGroupType(GroupType.SECRET);
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.existsByGroupIdAndUserIdAndStatus(GROUP_ID, USER_ID, MemberStatus.APPROVED))
                    .thenReturn(false);

            assertThatThrownBy(() -> groupService.getGroup(GROUP_ID, USER_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("This group is private");
        }

        @Test
        @DisplayName("Group not found - throws ValidationException")
        void getGroup_NotFound() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> groupService.getGroup(GROUP_ID, USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Group not found");
        }
    }

    // ==================== GET GROUP BY SLUG ====================

    @Nested
    @DisplayName("getGroupBySlug")
    class GetGroupBySlugTests {

        @Test
        @DisplayName("Success - returns group")
        void getGroupBySlug_Success() {
            when(groupRepository.findBySlug("test-group")).thenReturn(Optional.of(testGroup));

            GroupResponse response = groupService.getGroupBySlug("test-group", USER_ID);

            assertThat(response).isNotNull();
            assertThat(response.getSlug()).isEqualTo("test-group");
        }

        @Test
        @DisplayName("Slug is null - throws ValidationException")
        void getGroupBySlug_NullSlug() {
            assertThatThrownBy(() -> groupService.getGroupBySlug(null, USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Slug is required");
        }

        @Test
        @DisplayName("Slug is blank - throws ValidationException")
        void getGroupBySlug_BlankSlug() {
            assertThatThrownBy(() -> groupService.getGroupBySlug("   ", USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Slug is required");
        }

        @Test
        @DisplayName("Slug with null character - sanitized")
        void getGroupBySlug_NullCharacterSanitized() {
            when(groupRepository.findBySlug("test-group")).thenReturn(Optional.of(testGroup));

            GroupResponse response = groupService.getGroupBySlug("test\u0000-group", USER_ID);

            assertThat(response).isNotNull();
            verify(groupRepository).findBySlug("test-group");
        }

        @Test
        @DisplayName("Group not found - throws ValidationException")
        void getGroupBySlug_NotFound() {
            when(groupRepository.findBySlug("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> groupService.getGroupBySlug("nonexistent", USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Group not found with slug");
        }

        @Test
        @DisplayName("Private group, currentUserId is null - throws ForbiddenException")
        void getGroupBySlug_PrivateGroupNullUser() {
            testGroup.setGroupType(GroupType.PRIVATE);
            when(groupRepository.findBySlug("test-group")).thenReturn(Optional.of(testGroup));

            assertThatThrownBy(() -> groupService.getGroupBySlug("test-group", null))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("This group is private");
        }

        @Test
        @DisplayName("Private group, user is not member - throws ForbiddenException")
        void getGroupBySlug_PrivateGroupNotMember() {
            testGroup.setGroupType(GroupType.PRIVATE);
            when(groupRepository.findBySlug("test-group")).thenReturn(Optional.of(testGroup));
            when(memberRepository.existsByGroupIdAndUserIdAndStatus(GROUP_ID, USER_ID, MemberStatus.APPROVED))
                    .thenReturn(false);

            assertThatThrownBy(() -> groupService.getGroupBySlug("test-group", USER_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("This group is private");
        }
    }

    // ==================== SEARCH GROUPS ====================

    @Nested
    @DisplayName("searchGroups")
    class SearchGroupsTests {

        @Test
        @DisplayName("Success - finds by name")
        void searchGroups_FindsByName() {
            when(groupRepository.findByGroupTypeOrderByMemberCountDesc(GroupType.PUBLIC))
                    .thenReturn(List.of(testGroup));

            PageResponse<GroupSummaryResponse> response = groupService.searchGroups("Test", 0, 20);

            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getContent().get(0).getName()).isEqualTo("Test Group");
        }

        @Test
        @DisplayName("Success - finds by description")
        void searchGroups_FindsByDescription() {
            testGroup.setDescription("Technology and innovation");
            when(groupRepository.findByGroupTypeOrderByMemberCountDesc(GroupType.PUBLIC))
                    .thenReturn(List.of(testGroup));

            PageResponse<GroupSummaryResponse> response = groupService.searchGroups("innovation", 0, 20);

            assertThat(response.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Success - case insensitive")
        void searchGroups_CaseInsensitive() {
            when(groupRepository.findByGroupTypeOrderByMemberCountDesc(GroupType.PUBLIC))
                    .thenReturn(List.of(testGroup));

            PageResponse<GroupSummaryResponse> response = groupService.searchGroups("TEST GROUP", 0, 20);

            assertThat(response.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Success - no matches")
        void searchGroups_NoMatches() {
            when(groupRepository.findByGroupTypeOrderByMemberCountDesc(GroupType.PUBLIC))
                    .thenReturn(List.of(testGroup));

            PageResponse<GroupSummaryResponse> response = groupService.searchGroups("xyz", 0, 20);

            assertThat(response.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Success - name matches (description check short-circuited)")
        void searchGroups_NameMatchesShortCircuit() {
            testGroup.setName("Technology");
            testGroup.setDescription("Something else");
            when(groupRepository.findByGroupTypeOrderByMemberCountDesc(GroupType.PUBLIC))
                    .thenReturn(List.of(testGroup));

            PageResponse<GroupSummaryResponse> response = groupService.searchGroups("Tech", 0, 20);

            assertThat(response.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Success - name doesn't match but description matches")
        void searchGroups_NameNoMatchDescriptionMatches() {
            testGroup.setName("Sports");
            testGroup.setDescription("Technology and innovation");
            when(groupRepository.findByGroupTypeOrderByMemberCountDesc(GroupType.PUBLIC))
                    .thenReturn(List.of(testGroup));

            PageResponse<GroupSummaryResponse> response = groupService.searchGroups("Tech", 0, 20);

            assertThat(response.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Filtered out - name is null and description is null")
        void searchGroups_BothNameAndDescriptionNull() {
            testGroup.setName(null);
            testGroup.setDescription(null);
            when(groupRepository.findByGroupTypeOrderByMemberCountDesc(GroupType.PUBLIC))
                    .thenReturn(List.of(testGroup));

            PageResponse<GroupSummaryResponse> response = groupService.searchGroups("Tech", 0, 20);

            assertThat(response.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Filtered out - name is null and description doesn't match")
        void searchGroups_NameNullDescriptionNoMatch() {
            testGroup.setName(null);
            testGroup.setDescription("Sports and fitness");
            when(groupRepository.findByGroupTypeOrderByMemberCountDesc(GroupType.PUBLIC))
                    .thenReturn(List.of(testGroup));

            PageResponse<GroupSummaryResponse> response = groupService.searchGroups("Tech", 0, 20);

            assertThat(response.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Filtered out - name doesn't match and description is null")
        void searchGroups_NameNoMatchDescriptionNull() {
            testGroup.setName("Sports");
            testGroup.setDescription(null);
            when(groupRepository.findByGroupTypeOrderByMemberCountDesc(GroupType.PUBLIC))
                    .thenReturn(List.of(testGroup));

            PageResponse<GroupSummaryResponse> response = groupService.searchGroups("Tech", 0, 20);

            assertThat(response.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Success - with pagination page 0")
        void searchGroups_PaginationPage0() {
            Group group1 = createGroup("Group A", "group-a");
            Group group2 = createGroup("Group B", "group-b");
            Group group3 = createGroup("Group C", "group-c");

            when(groupRepository.findByGroupTypeOrderByMemberCountDesc(GroupType.PUBLIC))
                    .thenReturn(List.of(group1, group2, group3));

            PageResponse<GroupSummaryResponse> response = groupService.searchGroups("Group", 0, 2);

            assertThat(response.getContent()).hasSize(2);
            assertThat(response.getPage()).isEqualTo(0);
            assertThat(response.getTotalElements()).isEqualTo(3);
            assertThat(response.getTotalPages()).isEqualTo(2);
            assertThat(response.isFirst()).isTrue();
            assertThat(response.isLast()).isFalse();
        }

        @Test
        @DisplayName("Success - with pagination page 1")
        void searchGroups_PaginationPage1() {
            Group group1 = createGroup("Group A", "group-a");
            Group group2 = createGroup("Group B", "group-b");
            Group group3 = createGroup("Group C", "group-c");

            when(groupRepository.findByGroupTypeOrderByMemberCountDesc(GroupType.PUBLIC))
                    .thenReturn(List.of(group1, group2, group3));

            PageResponse<GroupSummaryResponse> response = groupService.searchGroups("Group", 1, 2);

            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getPage()).isEqualTo(1);
            assertThat(response.isFirst()).isFalse();
            assertThat(response.isLast()).isTrue();
        }

        @Test
        @DisplayName("Success - page beyond results (returns empty)")
        void searchGroups_PageBeyondResults() {
            when(groupRepository.findByGroupTypeOrderByMemberCountDesc(GroupType.PUBLIC))
                    .thenReturn(List.of(testGroup));

            PageResponse<GroupSummaryResponse> response = groupService.searchGroups("Test", 10, 20);

            assertThat(response.getContent()).isEmpty();
            assertThat(response.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("Sanitized query is empty - returns empty response")
        void searchGroups_EmptySanitizedQuery() {
            PageResponse<GroupSummaryResponse> response = groupService.searchGroups("", 0, 20);

            assertThat(response.getContent()).isEmpty();
            assertThat(response.isEmpty()).isTrue();
            verifyNoInteractions(groupRepository);
        }
    }

    // ==================== GET POPULAR GROUPS ====================

    @Nested
    @DisplayName("getPopularGroups")
    class GetPopularGroupsTests {

        @Test
        @DisplayName("Success - returns popular groups")
        void getPopularGroups_Success() {
            Page<Group> page = new PageImpl<>(List.of(testGroup), PageRequest.of(0, 20), 1);
            when(groupRepository.findPopularGroups(any(Pageable.class))).thenReturn(page);

            PageResponse<GroupSummaryResponse> response = groupService.getPopularGroups(0, 20);

            assertThat(response.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Success - empty list")
        void getPopularGroups_Empty() {
            Page<Group> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
            when(groupRepository.findPopularGroups(any(Pageable.class))).thenReturn(emptyPage);

            PageResponse<GroupSummaryResponse> response = groupService.getPopularGroups(0, 20);

            assertThat(response.getContent()).isEmpty();
        }
    }

    // ==================== GET FEATURED GROUPS ====================

    @Nested
    @DisplayName("getFeaturedGroups")
    class GetFeaturedGroupsTests {

        @Test
        @DisplayName("Success - returns featured groups")
        void getFeaturedGroups_Success() {
            Page<Group> page = new PageImpl<>(List.of(testGroup), PageRequest.of(0, 20), 1);
            when(groupRepository.findFeaturedGroups(any(Pageable.class))).thenReturn(page);

            PageResponse<GroupSummaryResponse> response = groupService.getFeaturedGroups(0, 20);

            assertThat(response.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Success - empty list")
        void getFeaturedGroups_Empty() {
            Page<Group> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
            when(groupRepository.findFeaturedGroups(any(Pageable.class))).thenReturn(emptyPage);

            PageResponse<GroupSummaryResponse> response = groupService.getFeaturedGroups(0, 20);

            assertThat(response.getContent()).isEmpty();
        }
    }

    // ==================== GET GROUPS BY CATEGORY ====================

    @Nested
    @DisplayName("getGroupsByCategory")
    class GetGroupsByCategoryTests {

        @Test
        @DisplayName("Success - returns groups")
        void getGroupsByCategory_Success() {
            Page<Group> page = new PageImpl<>(List.of(testGroup), PageRequest.of(0, 20), 1);
            when(groupRepository.findByCategoryIdOrderByPopularity(eq(CATEGORY_ID), any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<GroupSummaryResponse> response = groupService.getGroupsByCategory(CATEGORY_ID, 0, 20);

            assertThat(response.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Success - empty list")
        void getGroupsByCategory_Empty() {
            Page<Group> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
            when(groupRepository.findByCategoryIdOrderByPopularity(eq(CATEGORY_ID), any(Pageable.class)))
                    .thenReturn(emptyPage);

            PageResponse<GroupSummaryResponse> response = groupService.getGroupsByCategory(CATEGORY_ID, 0, 20);

            assertThat(response.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Category ID is null - throws ValidationException")
        void getGroupsByCategory_NullCategoryId() {
            assertThatThrownBy(() -> groupService.getGroupsByCategory(null, 0, 20))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Category ID is required");
        }
    }

    // ==================== GET USER GROUPS ====================

    @Nested
    @DisplayName("getUserGroups")
    class GetUserGroupsTests {

        @Test
        @DisplayName("Success - returns user memberships")
        void getUserGroups_Success() {
            Page<GroupMember> page = new PageImpl<>(List.of(testMember), PageRequest.of(0, 20), 1);
            when(memberRepository.findMembershipsByUser(eq(USER_ID), any(Pageable.class))).thenReturn(page);

            PageResponse<MembershipResponse> response = groupService.getUserGroups(USER_ID, 0, 20);

            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getContent().get(0).getGroupId()).isEqualTo(GROUP_ID);
        }

        @Test
        @DisplayName("Success - empty list")
        void getUserGroups_Empty() {
            Page<GroupMember> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
            when(memberRepository.findMembershipsByUser(eq(USER_ID), any(Pageable.class))).thenReturn(emptyPage);

            PageResponse<MembershipResponse> response = groupService.getUserGroups(USER_ID, 0, 20);

            assertThat(response.getContent()).isEmpty();
        }
    }

    // ==================== GENERATE SLUG (Private Method) ====================

    @Nested
    @DisplayName("generateSlug (private method)")
    class GenerateSlugTests {

        @Test
        @DisplayName("Empty after processing - generates fallback slug")
        void generateSlug_EmptyAfterProcessing() {
            when(groupRepository.existsBySlug(argThat(s -> s != null && s.startsWith("group-"))))
                    .thenReturn(false);

            String slug = ReflectionTestUtils.invokeMethod(groupService, "generateSlug", "@#$%^&*()");

            assertThat(slug).startsWith("group-");
            assertThat(slug).matches("group-\\d+");
        }
    }

    // ==================== MAP TO GROUP RESPONSE (via getGroup) ====================

    @Nested
    @DisplayName("mapToGroupResponse")
    class MapToGroupResponseTests {

        @Test
        @DisplayName("With categoryId - loads category name")
        void mapToGroupResponse_WithCategory() {
            testGroup.setCategoryId(CATEGORY_ID);
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(testCategory));

            GroupResponse response = groupService.getGroup(GROUP_ID, null);

            assertThat(response.getCategoryName()).isEqualTo("Technology");
        }

        @Test
        @DisplayName("Without categoryId - skips category lookup")
        void mapToGroupResponse_WithoutCategory() {
            testGroup.setCategoryId(null);
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));

            GroupResponse response = groupService.getGroup(GROUP_ID, null);

            assertThat(response.getCategoryName()).isNull();
            verify(categoryRepository, never()).findById(any());
        }

        @Test
        @DisplayName("With currentUserId - loads member info")
        void mapToGroupResponse_WithCurrentUser() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(testMember));

            GroupResponse response = groupService.getGroup(GROUP_ID, USER_ID);

            assertThat(response.getIsMember()).isTrue();
            assertThat(response.getIsAdmin()).isTrue();
            assertThat(response.getUserRole()).isEqualTo(MemberRole.OWNER);
        }

        @Test
        @DisplayName("With currentUserId but not member - member fields null")
        void mapToGroupResponse_CurrentUserNotMember() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.empty());

            GroupResponse response = groupService.getGroup(GROUP_ID, USER_ID);

            assertThat(response.getIsMember()).isNull();
            assertThat(response.getIsAdmin()).isNull();
        }

        @Test
        @DisplayName("Without currentUserId - skips member lookup")
        void mapToGroupResponse_WithoutCurrentUser() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));

            GroupResponse response = groupService.getGroup(GROUP_ID, null);

            assertThat(response.getIsMember()).isNull();
            verify(memberRepository, never()).findByGroupIdAndUserId(any(), any());
        }

        @Test
        @DisplayName("Member is pending - sets isPending true")
        void mapToGroupResponse_MemberPending() {
            GroupMember pendingMember = GroupMember.builder()
                    .group(testGroup)
                    .userId(USER_ID)
                    .role(MemberRole.MEMBER)
                    .status(MemberStatus.PENDING)
                    .build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
            when(memberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(pendingMember));

            GroupResponse response = groupService.getGroup(GROUP_ID, USER_ID);

            assertThat(response.getIsPending()).isTrue();
            assertThat(response.getIsMember()).isFalse();
        }
    }

    // ==================== HELPER METHODS ====================

    private Group createGroup(String name, String slug) {
        Group group = Group.builder()
                .id(UUID.randomUUID())
                .name(name)
                .slug(slug)
                .groupType(GroupType.PUBLIC)
                .ownerId(USER_ID)
                .memberCount(10)
                .postCount(5)
                .isVerified(false)
                .isFeatured(false)
                .allowMemberPosts(true)
                .requirePostApproval(false)
                .requireJoinApproval(false)
                .allowMemberInvites(true)
                .build();
        group.setCreatedAt(LocalDateTime.now());
        return group;
    }
}