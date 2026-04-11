package com.billboard.social.friendsfinder.service;

import com.billboard.social.common.client.EsportsBackendClient;
import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.PlayerDto;
import com.billboard.social.common.exception.ResourceNotFoundException;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.friendsfinder.dto.FriendsFinderDtos.FriendFinderResultResponse;
import com.billboard.social.friendsfinder.dto.FriendsFinderDtos.ScrimHistoryResponse;
import com.billboard.social.friendsfinder.entity.FriendSuggestion;
import com.billboard.social.friendsfinder.entity.ScrimHistory;
import com.billboard.social.friendsfinder.repository.FriendSuggestionRepository;
import com.billboard.social.friendsfinder.repository.ScrimHistoryRepository;
import com.billboard.social.graph.entity.Friendship;
import com.billboard.social.graph.repository.BlockRepository;
import com.billboard.social.graph.repository.FriendshipRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendsFinderServiceTest {

    @Mock private EsportsBackendClient esportsBackendClient;
    @Mock private FriendshipRepository friendshipRepository;
    @Mock private BlockRepository blockRepository;
    @Mock private FriendSuggestionRepository friendSuggestionRepository;
    @Mock private ScrimHistoryRepository scrimHistoryRepository;

    @InjectMocks
    private FriendsFinderService service;

    // ── Helper ───────────────────────────────────────────────────────────

    private PlayerDto player(Long id, String user, String gamerTag) {
        PlayerDto p = new PlayerDto();
        p.setId(id);
        p.setUser(user);
        p.setGamerTag(gamerTag);
        return p;
    }

    // ── searchPlayers ────────────────────────────────────────────────────

    @Test
    void searchPlayers_filtersSelfFromResults() {
        Long myUserId = 42L;
        when(esportsBackendClient.searchPlayersByCriteria(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(
                        player(1L, "42", "Me"),      // self — excluded
                        player(2L, "55", "Opponent") // other — included
                ));
        when(blockRepository.isBlockedEitherWay(anyLong(), anyLong())).thenReturn(false);
        when(friendshipRepository.findBetweenUsers(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(friendshipRepository.findMutualFriendIds(anyLong(), anyLong())).thenReturn(List.of());
        when(scrimHistoryRepository.countBetweenUsers(anyLong(), anyLong())).thenReturn(0);

        PageResponse<FriendFinderResultResponse> result = service.searchPlayers(myUserId, "SEA", null, null, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUserId()).isEqualTo(55L);
    }

    @Test
    void searchPlayers_filtersBlockedUsers() {
        when(esportsBackendClient.searchPlayersByCriteria(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(player(2L, "55", "BlockedGuy")));
        when(blockRepository.isBlockedEitherWay(42L, 55L)).thenReturn(true);

        PageResponse<FriendFinderResultResponse> result = service.searchPlayers(42L, null, null, null, 0, 20);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void searchPlayers_feignFailure_returnsEmptyPage() {
        when(esportsBackendClient.searchPlayersByCriteria(any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("Connection refused"));

        PageResponse<FriendFinderResultResponse> result = service.searchPlayers(42L, null, null, null, 0, 20);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void searchPlayers_friendshipStatusMappedCorrectly() {
        when(esportsBackendClient.searchPlayersByCriteria(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(player(2L, "55", "Friend")));
        when(blockRepository.isBlockedEitherWay(anyLong(), anyLong())).thenReturn(false);

        Friendship accepted = mock(Friendship.class);
        when(accepted.isAccepted()).thenReturn(true);
        when(friendshipRepository.findBetweenUsers(42L, 55L)).thenReturn(Optional.of(accepted));
        when(friendshipRepository.findMutualFriendIds(42L, 55L)).thenReturn(List.of(10L, 20L));
        when(scrimHistoryRepository.countBetweenUsers(42L, 55L)).thenReturn(3);

        PageResponse<FriendFinderResultResponse> result = service.searchPlayers(42L, "SEA", null, null, 0, 20);

        FriendFinderResultResponse r = result.getContent().get(0);
        assertThat(r.getFriendshipStatus()).isEqualTo("ACCEPTED");
        assertThat(r.getMutualFriendCount()).isEqualTo(2);
        assertThat(r.getScrimCount()).isEqualTo(3);
    }

    @Test
    void searchPlayers_pendingFriendship_showsPending() {
        when(esportsBackendClient.searchPlayersByCriteria(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(player(2L, "55", "PendingFriend")));
        when(blockRepository.isBlockedEitherWay(anyLong(), anyLong())).thenReturn(false);

        Friendship pending = mock(Friendship.class);
        when(pending.isAccepted()).thenReturn(false);
        when(pending.isPending()).thenReturn(true);
        when(friendshipRepository.findBetweenUsers(42L, 55L)).thenReturn(Optional.of(pending));
        when(friendshipRepository.findMutualFriendIds(42L, 55L)).thenReturn(List.of());
        when(scrimHistoryRepository.countBetweenUsers(42L, 55L)).thenReturn(0);

        PageResponse<FriendFinderResultResponse> result = service.searchPlayers(42L, null, null, null, 0, 20);

        assertThat(result.getContent().get(0).getFriendshipStatus()).isEqualTo("PENDING");
    }

    @Test
    void searchPlayers_nonNumericUserId_skipped() {
        PlayerDto badPlayer = player(99L, "not-a-number", "BadPlayer");
        when(esportsBackendClient.searchPlayersByCriteria(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(badPlayer));

        PageResponse<FriendFinderResultResponse> result = service.searchPlayers(42L, null, null, null, 0, 20);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void searchPlayers_nullPlayerInList_skipped() {
        when(esportsBackendClient.searchPlayersByCriteria(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(player(2L, "55", "Good")));
        when(blockRepository.isBlockedEitherWay(anyLong(), anyLong())).thenReturn(false);
        when(friendshipRepository.findBetweenUsers(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(friendshipRepository.findMutualFriendIds(anyLong(), anyLong())).thenReturn(List.of());
        when(scrimHistoryRepository.countBetweenUsers(anyLong(), anyLong())).thenReturn(0);

        PageResponse<FriendFinderResultResponse> result = service.searchPlayers(42L, null, null, null, 0, 20);

        // Only the valid player should appear
        assertThat(result.getContent()).hasSize(1);
    }

    // ── dismissSuggestion ────────────────────────────────────────────────

    @Test
    void dismissSuggestion_happyPath_setsDismissedTrue() {
        UUID id = UUID.randomUUID();
        FriendSuggestion suggestion = FriendSuggestion.builder()
                .id(id).userId(42L).suggestedUserId(55L).suggestionScore(70.0)
                .source("SCRIM_OPPONENT").dismissed(false).build();
        when(friendSuggestionRepository.findById(id)).thenReturn(Optional.of(suggestion));

        service.dismissSuggestion(42L, id);

        assertThat(suggestion.getDismissed()).isTrue();
        assertThat(suggestion.getDismissedAt()).isNotNull();
        verify(friendSuggestionRepository).save(suggestion);
    }

    @Test
    void dismissSuggestion_wrongUser_throwsValidationException() {
        UUID id = UUID.randomUUID();
        FriendSuggestion suggestion = FriendSuggestion.builder()
                .id(id).userId(99L).suggestedUserId(55L).suggestionScore(70.0)
                .source("SCRIM_OPPONENT").build();
        when(friendSuggestionRepository.findById(id)).thenReturn(Optional.of(suggestion));

        assertThatThrownBy(() -> service.dismissSuggestion(42L, id))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void dismissSuggestion_notFound_throwsResourceNotFoundException() {
        UUID id = UUID.randomUUID();
        when(friendSuggestionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.dismissSuggestion(42L, id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getScrimHistory ──────────────────────────────────────────────────

    @Test
    void getScrimHistory_resolvesOpponentCorrectly() {
        ScrimHistory h = ScrimHistory.builder()
                .id(UUID.randomUUID()).userIdA(42L).userIdB(55L)
                .gameMode("5v5").matchQualityScore(80.0)
                .playedAt(LocalDateTime.now()).build();
        Page<ScrimHistory> page = new PageImpl<>(List.of(h));
        when(scrimHistoryRepository.findByUserId(eq(42L), any(PageRequest.class))).thenReturn(page);

        PlayerDto opponent = player(2L, "55", "NightHawk");
        when(esportsBackendClient.getPlayerByUserId("55")).thenReturn(opponent);
        when(friendshipRepository.findBetweenUsers(42L, 55L)).thenReturn(Optional.empty());

        PageResponse<ScrimHistoryResponse> result = service.getScrimHistory(42L, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        ScrimHistoryResponse r = result.getContent().get(0);
        assertThat(r.getOpponentUserId()).isEqualTo(55L);
        assertThat(r.getOpponentGamerTag()).isEqualTo("NightHawk");
        assertThat(r.getFriendshipStatus()).isEqualTo("NONE");
    }

    @Test
    void getScrimHistory_feignFailure_defaultsGamerTagToUnknown() {
        ScrimHistory h = ScrimHistory.builder()
                .id(UUID.randomUUID()).userIdA(42L).userIdB(55L)
                .gameMode("5v5").playedAt(LocalDateTime.now()).build();
        Page<ScrimHistory> page = new PageImpl<>(List.of(h));
        when(scrimHistoryRepository.findByUserId(eq(42L), any(PageRequest.class))).thenReturn(page);
        when(esportsBackendClient.getPlayerByUserId("55")).thenThrow(new RuntimeException("timeout"));
        when(friendshipRepository.findBetweenUsers(42L, 55L)).thenReturn(Optional.empty());

        PageResponse<ScrimHistoryResponse> result = service.getScrimHistory(42L, 0, 10);

        assertThat(result.getContent().get(0).getOpponentGamerTag()).isEqualTo("Unknown");
    }

    @Test
    void getScrimHistory_userIsB_opponentIsA() {
        // When the current user is userIdB, the opponent should be userIdA
        ScrimHistory h = ScrimHistory.builder()
                .id(UUID.randomUUID()).userIdA(55L).userIdB(42L)
                .gameMode("3v3").playedAt(LocalDateTime.now()).build();
        Page<ScrimHistory> page = new PageImpl<>(List.of(h));
        when(scrimHistoryRepository.findByUserId(eq(42L), any(PageRequest.class))).thenReturn(page);

        PlayerDto opponent = player(2L, "55", "OpponentPlayer");
        when(esportsBackendClient.getPlayerByUserId("55")).thenReturn(opponent);
        when(friendshipRepository.findBetweenUsers(42L, 55L)).thenReturn(Optional.empty());

        PageResponse<ScrimHistoryResponse> result = service.getScrimHistory(42L, 0, 10);

        assertThat(result.getContent().get(0).getOpponentUserId()).isEqualTo(55L);
    }
}
