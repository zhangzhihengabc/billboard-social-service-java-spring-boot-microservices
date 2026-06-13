package com.billboard.social.common.client;

import com.billboard.social.common.dto.UserSummary;

// Superseded by UserServiceClientFallbackFactory — kept only to avoid breaking
// any deserialized Spring application context caches during a rolling deploy.
// Not a @Component: must not be registered as a bean (would create an ambiguous
// second UserServiceClient bean and conflict with the Feign proxy).
public class UserServiceClientFallback implements UserServiceClient {

    @Override
    public UserSummary getUserSummary(Long userId) {
        throw new UnsupportedOperationException(
                "UserServiceClientFallback is superseded; use UserServiceClientFallbackFactory");
    }
}
