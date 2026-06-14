package com.billboard.social.common.client;

import com.billboard.social.common.exception.ResourceNotFoundException;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceClientFallbackFactoryTest {

    private UserServiceClientFallbackFactory factory;

    @BeforeEach
    void setUp() {
        factory = new UserServiceClientFallbackFactory();
    }

    @Nested
    @DisplayName("Transport / 5xx failure")
    class TransportFailure {

        @Test
        @DisplayName("Throws the original RuntimeException — never returns fabricated UserSummary")
        void create_runtimeCause_rethrows() {
            RuntimeException cause = new RuntimeException("connection refused");
            UserServiceClient client = factory.create(cause);

            assertThatThrownBy(() -> client.getUserSummary(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("connection refused");
        }

        @Test
        @DisplayName("Wraps checked cause as RuntimeException — still throws, never returns fabricated data")
        void create_checkedCause_wrapsAndThrows() {
            Exception cause = new Exception("timeout");
            UserServiceClient client = factory.create(cause);

            assertThatThrownBy(() -> client.getUserSummary(2L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("SSO service unavailable")
                    .hasMessageContaining("2")
                    .hasCause(cause);
        }
    }

    @Nested
    @DisplayName("404 — user genuinely not found")
    class NotFound {

        @Test
        @DisplayName("Throws ResourceNotFoundException, not a fabricated UserSummary")
        void create_notFound_throwsResourceNotFoundException() {
            FeignException.NotFound notFound = mock(FeignException.NotFound.class);
            when(notFound.getMessage()).thenReturn("404 Not Found");

            UserServiceClient client = factory.create(notFound);

            assertThatThrownBy(() -> client.getUserSummary(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    @Nested
    @DisplayName("No fabricated data — golden rule")
    class NoFabrication {

        @Test
        @DisplayName("Fallback never returns unknown@gmail.com — must always throw")
        void create_anyFailure_neverYieldsUnknownAtGmailDotCom() {
            UserServiceClient client = factory.create(new RuntimeException("SSO down"));

            // The contract: throw, never silently return { username:"unknown", email:"unknown@gmail.com" }
            assertThatThrownBy(() -> client.getUserSummary(5L))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}
