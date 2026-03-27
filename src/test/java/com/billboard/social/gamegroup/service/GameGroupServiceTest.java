package com.billboard.social.gamegroup.service;

import com.billboard.social.common.client.EsportsBackendClient;
import com.billboard.social.common.dto.*;
import com.billboard.social.common.exception.ForbiddenException;
import com.billboard.social.common.exception.ResourceNotFoundException;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.gamegroup.dto.request.GameGroupRequests.*;
import com.billboard.social.gamegroup.dto.response.GameGroupResponses.*;
import com.billboard.social.gamegroup.entity.GameGroupProfile;
import com.billboard.social.gamegroup.entity.GroupScrimFilter;
import com.billboard.social.gamegroup.entity.GroupTeamLink;
import com.billboard.social.gamegroup.event.GameGroupEventPublisher;
import com.billboard.social.gamegroup.repository.GameGroupProfileRepository;
import com.billboard.social.gamegroup.repository.GroupScrimFilterRepository;
import com.billboard.social.gamegroup.repository.GroupTeamLinkRepository;
import com.billboard.social.group.dto.request.GroupRequests.JoinGroupRequest;
import com.billboard.social.group.dto.response.GroupResponses;
import com.billboard.social.group.dto.response.GroupResponses.GroupMemberResponse;
import com.billboard.social.group.entity.Group;
import com.billboard.social.group.entity.GroupMember;
import com.billboard.social.group.entity.enums.MemberRole;
import com.billboard.social.group.entity.enums.MemberStatus;
import com.billboard.social.group.repository.GroupMemberRepository;
import com.billboard.social.group.repository.GroupRepository;
import com.billboard.social.group.service.GroupMemberService;
import com.billboard.social.group.service.GroupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GameGroupService")
class GameGroupServiceTest {

    // ─────────────────────────────────────────────────────────────
    // Mocks
    // ─────────────────────────────────────────────────────────────

    @Mock private GroupService                  groupService;
    @Mock private GroupRepository               groupRepository;
    @Mock private GroupMemberRepository         groupMemberRepository;
    @Mock private GroupMemberService            groupMemberService;
    @Mock private GameGroupProfileRepository    gameGroupProfileRepository;
    @Mock private GroupScrimFilterRepository    groupScrimFilterRepository;
    @Mock private GroupTeamLinkRepository       groupTeamLinkRepository;
    @Mock private GameGroupEventPublisher       eventPublisher;
    @Mock private GameAccountLinkService gameAccountLinkService;
    @Mock private AuditService auditService;
    @Mock private EsportsBackendClient          esportsBackendClient;

    @InjectMocks
    private GameGroupService service;

    // ─────────────────────────────────────────────────────────────
    // Shared constants
    // ─────────────────────────────────────────────────────────────

    private static final UUID   GROUP_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Long   USER_ID   = 42L;
    private static final Long   TEAM_ID   = 7L;
    private static final Long   PLAYER_ID = 99L;

    // ─────────────────────────────────────────────────────────────
    // Fixture builders
    // ─────────────────────────────────────────────────────────────

    private Group group() {
        return Group.builder()
                .id(GROUP_ID)
                .name("Test Group")
                .slug("test-group")
                .ownerId(USER_ID)
                .memberCount(3)
                .isVerified(false)
                .build();
    }

    private GameGroupProfile profile() {
        return GameGroupProfile.builder()
                .id(UUID.randomUUID())
                .group(group())
                .gameTag("valorant")
                .region("NA")
                .requireGameAccount(false)
                .build();
    }

    private GroupMember adminMember() {
        return GroupMember.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .role(MemberRole.OWNER)
                .status(MemberStatus.APPROVED)
                .build();
    }

    private GroupMember plainMember() {
        return GroupMember.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .role(MemberRole.MEMBER)
                .status(MemberStatus.APPROVED)
                .build();
    }

    private GroupScrimFilter activeFilter() {
        GroupScrimFilter f = new GroupScrimFilter();
        f.setId(UUID.randomUUID());
        f.setGroup(group());
        f.setGameTag("valorant");
        f.setIsActive(true);
        f.setLastBroadcastAt(null);
        return f;
    }

    // ──────────────────────────────────────────────────────────────
    // checkAdminAccess (tested indirectly through every guarded method)
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("checkAdminAccess — indirect coverage")
    class CheckAdminAccess {

        @Test
        @DisplayName("throws ForbiddenException when user is not a member of the group")
        void notMember_throwsForbidden() {
            when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateGameGroupProfile(
                    USER_ID, GROUP_ID, UpdateGameGroupProfileRequest.builder().build()))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("not a member");
        }

