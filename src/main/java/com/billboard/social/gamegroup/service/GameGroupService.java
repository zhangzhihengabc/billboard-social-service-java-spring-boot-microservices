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
import com.billboard.social.group.entity.Group;
import com.billboard.social.group.entity.GroupMember;
import com.billboard.social.group.entity.enums.MemberRole;
import com.billboard.social.group.entity.enums.MemberStatus;
import com.billboard.social.group.repository.GroupMemberRepository;
import com.billboard.social.group.repository.GroupRepository;
import com.billboard.social.group.service.GroupMemberService;
import com.billboard.social.group.service.GroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameGroupService {

    private final GroupService groupService;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupMemberService groupMemberService;
    private final GameGroupProfileRepository gameGroupProfileRepository;
    private final GroupScrimFilterRepository groupScrimFilterRepository;
    private final GroupTeamLinkRepository groupTeamLinkRepository;
    private final GameGroupEventPublisher eventPublisher;
    private final GameAccountLinkService gameAccountLinkService;
    private final AuditService auditService;
    private final EsportsBackendClient esportsBackendClient;

    // ==================== CORE METHODS (18A) ====================

    @Transactional
    public GameGroupResponse createGameGroup(Long userId, CreateGameGroupRequest request) {
        // Create base group via GroupService
        com.billboard.social.group.dto.request.GroupRequests.CreateGroupRequest baseRequest =
                com.billboard.social.group.dto.request.GroupRequests.CreateGroupRequest.builder()
                        .name(request.getName())
                        .description(request.getDescription())
                        .build();

        com.billboard.social.group.dto.response.GroupResponses.GroupResponse baseGroup =
                groupService.createGroup(userId, baseRequest);

        Group group = groupRepository.findById(baseGroup.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", baseGroup.getId()));

        // Create game profile
        GameGroupProfile profile = GameGroupProfile.builder()
                .group(group)
                .gameTag(request.getGameTag())
                .gameId(request.getGameId())
                .region(request.getRegion())
                .platform(request.getPlatform())
                .minRank(request.getMinRank())
                .maxRank(request.getMaxRank())
                .requireGameAccount(request.getRequireGameAccount() != null ? request.getRequireGameAccount() : false)
                .discordServerId(request.getDiscordServerId())
                .discordChannelId(request.getDiscordChannelId())
                .build();

        profile = gameGroupProfileRepository.save(profile);

        // Request a dedicated chat channel for this game group.
        // The chat service processes this event asynchronously and fires group.chat.channel.created
        // when the channel is ready; GroupChatChannelConsumer stores the returned channelId.
        eventPublisher.publishGroupChatRequested(group.getId(), List.of(userId));

        auditService.log(group.getId(), userId, "GROUP_CREATED", "GROUP", group.getId().toString(),
                "Game group created for " + request.getGameTag());

        log.info("Game group created: groupId={} gameTag={} by user={}", group.getId(), request.getGameTag(), userId);
        return mapToGameGroupResponse(group, profile, userId);
    }

    @Transactional
    public GameGroupResponse updateGameGroupProfile(Long userId, UUID groupId, UpdateGameGroupProfileRequest request) {
        checkAdminAccess(userId, groupId);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));

        GameGroupProfile profile = gameGroupProfileRepository.findByGroupId(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("GameGroupProfile", "groupId", groupId));

        if (request.getRegion() != null) profile.setRegion(request.getRegion());
        if (request.getPlatform() != null) profile.setPlatform(request.getPlatform());
        if (request.getMinRank() != null) profile.setMinRank(request.getMinRank());
        if (request.getMaxRank() != null) profile.setMaxRank(request.getMaxRank());
        if (request.getRequireGameAccount() != null) profile.setRequireGameAccount(request.getRequireGameAccount());
        if (request.getDiscordServerId() != null) profile.setDiscordServerId(request.getDiscordServerId());
        if (request.getDiscordChannelId() != null) profile.setDiscordChannelId(request.getDiscordChannelId());

        profile = gameGroupProfileRepository.save(profile);

        auditService.log(groupId, userId, "PROFILE_UPDATED", "GROUP", groupId.toString(), "Game profile updated");

        log.info("Game group profile updated: groupId={} by user={}", groupId, userId);
        return mapToGameGroupResponse(group, profile, userId);
    }

    @Transactional(readOnly = true)
    public GameGroupResponse getGameGroup(UUID groupId, Long currentUserId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));

        GameGroupProfile profile = gameGroupProfileRepository.findByGroupId(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("GameGroupProfile", "groupId", groupId));

        return mapToGameGroupResponse(group, profile, currentUserId);
    }

    @Transactional(readOnly = true)
    public PageResponse<GameGroupResponse> searchGameGroups(String gameTag, String region, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<GameGroupProfile> profiles;

        if (region != null && !region.isBlank()) {
            profiles = gameGroupProfileRepository.findByGameTagAndRegion(gameTag, region, pageRequest);
        } else {
            profiles = gameGroupProfileRepository.findByGameTag(gameTag, pageRequest);
        }

        return PageResponse.from(profiles, p -> {
            Group group = p.getGroup();
            return mapToGameGroupResponse(group, p, null);
        });
    }

    @Transactional(readOnly = true)
    public GameGroupEmbedResponse getGroupEmbed(UUID groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));

        GameGroupProfile profile = gameGroupProfileRepository.findByGroupId(groupId)
                .orElse(null);

        return GameGroupEmbedResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .slug(group.getSlug())
                .groupType(group.getGroupType())
                .gameTag(profile != null ? profile.getGameTag() : null)
                .region(profile != null ? profile.getRegion() : null)
                .memberCount(group.getMemberCount())
                .isVerified(group.getIsVerified())
                .iconUrl(group.getIconUrl())
                .build();
    }

    // ==================== LFS / SCRIM / TEAMS / OWNERSHIP (18B) ====================

    @Transactional
    public ScrimFilterResponse saveScrimFilter(Long userId, UUID groupId, ScrimFilterRequest request) {
        checkAdminAccess(userId, groupId);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));

        GroupScrimFilter filter = groupScrimFilterRepository.findByGroupId(groupId)
                .orElse(GroupScrimFilter.builder().group(group).build());

        filter.setGameTag(request.getGameTag());
        filter.setRegion(request.getRegion());
        filter.setFormat(request.getFormat());
        filter.setMapPool(request.getMapPool());
        filter.setMinTeamSize(request.getMinTeamSize());
        filter.setMaxTeamSize(request.getMaxTeamSize());
        filter.setMinElo(request.getMinElo());
        filter.setMaxElo(request.getMaxElo());
        filter.setAvailabilitySlots(request.getAvailabilitySlots());
        if (request.getIsActive() != null) filter.setIsActive(request.getIsActive());

        filter = groupScrimFilterRepository.save(filter);

        auditService.log(groupId, userId, "SCRIM_FILTER_SAVED", "SCRIM_FILTER", filter.getId().toString(), null);

        log.info("Scrim filter saved: groupId={} by user={}", groupId, userId);
        return mapToScrimFilterResponse(filter);
    }

    @Transactional(readOnly = true)
    public ScrimFilterResponse getScrimFilter(UUID groupId) {
        GroupScrimFilter filter = groupScrimFilterRepository.findByGroupId(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("ScrimFilter", "groupId", groupId));
        return mapToScrimFilterResponse(filter);
    }

    @Transactional(readOnly = true)
    public List<Long> getMemberIds(UUID groupId) {
        return groupMemberRepository.findMemberUserIds(groupId);
    }

    @Transactional
    public void broadcastLfs(Long userId, UUID groupId, LfsBroadcastRequest request) {
        checkAdminAccess(userId, groupId);

        GroupScrimFilter filter = groupScrimFilterRepository.findByGroupId(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("ScrimFilter", "groupId", groupId));

        if (!filter.getIsActive()) {
            throw new ValidationException("Scrim filter must be active to broadcast LFS");
        }

        // 5-minute cooldown check
        if (filter.getLastBroadcastAt() != null &&
                filter.getLastBroadcastAt().isAfter(LocalDateTime.now().minusMinutes(5))) {
            throw new ValidationException("LFS broadcast cooldown: please wait 5 minutes between broadcasts");
        }

        List<Long> memberIds = groupMemberRepository.findMemberUserIds(groupId);

        GameGroupProfile profile = gameGroupProfileRepository.findByGroupId(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("GameGroupProfile", "groupId", groupId));

        filter.setLastBroadcastAt(LocalDateTime.now());
        groupScrimFilterRepository.save(filter);

        eventPublisher.publishLfsBroadcast(groupId, profile.getGameTag(), profile.getRegion(), memberIds, request.getMessage());

        auditService.log(groupId, userId, "LFS_BROADCAST", "GROUP", groupId.toString(), null);
        log.info("LFS broadcast: groupId={} by user={}", groupId, userId);
    }

    @Transactional
    public void cancelLfs(Long userId, UUID groupId) {
        checkAdminAccess(userId, groupId);

        GameGroupProfile profile = gameGroupProfileRepository.findByGroupId(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("GameGroupProfile", "groupId", groupId));

        GroupScrimFilter filter = groupScrimFilterRepository.findByGroupId(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("ScrimFilter", "groupId", groupId));

        filter.setIsActive(false);
        groupScrimFilterRepository.save(filter);

        eventPublisher.publishLfsCancelled(groupId, profile.getGameTag());

        auditService.log(groupId, userId, "LFS_CANCELLED", "GROUP", groupId.toString(), null);
        log.info("LFS cancelled: groupId={} by user={}", groupId, userId);
    }

    @Transactional
    public LfsMatchFoundResponse handleMatchFound(Long userId, UUID groupId, LfsMatchFoundRequest request) {
        checkAdminAccess(userId, groupId);

        eventPublisher.publishLfsMatched(groupId, request.getMatchedGroupId(), request.getGameTag());

        auditService.log(groupId, userId, "LFS_MATCHED", "GROUP", request.getMatchedGroupId().toString(), null);
        log.info("LFS match found: groupId={} matchedGroupId={}", groupId, request.getMatchedGroupId());

        return LfsMatchFoundResponse.builder()
                .groupId(groupId)
                .matchedGroupId(request.getMatchedGroupId())
                .gameTag(request.getGameTag())
                .matchedAt(LocalDateTime.now())
                .build();
    }

    @Transactional
    public GroupTeamLinkResponse linkTeam(Long userId, UUID groupId, LinkTeamRequest request) {
        checkAdminAccess(userId, groupId);

        if (groupTeamLinkRepository.existsByGroupIdAndTeamId(groupId, request.getTeamId())) {
            throw new ValidationException("Team is already linked to this group");
        }

        // Validate team membership via esports-backend (two-step).
        // Step 1: resolve SSO userId (Long) → esports playerId (Long).
        // Step 2: check that playerId appears in the team's member list with status ACTIVE.
        boolean isMember = false;
        try {
            PlayerDto playerDto = esportsBackendClient.getPlayerByUserId(userId.toString());
            if (playerDto != null && playerDto.getId() != null) {
                TeamMembersResponseDto teamMembers = esportsBackendClient.getTeamMembers(request.getTeamId());
                if (teamMembers != null && teamMembers.getMembers() != null) {
                    isMember = teamMembers.getMembers().stream()
                            .anyMatch(m -> m.getPlayerId() != null
                                    && m.getPlayerId().equals(playerDto.getId())
                                    && "ACTIVE".equals(m.getStatus()));
                }
            }
        } catch (Exception e) {
            log.warn("Could not validate team membership for userId={} teamId={}: {}",
                    userId, request.getTeamId(), e.getMessage());
        }
        if (!isMember) {
            throw new ForbiddenException("You must be a member of the team to link it");
        }

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));

        TeamSummaryDto teamSummary = esportsBackendClient.getTeamSummary(request.getTeamId());

        GroupTeamLink link = GroupTeamLink.builder()
                .group(group)
                .teamId(request.getTeamId())
                .linkedBy(userId)
                .build();

        link = groupTeamLinkRepository.save(link);

        auditService.log(groupId, userId, "TEAM_LINKED", "TEAM", request.getTeamId().toString(), null);
        log.info("Team {} linked to group {} by user {}", request.getTeamId(), groupId, userId);

        return GroupTeamLinkResponse.builder()
                .id(link.getId())
                .groupId(groupId)
                .teamId(link.getTeamId())
                .teamName(teamSummary != null ? teamSummary.getName() : null)
                .linkedBy(link.getLinkedBy())
                .linkedAt(link.getLinkedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<GroupTeamLinkResponse> getLinkedTeams(UUID groupId) {
        return groupTeamLinkRepository.findByGroupId(groupId)
                .stream()
                .map(link -> {
                    TeamSummaryDto teamSummary = null;
                    try {
                        teamSummary = esportsBackendClient.getTeamSummary(link.getTeamId());
                    } catch (Exception e) {
                        log.warn("Could not fetch team summary for teamId={}", link.getTeamId());
                    }
                    TeamSummaryDto finalTeamSummary = teamSummary;
                    return GroupTeamLinkResponse.builder()
                            .id(link.getId())
                            .groupId(groupId)
                            .teamId(link.getTeamId())
                            .teamName(finalTeamSummary != null ? finalTeamSummary.getName() : null)
                            .linkedBy(link.getLinkedBy())
                            .linkedAt(link.getLinkedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void unlinkTeam(Long userId, UUID groupId, Long teamId) {
        checkAdminAccess(userId, groupId);

        if (!groupTeamLinkRepository.existsByGroupIdAndTeamId(groupId, teamId)) {
            throw new ResourceNotFoundException("Team link not found for teamId=" + teamId);
        }

        groupTeamLinkRepository.deleteByGroupIdAndTeamId(groupId, teamId);

        auditService.log(groupId, userId, "TEAM_UNLINKED", "TEAM", teamId.toString(), null);
        log.info("Team {} unlinked from group {} by user {}", teamId, groupId, userId);
    }

    @Transactional
    public void transferOwnership(Long ownerId, UUID groupId, TransferOwnershipRequest request) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));

        if (!group.getOwnerId().equals(ownerId)) {
            throw new ForbiddenException("Only the owner can transfer ownership");
        }

        GroupMember currentOwner = groupMemberRepository.findByGroupIdAndUserId(groupId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Owner membership not found"));

        GroupMember newOwner = groupMemberRepository.findByGroupIdAndUserId(groupId, request.getNewOwnerId())
                .orElseThrow(() -> new ResourceNotFoundException("New owner must be a current member"));

        if (newOwner.getStatus() != MemberStatus.APPROVED) {
            throw new ValidationException("New owner must be an approved member");
        }

        currentOwner.setRole(MemberRole.ADMIN);
        newOwner.setRole(MemberRole.OWNER);
        group.setOwnerId(request.getNewOwnerId());

        groupMemberRepository.save(currentOwner);
        groupMemberRepository.save(newOwner);
        groupRepository.save(group);

        auditService.log(groupId, ownerId, "OWNERSHIP_TRANSFERRED", "USER",
                request.getNewOwnerId().toString(), "Ownership transferred to userId=" + request.getNewOwnerId());

        log.info("Ownership of group {} transferred from {} to {}", groupId, ownerId, request.getNewOwnerId());
    }

    // ==================== GAME GROUP JOIN (rank-gating) ====================

    /**
     * Join a game group with esports-specific gating checks enforced before delegating to
     * the generic GroupMemberService. This keeps all esports concerns (game accounts, ELO
     * ranks) inside the gamegroup module and leaves the group module untouched.
     *
     * Gate order:
     * 1. requireGameAccount — user must have a verified account for this game.
     * 2. minRank / maxRank  — user's ELO must fall within the group's bracket.
     *                          Soft gate: if the rank fetch fails, the check is skipped.
     * 3. Delegates to GroupMemberService.joinGroup() for the actual membership record.
     */
    @Transactional
    public com.billboard.social.group.dto.response.GroupResponses.GroupMemberResponse joinGameGroup(
            Long userId, UUID groupId,
            com.billboard.social.group.dto.request.GroupRequests.JoinGroupRequest request) {

        gameGroupProfileRepository.findByGroupId(groupId).ifPresent(profile -> {

            // Gate 1: verified game account required
            if (Boolean.TRUE.equals(profile.getRequireGameAccount())) {
                boolean hasAccount = gameAccountLinkService.hasVerifiedAccount(userId, profile.getGameTag());
                if (!hasAccount) {
                    throw new ValidationException(
                            "A verified game account for " + profile.getGameTag() + " is required to join this group");
                }
            }

            // Gate 2: rank bracket check (soft — skipped if rank fetch fails)
            if (profile.getMinRank() != null || profile.getMaxRank() != null) {
                com.billboard.social.common.dto.PlayerRankDto playerRank = null;
                try {
                    // Req 3 — two-step rank fetch.
                    // Step 1: resolve SSO userId → esports playerId.
                    // Step 2: fetch player statistics and compute composite MVP score (0-100).
                    // The score is stored in PlayerRankDto.elo and compared against
                    // profile.getMinRank() / profile.getMaxRank().
                    // Soft gate: if either call fails, the rank check is skipped entirely.
                    PlayerDto playerDto = esportsBackendClient.getPlayerByUserId(userId.toString());
                    if (playerDto != null && playerDto.getId() != null) {
                        PlayerStatisticsDto stats = esportsBackendClient.getPlayerStatistics(playerDto.getId());
                        if (stats != null) {
                            playerRank = com.billboard.social.common.dto.PlayerRankDto.builder()
                                    .userId(userId)
                                    .gameTag(profile.getGameTag())
                                    .elo(computeMvpScore(stats))
                                    .build();
                        }
                    }
                } catch (Exception e) {
                    log.warn("Could not fetch rank for user={} gameTag={}: {}", userId, profile.getGameTag(), e.getMessage());
                }

                if (playerRank != null && playerRank.getElo() != null) {
                    if (profile.getMinRank() != null && profile.getMinRank().matches("\\d+")
                            && playerRank.getElo() < Integer.parseInt(profile.getMinRank())) {
                        throw new ValidationException("Your rank does not meet the minimum requirement for this group");
                    }
                    if (profile.getMaxRank() != null && profile.getMaxRank().matches("\\d+")
                            && playerRank.getElo() > Integer.parseInt(profile.getMaxRank())) {
                        throw new ValidationException("Your rank exceeds the maximum allowed for this group");
                    }
                }
            }
        });

        // Gate 3: delegate to group module for the actual membership record
        return groupMemberService.joinGroup(userId, groupId, request);
    }

    // ==================== SECTION 5: GROUP-EXCLUSIVE SCRIM SEARCH ====================

    /**
     * Find groups currently broadcasting LFS (Looking For Scrim) that match the given criteria.
     * ELO range overlap: returns groups whose scrim filter ELO range intersects with the
     * caller's (minElo, maxElo) window. NULL caller params skip that criterion.
     * Results are sorted by lastBroadcastAt DESC so the most recent broadcasts appear first.
     */
    @Transactional(readOnly = true)
    public PageResponse<LfsGroupResponse> searchLfsGroups(String gameTag, String region,
                                                          String format, Integer minElo,
                                                          Integer maxElo, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "lastBroadcastAt"));

        Page<GroupScrimFilter> filters = groupScrimFilterRepository.searchActiveLfsGroups(
                gameTag, region, format, minElo, maxElo, pageRequest);

        return PageResponse.from(filters, this::mapToLfsGroupResponse);
    }

    // ==================== SECTION 5: GROUP LEADERBOARDS ====================

    /**
     * Return a paginated, sorted leaderboard of game groups for a given game tag.
     * Supported sortBy values: WIN_RATE (default), SCRIM_COUNT, AVERAGE_ELO.
     * Groups with null stats (no completed scrims yet) sort to the bottom.
     */
    @Transactional(readOnly = true)
    public PageResponse<LeaderboardEntryResponse> getLeaderboard(String gameTag, String sortBy,
                                                                 int page, int size) {
        Sort sort = resolveLeaderboardSort(sortBy);
        PageRequest pageRequest = PageRequest.of(page, size, sort);

        Page<GameGroupProfile> profiles = gameGroupProfileRepository.findByGameTag(gameTag, pageRequest);

        return PageResponse.from(profiles, profile -> {
            Group group = profile.getGroup();
            return LeaderboardEntryResponse.builder()
                    .groupId(group.getId())
                    .groupName(group.getName())
                    .slug(group.getSlug())
                    .memberCount(group.getMemberCount())
                    .gameTag(profile.getGameTag())
                    .region(profile.getRegion())
                    .scrimCount(profile.getScrimCount())
                    .winRate(profile.getWinRate())
                    .averageElo(profile.getAverageElo())
                    .build();
        });
    }

    // ==================== SECTION 5: GROUP CHAT CHANNELS ====================

    /**
     * Return the chat channel provisioned for this game group.
     * The chatChannelId is set asynchronously by GroupChatChannelConsumer after the
     * chat service processes the GROUP_CHAT_REQUESTED event fired at group creation.
     * Returns 404 if the channel has not yet been provisioned.
     */
    @Transactional(readOnly = true)
    public ChatChannelResponse getChatChannel(UUID groupId) {
        GameGroupProfile profile = gameGroupProfileRepository.findByGroupId(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("GameGroupProfile", "groupId", groupId));

        if (profile.getChatChannelId() == null) {
            throw new ResourceNotFoundException("ChatChannel", "groupId", groupId);
        }

        return ChatChannelResponse.builder()
                .groupId(groupId)
                .chatChannelId(profile.getChatChannelId())
                .build();
    }

    /**
     * Store the chat channel ID returned by the social chat service.
     * Called exclusively by GroupChatChannelConsumer — not exposed via HTTP.
     */
    @Transactional
    public void updateChatChannelId(UUID groupId, String chatChannelId) {
        gameGroupProfileRepository.findByGroupId(groupId).ifPresent(profile -> {
            profile.setChatChannelId(chatChannelId);
            gameGroupProfileRepository.save(profile);
            log.info("Chat channel provisioned for group {}: channelId={}", groupId, chatChannelId);
        });
    }

    // ==================== PRIVATE HELPERS ====================

    /**
     * Computes a composite player score (0–100) from esports-backend statistics.
     *
     * This mirrors the MVP scoring algorithm in the esports-backend
     * (GET /api/statistics/mvp-players) and is used as the rank proxy for
     * rank-gated group joins where no per-game ELO is available.
     *
     * Formula:
     *   KD Ratio normalised   × 30%   (KD of 5.0 = max 100)
     *   Win Rate              × 25%   (already 0-100)
     *   Wins normalised       × 20%   (50 wins = max 100)
     *   Average Score norm    × 15%   (avg 1000 = max 100)
     *   Activity normalised   × 10%   (100 matches = max 100)
     *
     * Returns an int so it can be stored in PlayerRankDto.elo directly.
     */
    private int computeMvpScore(PlayerStatisticsDto stats) {
        double kdRatio       = stats.getKillDeathRatio() != null ? stats.getKillDeathRatio() : 0.0;
        double kdScore       = Math.min(kdRatio * 20, 100.0);

        int    matchesPlayed = stats.getMatchesPlayed() != null ? stats.getMatchesPlayed() : 0;
        int    wins          = stats.getWins()          != null ? stats.getWins()          : 0;
        double winRate       = matchesPlayed > 0 ? (wins / (double) matchesPlayed) * 100.0 : 0.0;

        double winsScore     = Math.min((wins / 50.0) * 100.0, 100.0);

        double avgScore      = stats.getAverageScore()  != null ? stats.getAverageScore()  : 0.0;
        double avgScoreNorm  = Math.min((avgScore / 1000.0) * 100.0, 100.0);

        double activityScore = Math.min((matchesPlayed / 100.0) * 100.0, 100.0);

        double score = (kdScore      * 0.30)
                + (winRate      * 0.25)
                + (winsScore    * 0.20)
                + (avgScoreNorm * 0.15)
                + (activityScore * 0.10);

        return (int) Math.round(score);
    }


    private LfsGroupResponse mapToLfsGroupResponse(GroupScrimFilter filter) {
        Group group = filter.getGroup();
        // Attempt to enrich with stats from the game group profile. Profile may be absent for
        // orphaned filters (edge case), so guard with orElse(null).
        GameGroupProfile profile = gameGroupProfileRepository.findByGroupId(group.getId()).orElse(null);

        return LfsGroupResponse.builder()
                .groupId(group.getId())
                .groupName(group.getName())
                .slug(group.getSlug())
                .memberCount(group.getMemberCount())
                .gameTag(filter.getGameTag())
                .region(filter.getRegion())
                .platform(profile != null ? profile.getPlatform() : null)
                .averageElo(profile != null ? profile.getAverageElo() : null)
                .scrimCount(profile != null ? profile.getScrimCount() : 0)
                .format(filter.getFormat())
                .mapPool(filter.getMapPool())
                .minTeamSize(filter.getMinTeamSize())
                .maxTeamSize(filter.getMaxTeamSize())
                .minElo(filter.getMinElo())
                .maxElo(filter.getMaxElo())
                .availabilitySlots(filter.getAvailabilitySlots())
                .lastBroadcastAt(filter.getLastBroadcastAt())
                .build();
    }

    /**
     * Maps the user-supplied sortBy string to a Spring Data Sort.
     * Defaults to WIN_RATE DESC if the value is unrecognised or null.
     */
    private Sort resolveLeaderboardSort(String sortBy) {
        if (sortBy == null) {
            return Sort.by(Sort.Direction.DESC, "winRate");
        }
        return switch (sortBy.toUpperCase()) {
            case "SCRIM_COUNT" -> Sort.by(Sort.Direction.DESC, "scrimCount");
            case "AVERAGE_ELO" -> Sort.by(Sort.Direction.DESC, "averageElo");
            default            -> Sort.by(Sort.Direction.DESC, "winRate");
        };
    }

    private void checkAdminAccess(Long userId, UUID groupId) {
        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this group"));

        if (!member.isAdmin()) {
            throw new ForbiddenException("Admin access required");
        }
    }

    private GameGroupResponse mapToGameGroupResponse(Group group, GameGroupProfile profile, Long currentUserId) {
        GameGroupResponse.GameGroupResponseBuilder builder = GameGroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .slug(group.getSlug())
                .description(group.getDescription())
                .groupType(group.getGroupType())
                .ownerId(group.getOwnerId())
                .memberCount(group.getMemberCount())
                .isVerified(group.getIsVerified())
                .gameTag(profile.getGameTag())
                .gameId(profile.getGameId())
                .region(profile.getRegion())
                .platform(profile.getPlatform())
                .minRank(profile.getMinRank())
                .maxRank(profile.getMaxRank())
                .scrimCount(profile.getScrimCount())
                .winRate(profile.getWinRate())
                .averageElo(profile.getAverageElo())
                .requireGameAccount(profile.getRequireGameAccount())
                .discordServerId(profile.getDiscordServerId())
                .discordChannelId(profile.getDiscordChannelId())
                .createdAt(group.getCreatedAt());

        if (currentUserId != null) {
            groupMemberRepository.findByGroupIdAndUserId(group.getId(), currentUserId)
                    .ifPresent(member -> {
                        builder.isMember(member.isApproved());
                        builder.isAdmin(member.isAdmin());
                    });
        }

        return builder.build();
    }

    private ScrimFilterResponse mapToScrimFilterResponse(GroupScrimFilter filter) {
        return ScrimFilterResponse.builder()
                .id(filter.getId())
                .groupId(filter.getGroup().getId())
                .gameTag(filter.getGameTag())
                .region(filter.getRegion())
                .format(filter.getFormat())
                .mapPool(filter.getMapPool())
                .minTeamSize(filter.getMinTeamSize())
                .maxTeamSize(filter.getMaxTeamSize())
                .minElo(filter.getMinElo())
                .maxElo(filter.getMaxElo())
                .availabilitySlots(filter.getAvailabilitySlots())
                .isActive(filter.getIsActive())
                .lastBroadcastAt(filter.getLastBroadcastAt())
                .build();
    }
}