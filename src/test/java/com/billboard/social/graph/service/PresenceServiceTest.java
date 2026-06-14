package com.billboard.social.graph.service;

import com.billboard.social.common.client.UserServiceClient;
import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.dto.ApiResponse;
import com.billboard.social.graph.dto.response.SocialResponses.FriendPresenceResponse;
import com.billboard.social.graph.repository.FriendshipRepository;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PresenceServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private PresenceService presenceService;

    private static final Long USER_ID    = 1L;
    private static final Long FRIEND_ID_1 = 2L;
    private static final Long FRIEND_ID_2 = 3L;
    private static final long TTL_SECONDS = 60L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(presenceService, "presenceTtlSeconds", TTL_SECONDS);
    }

    // ==================== markOnline ====================

    @Nested
    @DisplayName("markOnline")
    class MarkOnlineTests {

        @BeforeEach
        void setUp() {
            // opsForValue() is only called inside markOnline(); scoping this stub
            // here prevents UnnecessaryStubbingException in isOnline/getOnlineFriends tests.
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        }

        @Test
        @DisplayName("Sets presence key in Redis with correct TTL")
        void markOnline_SetsKeyWithTtl() {
            presenceService.markOnline(USER_ID);

            verify(valueOperations).set("presence:" + USER_ID, "ONLINE", TTL_SECONDS, TimeUnit.SECONDS);
        }

        @Test
        @DisplayName("Uses user-specific key so different users do not collide")
        void markOnline_UsesUserSpecificKey() {
            presenceService.markOnline(FRIEND_ID_1);

            verify(valueOperations).set(eq("presence:" + FRIEND_ID_1), anyString(), anyLong(), any(TimeUnit.class));
            verify(valueOperations, never()).set(eq("presence:" + USER_ID), anyString(), anyLong(), any(TimeUnit.class));
        }
    }

    // ==================== isOnline ====================

    @Nested
    @DisplayName("isOnline")
    class IsOnlineTests {

        @Test
        @DisplayName("Returns true when presence key exists in Redis")
        void isOnline_KeyExists_ReturnsTrue() {
            when(redisTemplate.hasKey("presence:" + USER_ID)).thenReturn(Boolean.TRUE);

            assertThat(presenceService.isOnline(USER_ID)).isTrue();
        }

        @Test
        @DisplayName("Returns false when presence key does not exist in Redis")
        void isOnline_KeyAbsent_ReturnsFalse() {
            when(redisTemplate.hasKey("presence:" + USER_ID)).thenReturn(Boolean.FALSE);

            assertThat(presenceService.isOnline(USER_ID)).isFalse();
        }

        @Test
        @DisplayName("Returns false when Redis returns null (key expired during check)")
        void isOnline_NullFromRedis_ReturnsFalse() {
            when(redisTemplate.hasKey("presence:" + USER_ID)).thenReturn(null);

            assertThat(presenceService.isOnline(USER_ID)).isFalse();
        }
    }

    // ==================== getOnlineFriends ====================

    @Nested
    @DisplayName("getOnlineFriends")
    class GetOnlineFriendsTests {

        @Test
        @DisplayName("Returns empty list when user has no accepted friends")
        void getOnlineFriends_NoFriends_ReturnsEmptyList() {
            when(friendshipRepository.findFriendIds(USER_ID)).thenReturn(Collections.emptyList());

            List<FriendPresenceResponse> result = presenceService.getOnlineFriends(USER_ID);

            assertThat(result).isEmpty();
            verifyNoInteractions(userServiceClient);
        }

        @Test
        @DisplayName("Returns only online friends when some friends are offline")
        void getOnlineFriends_MixedPresence_ReturnsOnlineOnly() {
            when(friendshipRepository.findFriendIds(USER_ID)).thenReturn(List.of(FRIEND_ID_1, FRIEND_ID_2));
            when(redisTemplate.hasKey("presence:" + FRIEND_ID_1)).thenReturn(Boolean.TRUE);
            when(redisTemplate.hasKey("presence:" + FRIEND_ID_2)).thenReturn(Boolean.FALSE);
            when(userServiceClient.getUserSummary(FRIEND_ID_1)).thenReturn(
                    apiResponse(UserSummary.builder().id(FRIEND_ID_1).username("alice").email("alice@test.com").build()));

            List<FriendPresenceResponse> result = presenceService.getOnlineFriends(USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUserId()).isEqualTo(FRIEND_ID_1);
            assertThat(result.get(0).getUsername()).isEqualTo("alice");
            assertThat(result.get(0).isOnline()).isTrue();
            verify(userServiceClient, never()).getUserSummary(FRIEND_ID_2);
        }

        @Test
        @DisplayName("Returns all friends when all are online")
        void getOnlineFriends_AllOnline_ReturnsAll() {
            when(friendshipRepository.findFriendIds(USER_ID)).thenReturn(List.of(FRIEND_ID_1, FRIEND_ID_2));
            when(redisTemplate.hasKey("presence:" + FRIEND_ID_1)).thenReturn(Boolean.TRUE);
            when(redisTemplate.hasKey("presence:" + FRIEND_ID_2)).thenReturn(Boolean.TRUE);
            when(userServiceClient.getUserSummary(FRIEND_ID_1)).thenReturn(
                    apiResponse(UserSummary.builder().id(FRIEND_ID_1).username("alice").email("alice@test.com").build()));
            when(userServiceClient.getUserSummary(FRIEND_ID_2)).thenReturn(
                    apiResponse(UserSummary.builder().id(FRIEND_ID_2).username("bob").email("bob@test.com").build()));

            List<FriendPresenceResponse> result = presenceService.getOnlineFriends(USER_ID);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(FriendPresenceResponse::getUsername)
                    .containsExactlyInAnyOrder("alice", "bob");
        }

        @Test
        @DisplayName("Returns empty list when all friends are offline")
        void getOnlineFriends_AllOffline_ReturnsEmptyList() {
            when(friendshipRepository.findFriendIds(USER_ID)).thenReturn(List.of(FRIEND_ID_1, FRIEND_ID_2));
            when(redisTemplate.hasKey("presence:" + FRIEND_ID_1)).thenReturn(Boolean.FALSE);
            when(redisTemplate.hasKey("presence:" + FRIEND_ID_2)).thenReturn(Boolean.FALSE);

            List<FriendPresenceResponse> result = presenceService.getOnlineFriends(USER_ID);

            assertThat(result).isEmpty();
            verifyNoInteractions(userServiceClient);
        }

        @Test
        @DisplayName("SSO 5xx — throws, never returns unknown@gmail.com")
        void getOnlineFriends_IdentityServiceDown_Throws() {
            when(friendshipRepository.findFriendIds(USER_ID)).thenReturn(List.of(FRIEND_ID_1));
            when(redisTemplate.hasKey("presence:" + FRIEND_ID_1)).thenReturn(Boolean.TRUE);

            Request dummyRequest = Request.create(
                    Request.HttpMethod.GET, "/api/v1/auth/2/summary",
                    Map.of(), null, new RequestTemplate());
            when(userServiceClient.getUserSummary(FRIEND_ID_1))
                    .thenThrow(new FeignException.ServiceUnavailable("Service unavailable", dummyRequest, null, null));

            assertThatThrownBy(() -> presenceService.getOnlineFriends(USER_ID))
                    .isInstanceOf(FeignException.class);
        }

        @Test
        @DisplayName("SSO 404 — throws, never returns unknown@gmail.com")
        void getOnlineFriends_UserNotFoundInIdentityService_Throws() {
            when(friendshipRepository.findFriendIds(USER_ID)).thenReturn(List.of(FRIEND_ID_1));
            when(redisTemplate.hasKey("presence:" + FRIEND_ID_1)).thenReturn(Boolean.TRUE);

            Request dummyRequest = Request.create(
                    Request.HttpMethod.GET, "/api/v1/auth/2/summary",
                    Map.of(), null, new RequestTemplate());
            when(userServiceClient.getUserSummary(FRIEND_ID_1))
                    .thenThrow(new FeignException.NotFound("Not found", dummyRequest, null, null));

            assertThatThrownBy(() -> presenceService.getOnlineFriends(USER_ID))
                    .isInstanceOf(FeignException.NotFound.class);
        }

        @Test
        @DisplayName("SSO returns null — username is null, no fake email")
        void getOnlineFriends_NullUserSummary_UsernameNull() {
            when(friendshipRepository.findFriendIds(USER_ID)).thenReturn(List.of(FRIEND_ID_1));
            when(redisTemplate.hasKey("presence:" + FRIEND_ID_1)).thenReturn(Boolean.TRUE);
            when(userServiceClient.getUserSummary(FRIEND_ID_1)).thenReturn(apiResponse(null));

            List<FriendPresenceResponse> result = presenceService.getOnlineFriends(USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUsername()).isNull();
        }

        @Test
        @DisplayName("Any failing SSO lookup causes the whole presence call to throw")
        void getOnlineFriends_OneLookupFails_WholeCallThrows() {
            when(friendshipRepository.findFriendIds(USER_ID)).thenReturn(List.of(FRIEND_ID_1));
            when(redisTemplate.hasKey("presence:" + FRIEND_ID_1)).thenReturn(Boolean.TRUE);

            Request dummyRequest = Request.create(
                    Request.HttpMethod.GET, "/api/v1/auth/2/summary",
                    Map.of(), null, new RequestTemplate());
            when(userServiceClient.getUserSummary(FRIEND_ID_1))
                    .thenThrow(new FeignException.NotFound("Not found", dummyRequest, null, null));

            assertThatThrownBy(() -> presenceService.getOnlineFriends(USER_ID))
                    .isInstanceOf(FeignException.NotFound.class);
        }
    }

    private static ApiResponse<UserSummary> apiResponse(UserSummary summary) {
        ApiResponse<UserSummary> response = new ApiResponse<>();
        response.setSuccess(summary != null);
        response.setData(summary);
        return response;
    }
}