package com.billboard.social.common.client;

import com.billboard.social.common.dto.UserSummary;
import com.billboard.social.common.dto.ApiResponse;
import feign.FeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSummaryResolverTest {

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private UserSummaryResolver resolver;

    private static final Long USER_ID = 42L;

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("Returns UserSummary from SSO on success")
        void resolveForDisplay_success_returnsSummary() {
            UserSummary expected = UserSummary.builder()
                    .id(USER_ID).username("alice").email("alice@example.com").build();
            when(userServiceClient.getUserSummary(USER_ID)).thenReturn(apiResponse(expected));

            UserSummary result = resolver.resolveForDisplay(USER_ID);

            assertThat(result.getId()).isEqualTo(USER_ID);
            assertThat(result.getUsername()).isEqualTo("alice");
            assertThat(result.getEmail()).isEqualTo("alice@example.com");
        }
    }

    @Nested
    @DisplayName("Transport failure")
    class TransportFailure {

        @Test
        @DisplayName("Returns id-only UserSummary — null username, null email")
        void resolveForDisplay_transportFailure_returnsIdOnlyNoFabrication() {
            when(userServiceClient.getUserSummary(USER_ID))
                    .thenThrow(new RuntimeException("connection refused"));

            UserSummary result = resolver.resolveForDisplay(USER_ID);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(USER_ID);
            assertThat(result.getUsername()).isNull();
            assertThat(result.getEmail()).isNull();
        }

        @Test
        @DisplayName("Never returns unknown@gmail.com or fabricated username")
        void resolveForDisplay_neverFabricates() {
            when(userServiceClient.getUserSummary(USER_ID))
                    .thenThrow(new RuntimeException("SSO down"));

            UserSummary result = resolver.resolveForDisplay(USER_ID);

            assertThat(result.getEmail()).isNotEqualTo("unknown@gmail.com");
            assertThat(result.getUsername()).isNotEqualTo("Unknown");
            assertThat(result.getUsername()).isNull();
        }
    }

    @Nested
    @DisplayName("404 — user not found")
    class NotFound {

        @Test
        @DisplayName("Returns id-only UserSummary on 404 — null username, null email")
        void resolveForDisplay_notFound_returnsIdOnly() {
            FeignException.NotFound notFound = mock(FeignException.NotFound.class);
            when(notFound.getMessage()).thenReturn("404 Not Found");
            when(userServiceClient.getUserSummary(USER_ID)).thenThrow(notFound);

            UserSummary result = resolver.resolveForDisplay(USER_ID);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(USER_ID);
            assertThat(result.getUsername()).isNull();
            assertThat(result.getEmail()).isNull();
        }
    }

    private static ApiResponse<UserSummary> apiResponse(UserSummary summary) {
        ApiResponse<UserSummary> response = new ApiResponse<>();
        response.setSuccess(summary != null);
        response.setData(summary);
        return response;
    }
}
