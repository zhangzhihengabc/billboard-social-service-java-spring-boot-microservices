package com.billboard.social.graph.service;

import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.common.dto.PageResponse;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.exception.ValidationException;
import com.billboard.social.graph.dto.request.SocialRequests.PokeRequest;
import com.billboard.social.graph.dto.response.SocialResponses.PokeResponse;
import com.billboard.social.graph.entity.Poke;
import com.billboard.social.graph.event.SocialEventPublisher;
import com.billboard.social.graph.repository.BlockRepository;
import com.billboard.social.graph.repository.PokeRepository;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PokeServiceTest {

    @Mock
    private PokeRepository pokeRepository;

    @Mock
    private BlockRepository blockRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private SocialEventPublisher eventPublisher;

    @InjectMocks
    private PokeService pokeService;

    // Test constants
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TARGET_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID POKE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private Poke testPoke;
    private UserSummary testUserSummary;

    @BeforeEach
    void setUp() {
        testPoke = Poke.builder()
                .id(POKE_ID)
                .pokerId(USER_ID)
                .pokedId(TARGET_USER_ID)
                .isActive(true)
                .pokeCount(1)
                .build();
        testPoke.setCreatedAt(LocalDateTime.now());

        testUserSummary = UserSummary.builder()
                .id(USER_ID)
                .username("testuser")
                .displayName("Test User")
                .avatarUrl("https://example.com/avatar.jpg")
                .isVerified(false)
                .build();
    }

    // ==================== POKE ====================

    @Nested
    @DisplayName("poke")
    class PokeTests {

        @Test
        @DisplayName("Success - creates new poke")
        void poke_Success_CreatesNewPoke() {
            PokeRequest request = PokeRequest.builder()
                    .userId(TARGET_USER_ID)
                    .build();

            when(blockRepository.isBlockedEitherWay(USER_ID, TARGET_USER_ID)).thenReturn(false);
            when(pokeRepository.findByPokerIdAndPokedId(USER_ID, TARGET_USER_ID)).thenReturn(Optional.empty());
            when(pokeRepository.save(any(Poke.class))).thenAnswer(invocation -> {
                Poke saved = invocation.getArgument(0);
                saved.setId(POKE_ID);
                saved.setCreatedAt(LocalDateTime.now());
                return saved;
            });
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            PokeResponse response = pokeService.poke(USER_ID, request);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(POKE_ID);
            assertThat(response.getPokerId()).isEqualTo(USER_ID);
            assertThat(response.getPokedId()).isEqualTo(TARGET_USER_ID);
            assertThat(response.getIsActive()).isTrue();
            assertThat(response.getPokeCount()).isEqualTo(1);
            verify(eventPublisher).publishUserPoked(any(Poke.class));
        }

        @Test
        @DisplayName("Success - increments existing poke count")
        void poke_Success_IncrementsExistingPoke() {
            PokeRequest request = PokeRequest.builder()
                    .userId(TARGET_USER_ID)
                    .build();

            Poke existingPoke = Poke.builder()
                    .id(POKE_ID)
                    .pokerId(USER_ID)
                    .pokedId(TARGET_USER_ID)
                    .isActive(true)
                    .pokeCount(2)
                    .build();
            existingPoke.setCreatedAt(LocalDateTime.now());

            when(blockRepository.isBlockedEitherWay(USER_ID, TARGET_USER_ID)).thenReturn(false);
            when(pokeRepository.findByPokerIdAndPokedId(USER_ID, TARGET_USER_ID)).thenReturn(Optional.of(existingPoke));
            when(pokeRepository.save(any(Poke.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            PokeResponse response = pokeService.poke(USER_ID, request);

            assertThat(response).isNotNull();
            assertThat(response.getPokeCount()).isEqualTo(3); // Incremented from 2 to 3
            verify(eventPublisher, never()).publishUserPoked(any()); // No event for increment
        }

        @Test
        @DisplayName("Null userId - throws ValidationException")
        void poke_NullUserId() {
            PokeRequest request = PokeRequest.builder().build();

            assertThatThrownBy(() -> pokeService.poke(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("User ID is required");

            verifyNoInteractions(pokeRepository);
        }

        @Test
        @DisplayName("Cannot poke yourself - throws ValidationException")
        void poke_CannotPokeYourself() {
            PokeRequest request = PokeRequest.builder()
                    .userId(USER_ID)
                    .build();

            assertThatThrownBy(() -> pokeService.poke(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Cannot poke yourself");

            verifyNoInteractions(pokeRepository);
        }

        @Test
        @DisplayName("Blocked either way - throws ValidationException")
        void poke_BlockedEitherWay() {
            PokeRequest request = PokeRequest.builder()
                    .userId(TARGET_USER_ID)
                    .build();

            when(blockRepository.isBlockedEitherWay(USER_ID, TARGET_USER_ID)).thenReturn(true);

            assertThatThrownBy(() -> pokeService.poke(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Cannot poke this user");

            verify(pokeRepository, never()).save(any());
        }

        @Test
        @DisplayName("Race condition - DataIntegrityViolationException")
        void poke_RaceCondition() {
            PokeRequest request = PokeRequest.builder()
                    .userId(TARGET_USER_ID)
                    .build();

            when(blockRepository.isBlockedEitherWay(USER_ID, TARGET_USER_ID)).thenReturn(false);
            when(pokeRepository.findByPokerIdAndPokedId(USER_ID, TARGET_USER_ID)).thenReturn(Optional.empty());
            when(pokeRepository.save(any(Poke.class)))
                    .thenThrow(new DataIntegrityViolationException("Duplicate key"));

            assertThatThrownBy(() -> pokeService.poke(USER_ID, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Poke already exists");

            verify(eventPublisher, never()).publishUserPoked(any());
        }
    }

    // ==================== POKE BACK ====================

    @Nested
    @DisplayName("pokeBack")
    class PokeBackTests {

        @Test
        @DisplayName("Success - pokes back and creates new poke")
        void pokeBack_Success() {
            // USER_ID is the poked user, TARGET_USER_ID is the poker
            Poke originalPoke = Poke.builder()
                    .id(POKE_ID)
                    .pokerId(TARGET_USER_ID)
                    .pokedId(USER_ID)
                    .isActive(true)
                    .pokeCount(1)
                    .build();
            originalPoke.setCreatedAt(LocalDateTime.now());

            when(pokeRepository.findById(POKE_ID)).thenReturn(Optional.of(originalPoke));
            when(pokeRepository.save(any(Poke.class))).thenAnswer(invocation -> {
                Poke saved = invocation.getArgument(0);
                if (saved.getId() == null) {
                    saved.setId(UUID.randomUUID());
                    saved.setCreatedAt(LocalDateTime.now());
                }
                return saved;
            });

            UserSummary pokerSummary = UserSummary.builder()
                    .id(TARGET_USER_ID)
                    .username("poker")
                    .build();
            when(userServiceClient.getUserSummary(TARGET_USER_ID)).thenReturn(pokerSummary);

            PokeResponse response = pokeService.pokeBack(USER_ID, POKE_ID);

            assertThat(response).isNotNull();
            assertThat(response.getPokedBackAt()).isNotNull();
            verify(eventPublisher).publishUserPoked(any(Poke.class));
        }

        @Test
        @DisplayName("Success - race condition, increments existing poke")
        void pokeBack_RaceCondition_IncrementsExisting() {
            Poke originalPoke = Poke.builder()
                    .id(POKE_ID)
                    .pokerId(TARGET_USER_ID)
                    .pokedId(USER_ID)
                    .isActive(true)
                    .pokeCount(1)
                    .build();
            originalPoke.setCreatedAt(LocalDateTime.now());

            Poke existingReversePoke = Poke.builder()
                    .id(UUID.randomUUID())
                    .pokerId(USER_ID)
                    .pokedId(TARGET_USER_ID)
                    .isActive(true)
                    .pokeCount(1)
                    .build();
            existingReversePoke.setCreatedAt(LocalDateTime.now());

            when(pokeRepository.findById(POKE_ID)).thenReturn(Optional.of(originalPoke));
            when(pokeRepository.save(any(Poke.class))).thenAnswer(invocation -> {
                Poke saved = invocation.getArgument(0);
                // First save is the original poke update
                if (saved.getId() != null && saved.getId().equals(POKE_ID)) {
                    return saved;
                }
                // Second save throws for new poke
                if (saved.getId() == null) {
                    throw new DataIntegrityViolationException("Duplicate key");
                }
                return saved;
            });
            when(pokeRepository.findByPokerIdAndPokedId(USER_ID, TARGET_USER_ID))
                    .thenReturn(Optional.of(existingReversePoke));

            UserSummary pokerSummary = UserSummary.builder()
                    .id(TARGET_USER_ID)
                    .username("poker")
                    .build();
            when(userServiceClient.getUserSummary(TARGET_USER_ID)).thenReturn(pokerSummary);

            PokeResponse response = pokeService.pokeBack(USER_ID, POKE_ID);

            assertThat(response).isNotNull();
            verify(eventPublisher).publishUserPoked(any(Poke.class));
        }

        @Test
        @DisplayName("Race condition - existing poke not found, throws ValidationException")
        void pokeBack_RaceCondition_ExistingNotFound() {
            Poke originalPoke = Poke.builder()
                    .id(POKE_ID)
                    .pokerId(TARGET_USER_ID)
                    .pokedId(USER_ID)
                    .isActive(true)
                    .pokeCount(1)
                    .build();
            originalPoke.setCreatedAt(LocalDateTime.now());

            when(pokeRepository.findById(POKE_ID)).thenReturn(Optional.of(originalPoke));
            when(pokeRepository.save(any(Poke.class))).thenAnswer(invocation -> {
                Poke saved = invocation.getArgument(0);
                if (saved.getId() != null && saved.getId().equals(POKE_ID)) {
                    return saved;
                }
                throw new DataIntegrityViolationException("Duplicate key");
            });
            when(pokeRepository.findByPokerIdAndPokedId(USER_ID, TARGET_USER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> pokeService.pokeBack(USER_ID, POKE_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Failed to create poke back");
        }

        @Test
        @DisplayName("Poke not found - throws ValidationException")
        void pokeBack_PokeNotFound() {
            when(pokeRepository.findById(POKE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pokeService.pokeBack(USER_ID, POKE_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Poke not found with id: " + POKE_ID);
        }

        @Test
        @DisplayName("Not the poked user - throws ValidationException")
        void pokeBack_NotThePokedUser() {
            // USER_ID is NOT the poked user
            Poke poke = Poke.builder()
                    .id(POKE_ID)
                    .pokerId(USER_ID)
                    .pokedId(TARGET_USER_ID)  // Different user was poked
                    .isActive(true)
                    .build();

            when(pokeRepository.findById(POKE_ID)).thenReturn(Optional.of(poke));

            assertThatThrownBy(() -> pokeService.pokeBack(USER_ID, POKE_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Cannot poke back on this poke");
        }

        @Test
        @DisplayName("Poke not active - throws ValidationException")
        void pokeBack_PokeNotActive() {
            Poke inactivePoke = Poke.builder()
                    .id(POKE_ID)
                    .pokerId(TARGET_USER_ID)
                    .pokedId(USER_ID)
                    .isActive(false)  // Not active
                    .build();

            when(pokeRepository.findById(POKE_ID)).thenReturn(Optional.of(inactivePoke));

            assertThatThrownBy(() -> pokeService.pokeBack(USER_ID, POKE_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("This poke is no longer active");
        }
    }

    // ==================== GET RECEIVED POKES ====================

    @Nested
    @DisplayName("getReceivedPokes")
    class GetReceivedPokesTests {

        @Test
        @DisplayName("Success - returns paginated received pokes")
        void getReceivedPokes_Success() {
            Page<Poke> page = new PageImpl<>(
                    List.of(testPoke),
                    PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")),
                    1
            );

            when(pokeRepository.findActivePokesForUser(eq(USER_ID), any(Pageable.class))).thenReturn(page);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            PageResponse<PokeResponse> response = pokeService.getReceivedPokes(USER_ID, 0, 20);

            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("Success - empty list")
        void getReceivedPokes_Empty() {
            Page<Poke> emptyPage = new PageImpl<>(
                    Collections.emptyList(),
                    PageRequest.of(0, 20),
                    0
            );

            when(pokeRepository.findActivePokesForUser(eq(USER_ID), any(Pageable.class))).thenReturn(emptyPage);

            PageResponse<PokeResponse> response = pokeService.getReceivedPokes(USER_ID, 0, 20);

            assertThat(response.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Success - custom pagination")
        void getReceivedPokes_CustomPagination() {
            Page<Poke> page = new PageImpl<>(
                    Collections.emptyList(),
                    PageRequest.of(5, 50),
                    0
            );

            when(pokeRepository.findActivePokesForUser(eq(USER_ID), any(Pageable.class))).thenReturn(page);

            PageResponse<PokeResponse> response = pokeService.getReceivedPokes(USER_ID, 5, 50);

            assertThat(response.getPage()).isEqualTo(5);
            assertThat(response.getSize()).isEqualTo(50);
        }
    }

    // ==================== GET SENT POKES ====================

    @Nested
    @DisplayName("getSentPokes")
    class GetSentPokesTests {

        @Test
        @DisplayName("Success - returns paginated sent pokes")
        void getSentPokes_Success() {
            Page<Poke> page = new PageImpl<>(
                    List.of(testPoke),
                    PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")),
                    1
            );

            when(pokeRepository.findPokesSentByUser(eq(USER_ID), any(Pageable.class))).thenReturn(page);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            PageResponse<PokeResponse> response = pokeService.getSentPokes(USER_ID, 0, 20);

            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getContent().get(0).getPokerId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("Success - empty list")
        void getSentPokes_Empty() {
            Page<Poke> emptyPage = new PageImpl<>(
                    Collections.emptyList(),
                    PageRequest.of(0, 20),
                    0
            );

            when(pokeRepository.findPokesSentByUser(eq(USER_ID), any(Pageable.class))).thenReturn(emptyPage);

            PageResponse<PokeResponse> response = pokeService.getSentPokes(USER_ID, 0, 20);

            assertThat(response.getContent()).isEmpty();
        }
    }

    // ==================== GET ACTIVE POKES COUNT ====================

    @Nested
    @DisplayName("getActivePokesCount")
    class GetActivePokesCountTests {

        @Test
        @DisplayName("Success - returns count")
        void getActivePokesCount_Success() {
            when(pokeRepository.countByPokedIdAndIsActiveTrue(USER_ID)).thenReturn(5L);

            long result = pokeService.getActivePokesCount(USER_ID);

            assertThat(result).isEqualTo(5L);
        }

        @Test
        @DisplayName("Success - returns zero")
        void getActivePokesCount_Zero() {
            when(pokeRepository.countByPokedIdAndIsActiveTrue(USER_ID)).thenReturn(0L);

            long result = pokeService.getActivePokesCount(USER_ID);

            assertThat(result).isEqualTo(0L);
        }
    }

    // ==================== DISMISS POKE ====================

    @Nested
    @DisplayName("dismissPoke")
    class DismissPokeTests {

        @Test
        @DisplayName("Success - dismisses poke")
        void dismissPoke_Success() {
            Poke activePoke = Poke.builder()
                    .id(POKE_ID)
                    .pokerId(TARGET_USER_ID)
                    .pokedId(USER_ID)
                    .isActive(true)
                    .build();

            when(pokeRepository.findById(POKE_ID)).thenReturn(Optional.of(activePoke));
            when(pokeRepository.save(any(Poke.class))).thenReturn(activePoke);

            pokeService.dismissPoke(USER_ID, POKE_ID);

            verify(pokeRepository).save(argThat(poke -> !poke.getIsActive()));
        }

        @Test
        @DisplayName("Poke not found - throws ValidationException")
        void dismissPoke_PokeNotFound() {
            when(pokeRepository.findById(POKE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pokeService.dismissPoke(USER_ID, POKE_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Poke not found with id: " + POKE_ID);
        }

        @Test
        @DisplayName("Not the poked user - throws ValidationException")
        void dismissPoke_NotThePokedUser() {
            Poke poke = Poke.builder()
                    .id(POKE_ID)
                    .pokerId(USER_ID)
                    .pokedId(TARGET_USER_ID)  // Different user was poked
                    .isActive(true)
                    .build();

            when(pokeRepository.findById(POKE_ID)).thenReturn(Optional.of(poke));

            assertThatThrownBy(() -> pokeService.dismissPoke(USER_ID, POKE_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Cannot dismiss this poke");
        }

        @Test
        @DisplayName("Already dismissed - throws ValidationException")
        void dismissPoke_AlreadyDismissed() {
            Poke inactivePoke = Poke.builder()
                    .id(POKE_ID)
                    .pokerId(TARGET_USER_ID)
                    .pokedId(USER_ID)
                    .isActive(false)  // Already dismissed
                    .build();

            when(pokeRepository.findById(POKE_ID)).thenReturn(Optional.of(inactivePoke));

            assertThatThrownBy(() -> pokeService.dismissPoke(USER_ID, POKE_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Poke is already dismissed");
        }
    }

    // ==================== FETCH USER SUMMARY WITH FALLBACK ====================

    @Nested
    @DisplayName("fetchUserSummaryWithFallback (via getReceivedPokes)")
    class FetchUserSummaryWithFallbackTests {

        private void setupPokesPage() {
            Page<Poke> page = new PageImpl<>(List.of(testPoke), PageRequest.of(0, 20), 1);
            when(pokeRepository.findActivePokesForUser(eq(USER_ID), any(Pageable.class))).thenReturn(page);
        }

        @Test
        @DisplayName("Success - returns user summary")
        void fetchUserSummaryWithFallback_Success() {
            setupPokesPage();
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            PageResponse<PokeResponse> response = pokeService.getReceivedPokes(USER_ID, 0, 20);

            assertThat(response.getContent().get(0).getPoker()).isNotNull();
            assertThat(response.getContent().get(0).getPoker().getUsername()).isEqualTo("testuser");
            assertThat(response.getContent().get(0).getPoker().getDisplayName()).isEqualTo("Test User");
        }

        @Test
        @DisplayName("Returns null - uses fallback")
        void fetchUserSummaryWithFallback_ReturnsNull() {
            setupPokesPage();
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(null);

            PageResponse<PokeResponse> response = pokeService.getReceivedPokes(USER_ID, 0, 20);

            assertThat(response.getContent().get(0).getPoker()).isNotNull();
            assertThat(response.getContent().get(0).getPoker().getId()).isEqualTo(USER_ID);
            assertThat(response.getContent().get(0).getPoker().getUsername()).isEqualTo("Unknown");
            assertThat(response.getContent().get(0).getPoker().getDisplayName()).isEqualTo("Unknown User");
            assertThat(response.getContent().get(0).getPoker().getAvatarUrl()).isNull();
            assertThat(response.getContent().get(0).getPoker().getIsVerified()).isFalse();
        }

        @Test
        @DisplayName("FeignException.NotFound - uses fallback")
        void fetchUserSummaryWithFallback_FeignNotFound() {
            setupPokesPage();
            Request feignRequest = Request.create(Request.HttpMethod.GET, "/users",
                    Collections.emptyMap(), null, new RequestTemplate());
            when(userServiceClient.getUserSummary(USER_ID))
                    .thenThrow(new FeignException.NotFound("Not found", feignRequest, null, null));

            PageResponse<PokeResponse> response = pokeService.getReceivedPokes(USER_ID, 0, 20);

            assertThat(response.getContent().get(0).getPoker().getUsername()).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("FeignException (other) - uses fallback")
        void fetchUserSummaryWithFallback_FeignOther() {
            setupPokesPage();
            Request feignRequest = Request.create(Request.HttpMethod.GET, "/users",
                    Collections.emptyMap(), null, new RequestTemplate());
            when(userServiceClient.getUserSummary(USER_ID))
                    .thenThrow(new FeignException.ServiceUnavailable("Service unavailable", feignRequest, null, null));

            PageResponse<PokeResponse> response = pokeService.getReceivedPokes(USER_ID, 0, 20);

            assertThat(response.getContent().get(0).getPoker().getUsername()).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("Generic Exception - uses fallback")
        void fetchUserSummaryWithFallback_GenericException() {
            setupPokesPage();
            when(userServiceClient.getUserSummary(USER_ID))
                    .thenThrow(new RuntimeException("Connection failed"));

            PageResponse<PokeResponse> response = pokeService.getReceivedPokes(USER_ID, 0, 20);

            assertThat(response.getContent().get(0).getPoker().getUsername()).isEqualTo("Unknown");
        }
    }

    // ==================== MAP TO POKE RESPONSE ====================

    @Nested
    @DisplayName("mapToPokeResponse")
    class MapToPokeResponseTests {

        @Test
        @DisplayName("Maps all fields correctly")
        void mapToPokeResponse_AllFields() {
            testPoke.setPokedBackAt(LocalDateTime.now());

            Page<Poke> page = new PageImpl<>(List.of(testPoke), PageRequest.of(0, 20), 1);
            when(pokeRepository.findActivePokesForUser(eq(USER_ID), any(Pageable.class))).thenReturn(page);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            PageResponse<PokeResponse> response = pokeService.getReceivedPokes(USER_ID, 0, 20);

            PokeResponse pokeResponse = response.getContent().get(0);
            assertThat(pokeResponse.getId()).isEqualTo(POKE_ID);
            assertThat(pokeResponse.getPokerId()).isEqualTo(USER_ID);
            assertThat(pokeResponse.getPokedId()).isEqualTo(TARGET_USER_ID);
            assertThat(pokeResponse.getIsActive()).isTrue();
            assertThat(pokeResponse.getPokeCount()).isEqualTo(1);
            assertThat(pokeResponse.getPokedBackAt()).isNotNull();
            assertThat(pokeResponse.getCreatedAt()).isNotNull();
            assertThat(pokeResponse.getPoker()).isNotNull();
        }

        @Test
        @DisplayName("Maps with null pokedBackAt")
        void mapToPokeResponse_NullPokedBackAt() {
            testPoke.setPokedBackAt(null);

            Page<Poke> page = new PageImpl<>(List.of(testPoke), PageRequest.of(0, 20), 1);
            when(pokeRepository.findActivePokesForUser(eq(USER_ID), any(Pageable.class))).thenReturn(page);
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(testUserSummary);

            PageResponse<PokeResponse> response = pokeService.getReceivedPokes(USER_ID, 0, 20);

            assertThat(response.getContent().get(0).getPokedBackAt()).isNull();
        }
    }
}