        @Test
        @DisplayName("throws ForbiddenException when user is a plain MEMBER (not admin/owner)")
        void plainMember_throwsForbidden() {
            when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID))
                    .thenReturn(Optional.of(plainMember()));

            assertThatThrownBy(() -> service.updateGameGroupProfile(
                    USER_ID, GROUP_ID, UpdateGameGroupProfileRequest.builder().build()))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Admin access required");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // createGameGroup
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("createGameGroup")
    class CreateGameGroup {

        private CreateGameGroupRequest validRequest() {
            return CreateGameGroupRequest.builder()
                    .name("My Group")
                    .gameTag("valorant")
                    .region("NA")
                    .requireGameAccount(false)
                    .build();
        }

        @BeforeEach
        void stubGroupService() {
            GroupResponses.GroupResponse baseGroup = GroupResponses.GroupResponse.builder()
                    .id(GROUP_ID).name("My Group").build();
            when(groupService.createGroup(eq(USER_ID), any()))
                    .thenReturn(baseGroup);
            when(groupRepository.findById(GROUP_ID))
                    .thenReturn(Optional.of(group()));
            when(gameGroupProfileRepository.save(any()))
                    .thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("saves profile, publishes event, logs audit, returns response")
        void happyPath() {
            GameGroupResponse resp = service.createGameGroup(USER_ID, validRequest());

            assertThat(resp).isNotNull();
            assertThat(resp.getGameTag()).isEqualTo("valorant");

            verify(gameGroupProfileRepository).save(any(GameGroupProfile.class));
            verify(eventPublisher).publishGroupChatRequested(eq(GROUP_ID), anyList());
            verify(auditService).log(eq(GROUP_ID), eq(USER_ID),
                    eq("GROUP_CREATED"), eq("GROUP"), anyString(), anyString());
        }

        @Test
        @DisplayName("requireGameAccount defaults to false when request has null")
        void nullRequireGameAccount_defaultsFalse() {
            CreateGameGroupRequest req = CreateGameGroupRequest.builder()
                    .name("G").gameTag("cs2").requireGameAccount(null).build();

            service.createGameGroup(USER_ID, req);

            verify(gameGroupProfileRepository).save(argThat(
                    (GameGroupProfile p) -> Boolean.FALSE.equals(p.getRequireGameAccount())));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // updateGameGroupProfile
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("updateGameGroupProfile")
    class UpdateGameGroupProfile {

        @BeforeEach
        void stubAdmin() {
            when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID))
                    .thenReturn(Optional.of(adminMember()));
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when group not found")
        void groupNotFound_throws() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateGameGroupProfile(
                    USER_ID, GROUP_ID, UpdateGameGroupProfileRequest.builder().build()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when profile not found")
        void profileNotFound_throws() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group()));
            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateGameGroupProfile(
                    USER_ID, GROUP_ID, UpdateGameGroupProfileRequest.builder().build()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("updates only non-null fields — null fields leave profile unchanged")
        void nullFields_notOverwritten() {
            GameGroupProfile existing = profile();
            existing.setRegion("EU");
            existing.setPlatform("PC");

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group()));
            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(existing));
            when(gameGroupProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // only region is set — platform should remain "PC"
            UpdateGameGroupProfileRequest req = UpdateGameGroupProfileRequest.builder()
                    .region("NA").build();

            service.updateGameGroupProfile(USER_ID, GROUP_ID, req);

            verify(gameGroupProfileRepository).save(argThat((GameGroupProfile p) ->
                    "NA".equals(p.getRegion()) && "PC".equals(p.getPlatform())));
        }

        @Test
        @DisplayName("updates all fields when all are non-null")
        void allFields_updated() {
            GameGroupProfile existing = profile();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group()));
            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(existing));
            when(gameGroupProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateGameGroupProfileRequest req = UpdateGameGroupProfileRequest.builder()
                    .region("EU").platform("Console").minRank("500")
                    .maxRank("2000").requireGameAccount(true)
                    .discordServerId("srv1").discordChannelId("ch1").build();

            service.updateGameGroupProfile(USER_ID, GROUP_ID, req);

            verify(gameGroupProfileRepository).save(argThat((GameGroupProfile p) ->
                    "EU".equals(p.getRegion())
                            && "Console".equals(p.getPlatform())
                            && "500".equals(p.getMinRank())
                            && "2000".equals(p.getMaxRank())
                            && Boolean.TRUE.equals(p.getRequireGameAccount())
                            && "srv1".equals(p.getDiscordServerId())
                            && "ch1".equals(p.getDiscordChannelId())));

            verify(auditService).log(eq(GROUP_ID), eq(USER_ID),
                    eq("PROFILE_UPDATED"), any(), any(), any());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // getGameGroup
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getGameGroup")
    class GetGameGroup {

        @Test
        @DisplayName("throws ResourceNotFoundException when group not found")
        void groupNotFound_throws() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getGameGroup(GROUP_ID, USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when profile not found")
        void profileNotFound_throws() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group()));
            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getGameGroup(GROUP_ID, USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("returns response with isMember/isAdmin populated when currentUserId is non-null")
        void withCurrentUserId_memberContextPopulated() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group()));
            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(profile()));
            when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID))
                    .thenReturn(Optional.of(adminMember()));

            GameGroupResponse resp = service.getGameGroup(GROUP_ID, USER_ID);

            assertThat(resp.getIsAdmin()).isTrue();
            assertThat(resp.getIsMember()).isTrue();
        }

        @Test
        @DisplayName("skips member context lookup when currentUserId is null")
        void withNullCurrentUserId_noMemberLookup() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group()));
            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(profile()));

            GameGroupResponse resp = service.getGameGroup(GROUP_ID, null);

            assertThat(resp).isNotNull();
            verify(groupMemberRepository, never()).findByGroupIdAndUserId(any(), any());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // searchGameGroups
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("searchGameGroups")
    class SearchGameGroups {

        @Test
        @DisplayName("uses findByGameTag when region is blank")
        void blankRegion_usesGameTagOnly() {
            Page<GameGroupProfile> page = new PageImpl<>(List.of());
            when(gameGroupProfileRepository.findByGameTag(eq("valorant"), any(Pageable.class)))
                    .thenReturn(page);

            service.searchGameGroups("valorant", "", 0, 10);

            verify(gameGroupProfileRepository).findByGameTag(eq("valorant"), any());
            verify(gameGroupProfileRepository, never()).findByGameTagAndRegion(any(), any(), any());
        }

        @Test
        @DisplayName("uses findByGameTagAndRegion when region is non-blank")
        void nonBlankRegion_usesGameTagAndRegion() {
            Page<GameGroupProfile> page = new PageImpl<>(List.of());
            when(gameGroupProfileRepository.findByGameTagAndRegion(
                    eq("valorant"), eq("NA"), any(Pageable.class))).thenReturn(page);

            service.searchGameGroups("valorant", "NA", 0, 10);

            verify(gameGroupProfileRepository).findByGameTagAndRegion(eq("valorant"), eq("NA"), any());
            verify(gameGroupProfileRepository, never()).findByGameTag(any(), any());
        }

        @Test
        @DisplayName("uses findByGameTag when region is null")
        void nullRegion_usesGameTagOnly() {
            Page<GameGroupProfile> page = new PageImpl<>(List.of());
            when(gameGroupProfileRepository.findByGameTag(eq("cs2"), any(Pageable.class)))
                    .thenReturn(page);

            service.searchGameGroups("cs2", null, 0, 5);

            verify(gameGroupProfileRepository).findByGameTag(eq("cs2"), any());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // getGroupEmbed
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getGroupEmbed")
    class GetGroupEmbed {

        @Test
        @DisplayName("throws ResourceNotFoundException when group not found")
        void groupNotFound_throws() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getGroupEmbed(GROUP_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("returns embed with null gameTag/region when profile absent")
        void profileAbsent_nullGameTagAndRegion() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group()));
            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.empty());

            GameGroupEmbedResponse resp = service.getGroupEmbed(GROUP_ID);

            assertThat(resp.getGameTag()).isNull();
            assertThat(resp.getRegion()).isNull();
            assertThat(resp.getId()).isEqualTo(GROUP_ID);
        }

        @Test
        @DisplayName("returns embed with gameTag and region from profile when present")
        void profilePresent_gameTagAndRegionPopulated() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group()));
            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(profile()));

            GameGroupEmbedResponse resp = service.getGroupEmbed(GROUP_ID);

            assertThat(resp.getGameTag()).isEqualTo("valorant");
            assertThat(resp.getRegion()).isEqualTo("NA");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // saveScrimFilter
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("saveScrimFilter")
    class SaveScrimFilter {

        private ScrimFilterRequest req() {
            return ScrimFilterRequest.builder()
                    .gameTag("valorant").region("NA").format("5v5")
                    .minTeamSize(5).maxTeamSize(5).isActive(true).build();
        }

        @BeforeEach
        void stubAdmin() {
            when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID))
                    .thenReturn(Optional.of(adminMember()));
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when group not found")
        void groupNotFound_throws() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.saveScrimFilter(USER_ID, GROUP_ID, req()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("creates new filter when none exists for the group")
        void noExistingFilter_createsNew() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group()));
            when(groupScrimFilterRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.empty());
            when(groupScrimFilterRepository.save(any())).thenAnswer(inv -> {
                GroupScrimFilter f = inv.getArgument(0);
                f.setId(UUID.randomUUID());
                return f;
            });

            ScrimFilterResponse resp = service.saveScrimFilter(USER_ID, GROUP_ID, req());

            assertThat(resp.getGameTag()).isEqualTo("valorant");
            verify(groupScrimFilterRepository).save(any());
            verify(auditService).log(eq(GROUP_ID), eq(USER_ID), eq("SCRIM_FILTER_SAVED"), any(), any(), any());
        }

        @Test
        @DisplayName("updates existing filter when one already exists")
        void existingFilter_updated() {
            GroupScrimFilter existing = activeFilter();
            existing.setGameTag("cs2");

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group()));
            when(groupScrimFilterRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(existing));
            when(groupScrimFilterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.saveScrimFilter(USER_ID, GROUP_ID, req());

            assertThat(existing.getGameTag()).isEqualTo("valorant");
        }

        @Test
        @DisplayName("skips setting isActive when request isActive is null")
        void nullIsActive_notOverwritten() {
            GroupScrimFilter existing = activeFilter();
            existing.setIsActive(true);

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group()));
            when(groupScrimFilterRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(existing));
            when(groupScrimFilterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ScrimFilterRequest nullIsActiveReq = ScrimFilterRequest.builder()
                    .gameTag("valorant").isActive(null).build();

            service.saveScrimFilter(USER_ID, GROUP_ID, nullIsActiveReq);

            assertThat(existing.getIsActive()).isTrue(); // unchanged
        }
    }

    // ══════════════════════════════════════════════════════════════
    // getScrimFilter
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getScrimFilter")
    class GetScrimFilter {

        @Test
        @DisplayName("throws ResourceNotFoundException when filter not found")
        void notFound_throws() {
            when(groupScrimFilterRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getScrimFilter(GROUP_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("returns mapped filter response")
        void found_returnsResponse() {
            when(groupScrimFilterRepository.findByGroupId(GROUP_ID))
                    .thenReturn(Optional.of(activeFilter()));

            ScrimFilterResponse resp = service.getScrimFilter(GROUP_ID);

            assertThat(resp.getGameTag()).isEqualTo("valorant");
            assertThat(resp.getIsActive()).isTrue();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // getMemberIds
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getMemberIds")
    class GetMemberIds {

        @Test
        @DisplayName("returns list from repository")
        void returnsRepoResult() {
            when(groupMemberRepository.findMemberUserIds(GROUP_ID))
                    .thenReturn(List.of(1L, 2L, 3L));

            List<Long> ids = service.getMemberIds(GROUP_ID);

            assertThat(ids).containsExactly(1L, 2L, 3L);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // broadcastLfs
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("broadcastLfs")
    class BroadcastLfs {

        private LfsBroadcastRequest req() {
            return LfsBroadcastRequest.builder().groupId(GROUP_ID).message("gg").build();
        }

        @BeforeEach
        void stubAdmin() {
            when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID))
                    .thenReturn(Optional.of(adminMember()));
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when scrim filter not found")
        void filterNotFound_throws() {
            when(groupScrimFilterRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.broadcastLfs(USER_ID, GROUP_ID, req()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws ValidationException when filter is inactive")
        void filterInactive_throws() {
            GroupScrimFilter inactive = activeFilter();
            inactive.setIsActive(false);
            when(groupScrimFilterRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(inactive));

            assertThatThrownBy(() -> service.broadcastLfs(USER_ID, GROUP_ID, req()))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("must be active");
        }

        @Test
        @DisplayName("throws ValidationException when cooldown is still active (within 5 minutes)")
        void cooldownActive_throws() {
            GroupScrimFilter filter = activeFilter();
            filter.setLastBroadcastAt(LocalDateTime.now().minusMinutes(3)); // 3 min ago
            when(groupScrimFilterRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(filter));

            assertThatThrownBy(() -> service.broadcastLfs(USER_ID, GROUP_ID, req()))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("cooldown");
        }

        @Test
        @DisplayName("proceeds when lastBroadcastAt is null (never broadcast before)")
        void nullLastBroadcast_proceeds() {
            GroupScrimFilter filter = activeFilter();
            filter.setLastBroadcastAt(null);

            when(groupScrimFilterRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(filter));
            when(groupMemberRepository.findMemberUserIds(GROUP_ID)).thenReturn(List.of(USER_ID));
            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(profile()));
            when(groupScrimFilterRepository.save(any())).thenReturn(filter);

            service.broadcastLfs(USER_ID, GROUP_ID, req());

            verify(eventPublisher).publishLfsBroadcast(any(), any(), any(), any(), any());
            verify(auditService).log(eq(GROUP_ID), eq(USER_ID), eq("LFS_BROADCAST"), any(), any(), any());
        }

        @Test
        @DisplayName("proceeds when lastBroadcastAt is older than 5 minutes (cooldown expired)")
        void cooldownExpired_proceeds() {
            GroupScrimFilter filter = activeFilter();
            filter.setLastBroadcastAt(LocalDateTime.now().minusMinutes(6));

            when(groupScrimFilterRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(filter));
            when(groupMemberRepository.findMemberUserIds(GROUP_ID)).thenReturn(List.of(USER_ID));
            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(profile()));
            when(groupScrimFilterRepository.save(any())).thenReturn(filter);

            service.broadcastLfs(USER_ID, GROUP_ID, req());

            verify(eventPublisher).publishLfsBroadcast(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when game group profile not found")
        void profileNotFound_throws() {
            GroupScrimFilter filter = activeFilter();
            when(groupScrimFilterRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(filter));
            when(groupMemberRepository.findMemberUserIds(GROUP_ID)).thenReturn(List.of());
            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.broadcastLfs(USER_ID, GROUP_ID, req()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // cancelLfs
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("cancelLfs")
    class CancelLfs {

        @BeforeEach
        void stubAdmin() {
            when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID))
                    .thenReturn(Optional.of(adminMember()));
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when profile not found")
        void profileNotFound_throws() {
            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancelLfs(USER_ID, GROUP_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when filter not found")
        void filterNotFound_throws() {
            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(profile()));
            when(groupScrimFilterRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancelLfs(USER_ID, GROUP_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("sets filter inactive, publishes cancelled event, logs audit")
        void happyPath() {
            GroupScrimFilter filter = activeFilter();
            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(profile()));
            when(groupScrimFilterRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(filter));
            when(groupScrimFilterRepository.save(any())).thenReturn(filter);

            service.cancelLfs(USER_ID, GROUP_ID);

            assertThat(filter.getIsActive()).isFalse();
            verify(eventPublisher).publishLfsCancelled(GROUP_ID, "valorant");
            verify(auditService).log(eq(GROUP_ID), eq(USER_ID), eq("LFS_CANCELLED"), any(), any(), any());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // handleMatchFound
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("handleMatchFound")
    class HandleMatchFound {

        private final UUID MATCHED_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

        private LfsMatchFoundRequest req() {
            return LfsMatchFoundRequest.builder()
                    .groupId(GROUP_ID).matchedGroupId(MATCHED_ID).gameTag("valorant").build();
        }

        @BeforeEach
        void stubAdmin() {
            when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID))
                    .thenReturn(Optional.of(adminMember()));
        }

        @Test
        @DisplayName("publishes matched event, logs audit, returns response")
        void happyPath() {
            LfsMatchFoundResponse resp = service.handleMatchFound(USER_ID, GROUP_ID, req());

            assertThat(resp.getGroupId()).isEqualTo(GROUP_ID);
            assertThat(resp.getMatchedGroupId()).isEqualTo(MATCHED_ID);
            assertThat(resp.getGameTag()).isEqualTo("valorant");
            assertThat(resp.getMatchedAt()).isNotNull();

            verify(eventPublisher).publishLfsMatched(GROUP_ID, MATCHED_ID, "valorant");
            verify(auditService).log(eq(GROUP_ID), eq(USER_ID), eq("LFS_MATCHED"), any(), any(), any());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // linkTeam
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("linkTeam")
    class LinkTeam {

        private LinkTeamRequest req() {
            return LinkTeamRequest.builder().teamId(TEAM_ID).build();
        }

        @BeforeEach
        void stubAdmin() {
            when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID))
                    .thenReturn(Optional.of(adminMember()));
        }

        @Test
        @DisplayName("throws ValidationException when team is already linked")
        void alreadyLinked_throws() {
            when(groupTeamLinkRepository.existsByGroupIdAndTeamId(GROUP_ID, TEAM_ID)).thenReturn(true);

            assertThatThrownBy(() -> service.linkTeam(USER_ID, GROUP_ID, req()))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("already linked");
        }

        @Test
        @DisplayName("throws ForbiddenException when esports client returns null player")
        void nullPlayerDto_throwsForbidden() {
            when(groupTeamLinkRepository.existsByGroupIdAndTeamId(GROUP_ID, TEAM_ID)).thenReturn(false);
            when(esportsBackendClient.getPlayerByUserId(anyString())).thenReturn(null);

            assertThatThrownBy(() -> service.linkTeam(USER_ID, GROUP_ID, req()))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("must be a member");
        }

        @Test
        @DisplayName("throws ForbiddenException when player id is null")
        void playerIdNull_throwsForbidden() {
            when(groupTeamLinkRepository.existsByGroupIdAndTeamId(GROUP_ID, TEAM_ID)).thenReturn(false);
            when(esportsBackendClient.getPlayerByUserId(anyString()))
                    .thenReturn(PlayerDto.builder().id(null).build());

            assertThatThrownBy(() -> service.linkTeam(USER_ID, GROUP_ID, req()))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("throws ForbiddenException when team members response is null")
        void nullTeamMembers_throwsForbidden() {
            when(groupTeamLinkRepository.existsByGroupIdAndTeamId(GROUP_ID, TEAM_ID)).thenReturn(false);
            when(esportsBackendClient.getPlayerByUserId(anyString()))
                    .thenReturn(PlayerDto.builder().id(PLAYER_ID).build());
            when(esportsBackendClient.getTeamMembers(TEAM_ID)).thenReturn(null);

            assertThatThrownBy(() -> service.linkTeam(USER_ID, GROUP_ID, req()))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("throws ForbiddenException when members list is null")
        void nullMembersList_throwsForbidden() {
            when(groupTeamLinkRepository.existsByGroupIdAndTeamId(GROUP_ID, TEAM_ID)).thenReturn(false);
            when(esportsBackendClient.getPlayerByUserId(anyString()))
                    .thenReturn(PlayerDto.builder().id(PLAYER_ID).build());
            when(esportsBackendClient.getTeamMembers(TEAM_ID))
                    .thenReturn(TeamMembersResponseDto.builder().members(null).build());

            assertThatThrownBy(() -> service.linkTeam(USER_ID, GROUP_ID, req()))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("throws ForbiddenException when player is in team but status is not ACTIVE")
        void memberNotActive_throwsForbidden() {
            when(groupTeamLinkRepository.existsByGroupIdAndTeamId(GROUP_ID, TEAM_ID)).thenReturn(false);
            when(esportsBackendClient.getPlayerByUserId(anyString()))
                    .thenReturn(PlayerDto.builder().id(PLAYER_ID).build());
            when(esportsBackendClient.getTeamMembers(TEAM_ID))
                    .thenReturn(TeamMembersResponseDto.builder()
                            .members(List.of(TeamMemberEntryDto.builder()
                                    .playerId(PLAYER_ID).status("INACTIVE").build()))
                            .build());

            assertThatThrownBy(() -> service.linkTeam(USER_ID, GROUP_ID, req()))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("throws ForbiddenException when esports client throws (graceful fallback)")
        void clientThrows_treatedAsNotMember() {
            when(groupTeamLinkRepository.existsByGroupIdAndTeamId(GROUP_ID, TEAM_ID)).thenReturn(false);
            when(esportsBackendClient.getPlayerByUserId(anyString()))
                    .thenThrow(new RuntimeException("service unavailable"));

            assertThatThrownBy(() -> service.linkTeam(USER_ID, GROUP_ID, req()))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("must be a member");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when group not found after membership check")
        void groupNotFound_throws() {
            when(groupTeamLinkRepository.existsByGroupIdAndTeamId(GROUP_ID, TEAM_ID)).thenReturn(false);
            when(esportsBackendClient.getPlayerByUserId(anyString()))
                    .thenReturn(PlayerDto.builder().id(PLAYER_ID).build());
            when(esportsBackendClient.getTeamMembers(TEAM_ID))
                    .thenReturn(TeamMembersResponseDto.builder()
                            .members(List.of(TeamMemberEntryDto.builder()
                                    .playerId(PLAYER_ID).status("ACTIVE").build()))
                            .build());
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.linkTeam(USER_ID, GROUP_ID, req()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("returns response with null teamName when team summary fetch returns null")
        void nullTeamSummary_teamNameNull() {
            stubSuccessfulMembership();
            when(esportsBackendClient.getTeamSummary(TEAM_ID)).thenReturn(null);
            GroupTeamLink link = stubSavedLink();

            GroupTeamLinkResponse resp = service.linkTeam(USER_ID, GROUP_ID, req());

            assertThat(resp.getTeamName()).isNull();
        }

        @Test
        @DisplayName("returns response with teamName populated when team summary is present")
        void teamSummaryPresent_teamNameSet() {
            stubSuccessfulMembership();
            when(esportsBackendClient.getTeamSummary(TEAM_ID))
                    .thenReturn(TeamSummaryDto.builder().id(TEAM_ID).name("Alpha").build());
            stubSavedLink();

            GroupTeamLinkResponse resp = service.linkTeam(USER_ID, GROUP_ID, req());

            assertThat(resp.getTeamName()).isEqualTo("Alpha");
            verify(auditService).log(eq(GROUP_ID), eq(USER_ID), eq("TEAM_LINKED"), any(), any(), any());
        }

        // ── private helpers ────────────────────────────────────────

        private void stubSuccessfulMembership() {
            when(groupTeamLinkRepository.existsByGroupIdAndTeamId(GROUP_ID, TEAM_ID)).thenReturn(false);
            when(esportsBackendClient.getPlayerByUserId(anyString()))
                    .thenReturn(PlayerDto.builder().id(PLAYER_ID).build());
            when(esportsBackendClient.getTeamMembers(TEAM_ID))
                    .thenReturn(TeamMembersResponseDto.builder()
                            .members(List.of(TeamMemberEntryDto.builder()
                                    .playerId(PLAYER_ID).status("ACTIVE").build()))
                            .build());
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group()));
        }

        private GroupTeamLink stubSavedLink() {
            GroupTeamLink link = GroupTeamLink.builder()
                    .id(UUID.randomUUID()).group(group()).teamId(TEAM_ID).linkedBy(USER_ID)
                    .linkedAt(LocalDateTime.of(2025, 1, 1, 12, 0)).build();
            when(groupTeamLinkRepository.save(any())).thenReturn(link);
            return link;
        }
    }

    // ══════════════════════════════════════════════════════════════
    // getLinkedTeams
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getLinkedTeams")
    class GetLinkedTeams {

        @Test
        @DisplayName("returns empty list when no teams are linked")
        void empty_returnsEmpty() {
            when(groupTeamLinkRepository.findByGroupId(GROUP_ID)).thenReturn(List.of());

            assertThat(service.getLinkedTeams(GROUP_ID)).isEmpty();
        }

        @Test
        @DisplayName("returns teamName null gracefully when team summary fetch throws")
        void teamSummaryThrows_teamNameNull() {
            GroupTeamLink link = GroupTeamLink.builder()
                    .id(UUID.randomUUID()).group(group()).teamId(TEAM_ID).linkedBy(USER_ID)
                    .linkedAt(LocalDateTime.of(2025, 1, 1, 12, 0)).build();
            when(groupTeamLinkRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(link));
            when(esportsBackendClient.getTeamSummary(TEAM_ID))
                    .thenThrow(new RuntimeException("service unavailable"));

            List<GroupTeamLinkResponse> result = service.getLinkedTeams(GROUP_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTeamName()).isNull();
        }

        @Test
        @DisplayName("returns populated teamName when team summary succeeds")
        void teamSummarySucceeds_teamNamePopulated() {
            GroupTeamLink link = GroupTeamLink.builder()
                    .id(UUID.randomUUID()).group(group()).teamId(TEAM_ID).linkedBy(USER_ID)
                    .linkedAt(LocalDateTime.of(2025, 1, 1, 12, 0)).build();
            when(groupTeamLinkRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(link));
            when(esportsBackendClient.getTeamSummary(TEAM_ID))
                    .thenReturn(TeamSummaryDto.builder().name("Alpha").build());

            List<GroupTeamLinkResponse> result = service.getLinkedTeams(GROUP_ID);

            assertThat(result.get(0).getTeamName()).isEqualTo("Alpha");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // unlinkTeam
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("unlinkTeam")
    class UnlinkTeam {

        @BeforeEach
        void stubAdmin() {
            when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID))
                    .thenReturn(Optional.of(adminMember()));
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when team link not found")
        void linkNotFound_throws() {
            when(groupTeamLinkRepository.existsByGroupIdAndTeamId(GROUP_ID, TEAM_ID)).thenReturn(false);

            assertThatThrownBy(() -> service.unlinkTeam(USER_ID, GROUP_ID, TEAM_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("deletes link, logs audit when link exists")
        void happyPath() {
            when(groupTeamLinkRepository.existsByGroupIdAndTeamId(GROUP_ID, TEAM_ID)).thenReturn(true);

            service.unlinkTeam(USER_ID, GROUP_ID, TEAM_ID);

            verify(groupTeamLinkRepository).deleteByGroupIdAndTeamId(GROUP_ID, TEAM_ID);
            verify(auditService).log(eq(GROUP_ID), eq(USER_ID), eq("TEAM_UNLINKED"), any(), any(), any());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // transferOwnership
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("transferOwnership")
    class TransferOwnership {

        private static final Long NEW_OWNER_ID = 99L;

        private TransferOwnershipRequest req() {
            return TransferOwnershipRequest.builder().newOwnerId(NEW_OWNER_ID).build();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when group not found")
        void groupNotFound_throws() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.transferOwnership(USER_ID, GROUP_ID, req()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws ForbiddenException when caller is not the owner")
        void notOwner_throwsForbidden() {
            Group g = group();
            g.setOwnerId(999L); // someone else is the owner
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(g));

            assertThatThrownBy(() -> service.transferOwnership(USER_ID, GROUP_ID, req()))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Only the owner");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when current owner membership not found")
        void currentOwnerMembershipNotFound_throws() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group()));
            when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.transferOwnership(USER_ID, GROUP_ID, req()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when new owner is not a current member")
        void newOwnerNotMember_throws() {
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group()));
            when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID))
                    .thenReturn(Optional.of(adminMember()));
            when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, NEW_OWNER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.transferOwnership(USER_ID, GROUP_ID, req()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws ValidationException when new owner is not APPROVED")
        void newOwnerNotApproved_throws() {
            GroupMember pendingNewOwner = GroupMember.builder()
                    .userId(NEW_OWNER_ID).role(MemberRole.MEMBER).status(MemberStatus.PENDING).build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group()));
            when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID))
                    .thenReturn(Optional.of(adminMember()));
            when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, NEW_OWNER_ID))
                    .thenReturn(Optional.of(pendingNewOwner));

            assertThatThrownBy(() -> service.transferOwnership(USER_ID, GROUP_ID, req()))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("approved member");
        }

        @Test
        @DisplayName("transfers roles, updates group ownerId, logs audit")
        void happyPath() {
            Group g = group();
            GroupMember currentOwnerMember = adminMember();
            GroupMember newOwnerMember = GroupMember.builder()
                    .userId(NEW_OWNER_ID).role(MemberRole.MEMBER).status(MemberStatus.APPROVED).build();

            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(g));
            when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID))
                    .thenReturn(Optional.of(currentOwnerMember));
            when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, NEW_OWNER_ID))
                    .thenReturn(Optional.of(newOwnerMember));
            when(groupMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(groupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.transferOwnership(USER_ID, GROUP_ID, req());

            assertThat(currentOwnerMember.getRole()).isEqualTo(MemberRole.ADMIN);
            assertThat(newOwnerMember.getRole()).isEqualTo(MemberRole.OWNER);
            assertThat(g.getOwnerId()).isEqualTo(NEW_OWNER_ID);
            verify(auditService).log(eq(GROUP_ID), eq(USER_ID), eq("OWNERSHIP_TRANSFERRED"), any(), any(), any());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // joinGameGroup
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("joinGameGroup")
    class JoinGameGroup {

        private JoinGroupRequest req() {
            return JoinGroupRequest.builder().message("let me in").build();
        }

        private GroupMemberResponse memberResponse() {
            return GroupMemberResponse.builder()
                    .userId(USER_ID).groupId(GROUP_ID)
                    .role(MemberRole.MEMBER).status(MemberStatus.APPROVED).build();
        }

        @Test
        @DisplayName("skips all gates when no profile exists for the group")
        void noProfile_gatesSkipped_delegatesToGroupMember() {
            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.empty());
            when(groupMemberService.joinGroup(eq(USER_ID), eq(GROUP_ID), any()))
                    .thenReturn(memberResponse());

            GroupMemberResponse resp = service.joinGameGroup(USER_ID, GROUP_ID, req());

            assertThat(resp.getStatus()).isEqualTo(MemberStatus.APPROVED);
            verifyNoInteractions(gameAccountLinkService, esportsBackendClient);
        }

        @Test
        @DisplayName("throws ValidationException when requireGameAccount=true and user has no verified account")
        void requireGameAccount_noAccount_throws() {
            GameGroupProfile p = profile();
            p.setRequireGameAccount(true);
            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(p));
            when(gameAccountLinkService.hasVerifiedAccount(USER_ID, "valorant")).thenReturn(false);

            assertThatThrownBy(() -> service.joinGameGroup(USER_ID, GROUP_ID, req()))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("verified game account");
        }

        @Test
        @DisplayName("passes game account gate when requireGameAccount=true and user has a verified account")
        void requireGameAccount_hasAccount_passes() {
            GameGroupProfile p = profile();
            p.setRequireGameAccount(true);
            p.setMinRank(null);
            p.setMaxRank(null);
            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(p));
            when(gameAccountLinkService.hasVerifiedAccount(USER_ID, "valorant")).thenReturn(true);
            when(groupMemberService.joinGroup(any(), any(), any())).thenReturn(memberResponse());

            assertThatCode(() -> service.joinGameGroup(USER_ID, GROUP_ID, req()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("skips rank gate when both minRank and maxRank are null")
        void bothRanksNull_rankGateSkipped() {
            GameGroupProfile p = profile();
            p.setMinRank(null);
            p.setMaxRank(null);
            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(p));
            when(groupMemberService.joinGroup(any(), any(), any())).thenReturn(memberResponse());

            assertThatCode(() -> service.joinGameGroup(USER_ID, GROUP_ID, req()))
                    .doesNotThrowAnyException();

            verifyNoInteractions(esportsBackendClient);
        }

        @Test
        @DisplayName("skips rank check (soft gate) when esports client throws")
        void clientThrows_rankGateSkipped() {
            GameGroupProfile p = profile();
            p.setMinRank("100");
            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(p));
            when(esportsBackendClient.getPlayerByUserId(anyString()))
                    .thenThrow(new RuntimeException("downstream failure"));
            when(groupMemberService.joinGroup(any(), any(), any())).thenReturn(memberResponse());

            assertThatCode(() -> service.joinGameGroup(USER_ID, GROUP_ID, req()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("skips rank check when playerDto is null")
        void nullPlayerDto_rankGateSkipped() {
            GameGroupProfile p = profile();
            p.setMinRank("100");
            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(p));
            when(esportsBackendClient.getPlayerByUserId(anyString())).thenReturn(null);
            when(groupMemberService.joinGroup(any(), any(), any())).thenReturn(memberResponse());

            assertThatCode(() -> service.joinGameGroup(USER_ID, GROUP_ID, req()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("skips rank check when playerDto has null id")
        void playerIdNull_rankGateSkipped() {
            GameGroupProfile p = profile();
            p.setMinRank("100");
            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(p));
            when(esportsBackendClient.getPlayerByUserId(anyString()))
                    .thenReturn(PlayerDto.builder().id(null).build());
            when(groupMemberService.joinGroup(any(), any(), any())).thenReturn(memberResponse());

            assertThatCode(() -> service.joinGameGroup(USER_ID, GROUP_ID, req()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("skips rank check when player statistics are null")
        void nullStats_rankGateSkipped() {
            GameGroupProfile p = profile();
            p.setMinRank("100");
            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(p));
            when(esportsBackendClient.getPlayerByUserId(anyString()))
                    .thenReturn(PlayerDto.builder().id(PLAYER_ID).build());
            when(esportsBackendClient.getPlayerStatistics(PLAYER_ID)).thenReturn(null);
            when(groupMemberService.joinGroup(any(), any(), any())).thenReturn(memberResponse());

            assertThatCode(() -> service.joinGameGroup(USER_ID, GROUP_ID, req()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("throws ValidationException when player ELO is below minRank")
        void elobelowMinRank_throws() {
            GameGroupProfile p = profile();
            p.setMinRank("1000"); // require elo >= 1000

            stubRankFetch(p, buildStats(2, 1, 1.0, 100.0)); // score ≈ low

            assertThatThrownBy(() -> service.joinGameGroup(USER_ID, GROUP_ID, req()))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("minimum requirement");
        }

        @Test
        @DisplayName("throws ValidationException when player ELO is above maxRank")
        void eloAboveMaxRank_throws() {
            GameGroupProfile p = profile();
            p.setMaxRank("1"); // require elo <= 1 — impossible for any real player

            stubRankFetch(p, buildStats(100, 90, 5.0, 1000.0)); // score = 100

            assertThatThrownBy(() -> service.joinGameGroup(USER_ID, GROUP_ID, req()))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("exceeds the maximum");
        }

        @Test
        @DisplayName("passes rank gate when ELO is within minRank and maxRank")
        void eloWithinRange_passes() {
            GameGroupProfile p = profile();
            p.setMinRank("1");    // any elo >= 1
            p.setMaxRank("100");  // any elo <= 100

            stubRankFetch(p, buildStats(10, 5, 1.0, 200.0)); // score moderate

            when(groupMemberService.joinGroup(any(), any(), any())).thenReturn(memberResponse());

            assertThatCode(() -> service.joinGameGroup(USER_ID, GROUP_ID, req()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("skips rank comparison when minRank is non-numeric")
        void nonNumericMinRank_skipped() {
            GameGroupProfile p = profile();
            p.setMinRank("Gold");  // non-numeric → skip
            p.setMaxRank(null);

            stubRankFetch(p, buildStats(10, 5, 1.0, 200.0));
            when(groupMemberService.joinGroup(any(), any(), any())).thenReturn(memberResponse());

            assertThatCode(() -> service.joinGameGroup(USER_ID, GROUP_ID, req()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("skips rank comparison when maxRank is non-numeric")
        void nonNumericMaxRank_skipped() {
            GameGroupProfile p = profile();
            p.setMinRank(null);
            p.setMaxRank("Diamond"); // non-numeric → skip

            stubRankFetch(p, buildStats(100, 90, 5.0, 1000.0));
            when(groupMemberService.joinGroup(any(), any(), any())).thenReturn(memberResponse());

            assertThatCode(() -> service.joinGameGroup(USER_ID, GROUP_ID, req()))
                    .doesNotThrowAnyException();
        }

        // ── private helpers ────────────────────────────────────────

        private void stubRankFetch(GameGroupProfile p, PlayerStatisticsDto stats) {
            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(p));
            when(esportsBackendClient.getPlayerByUserId(anyString()))
                    .thenReturn(PlayerDto.builder().id(PLAYER_ID).build());
            when(esportsBackendClient.getPlayerStatistics(PLAYER_ID)).thenReturn(stats);
        }

        private PlayerStatisticsDto buildStats(int matches, int wins,
                                               double kd, double avgScore) {
            return PlayerStatisticsDto.builder()
                    .matchesPlayed(matches).wins(wins)
                    .killDeathRatio(kd).averageScore(avgScore).build();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // searchLfsGroups
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("searchLfsGroups")
    class SearchLfsGroups {

        @Test
        @DisplayName("delegates to searchActiveLfsGroups with correct params")
        void delegatesCorrectly() {
            Page<GroupScrimFilter> page = new PageImpl<>(List.of());
            when(groupScrimFilterRepository.searchActiveLfsGroups(
                    eq("valorant"), eq("NA"), eq("5v5"), eq(1000), eq(2000), any()))
                    .thenReturn(page);

            service.searchLfsGroups("valorant", "NA", "5v5", 1000, 2000, 0, 10);

            verify(groupScrimFilterRepository).searchActiveLfsGroups(
                    eq("valorant"), eq("NA"), eq("5v5"), eq(1000), eq(2000), any());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // getLeaderboard
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getLeaderboard — resolveLeaderboardSort branches")
    class GetLeaderboard {

        @Test
        @DisplayName("uses winRate DESC for WIN_RATE sort")
        void winRate_sort() {
            Page<GameGroupProfile> page = new PageImpl<>(List.of());
            when(gameGroupProfileRepository.findByGameTag(eq("valorant"), argThat(p ->
                    p.getSort().toString().contains("winRate"))))
                    .thenReturn(page);

            service.getLeaderboard("valorant", "WIN_RATE", 0, 10);

            verify(gameGroupProfileRepository).findByGameTag(eq("valorant"),
                    argThat(p -> p.getSort().toString().contains("winRate")));
        }

        @Test
        @DisplayName("uses scrimCount DESC for SCRIM_COUNT sort")
        void scrimCount_sort() {
            Page<GameGroupProfile> page = new PageImpl<>(List.of());
            when(gameGroupProfileRepository.findByGameTag(eq("valorant"), argThat(p ->
                    p.getSort().toString().contains("scrimCount"))))
                    .thenReturn(page);

            service.getLeaderboard("valorant", "SCRIM_COUNT", 0, 10);

            verify(gameGroupProfileRepository).findByGameTag(eq("valorant"),
                    argThat(p -> p.getSort().toString().contains("scrimCount")));
        }

        @Test
        @DisplayName("uses averageElo DESC for AVERAGE_ELO sort")
        void averageElo_sort() {
            Page<GameGroupProfile> page = new PageImpl<>(List.of());
            when(gameGroupProfileRepository.findByGameTag(eq("valorant"), argThat(p ->
                    p.getSort().toString().contains("averageElo"))))
                    .thenReturn(page);

            service.getLeaderboard("valorant", "AVERAGE_ELO", 0, 10);

            verify(gameGroupProfileRepository).findByGameTag(eq("valorant"),
                    argThat(p -> p.getSort().toString().contains("averageElo")));
        }

        @Test
        @DisplayName("defaults to winRate DESC for unrecognised sortBy")
        void unknownSortBy_defaultsToWinRate() {
            Page<GameGroupProfile> page = new PageImpl<>(List.of());
            when(gameGroupProfileRepository.findByGameTag(eq("valorant"), argThat(p ->
                    p.getSort().toString().contains("winRate"))))
                    .thenReturn(page);

            service.getLeaderboard("valorant", "UNKNOWN_SORT", 0, 10);

            verify(gameGroupProfileRepository).findByGameTag(eq("valorant"),
                    argThat(p -> p.getSort().toString().contains("winRate")));
        }

        @Test
        @DisplayName("defaults to winRate DESC when sortBy is null")
        void nullSortBy_defaultsToWinRate() {
            Page<GameGroupProfile> page = new PageImpl<>(List.of());
            when(gameGroupProfileRepository.findByGameTag(eq("valorant"), argThat(p ->
                    p.getSort().toString().contains("winRate"))))
                    .thenReturn(page);

            service.getLeaderboard("valorant", null, 0, 10);

            verify(gameGroupProfileRepository).findByGameTag(eq("valorant"),
                    argThat(p -> p.getSort().toString().contains("winRate")));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // getChatChannel
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getChatChannel")
    class GetChatChannel {

        @Test
        @DisplayName("throws ResourceNotFoundException when profile not found")
        void profileNotFound_throws() {
            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getChatChannel(GROUP_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when chatChannelId is null (not yet provisioned)")
        void chatChannelIdNull_throws() {
            GameGroupProfile p = profile();
            p.setChatChannelId(null);
            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(p));

            assertThatThrownBy(() -> service.getChatChannel(GROUP_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("returns ChatChannelResponse when chatChannelId is present")
        void chatChannelPresent_returnsResponse() {
            GameGroupProfile p = profile();
            p.setChatChannelId("channel-abc-123");
            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(p));

            ChatChannelResponse resp = service.getChatChannel(GROUP_ID);

            assertThat(resp.getChatChannelId()).isEqualTo("channel-abc-123");
            assertThat(resp.getGroupId()).isEqualTo(GROUP_ID);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // updateChatChannelId
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("updateChatChannelId")
    class UpdateChatChannelId {

        @Test
        @DisplayName("does nothing when profile not found (no-op)")
        void profileAbsent_noOp() {
            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.empty());

            assertThatCode(() -> service.updateChatChannelId(GROUP_ID, "ch-xyz"))
                    .doesNotThrowAnyException();

            verify(gameGroupProfileRepository, never()).save(any());
        }

        @Test
        @DisplayName("sets chatChannelId and saves when profile exists")
        void profilePresent_channelIdSet() {
            GameGroupProfile p = profile();
            p.setChatChannelId(null);
            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(p));
            when(gameGroupProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.updateChatChannelId(GROUP_ID, "ch-xyz");

            assertThat(p.getChatChannelId()).isEqualTo("ch-xyz");
            verify(gameGroupProfileRepository).save(p);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // computeMvpScore (tested via joinGameGroup rank gate)
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("computeMvpScore — score formula branches")
    class ComputeMvpScore {

        @Test
        @DisplayName("produces score of 0 when all stat fields are null")
        void allNullStats_scoreZero() {
            GameGroupProfile p = profile();
            p.setMinRank("1"); // any elo >= 1 — will reject score 0

            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(p));
            when(esportsBackendClient.getPlayerByUserId(anyString()))
                    .thenReturn(PlayerDto.builder().id(PLAYER_ID).build());
            when(esportsBackendClient.getPlayerStatistics(PLAYER_ID))
                    .thenReturn(PlayerStatisticsDto.builder().build()); // all null

            // score = 0, minRank = 1, so 0 < 1 → rejects
            assertThatThrownBy(() -> service.joinGameGroup(USER_ID, GROUP_ID,
                    JoinGroupRequest.builder().build()))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("minimum requirement");
        }

        @Test
        @DisplayName("produces score of 100 for perfect stats (capped at 100)")
        void perfectStats_score100() {
            GameGroupProfile p = profile();
            p.setMaxRank("99"); // elo <= 99 — will reject score 100

            when(gameGroupProfileRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(p));
            when(esportsBackendClient.getPlayerByUserId(anyString()))
                    .thenReturn(PlayerDto.builder().id(PLAYER_ID).build());
            when(esportsBackendClient.getPlayerStatistics(PLAYER_ID))
                    .thenReturn(PlayerStatisticsDto.builder()
                            .matchesPlayed(100).wins(100)
                            .killDeathRatio(5.0)
                            .averageScore(1000.0)
                            .build());

            // score = 100, maxRank = 99, so 100 > 99 → rejects
            assertThatThrownBy(() -> service.joinGameGroup(USER_ID, GROUP_ID,
                    JoinGroupRequest.builder().build()))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("exceeds the maximum");
        }
    }
}