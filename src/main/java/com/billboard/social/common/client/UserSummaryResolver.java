package com.billboard.social.common.client;

import com.billboard.social.common.dto.UserSummary;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Safe display-enrichment wrapper for SSO user lookups.
 * On failure: logs ERROR (never fabricates username/email), returns id-only UserSummary (null username, null email).
 * Use ONLY for display enrichment — never for validation paths where a real user must exist.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UserSummaryResolver {

    private final UserServiceClient userServiceClient;

    public UserSummary resolveForDisplay(Long userId) {
        try {
            return userServiceClient.getUserSummary(userId);
        } catch (Exception e) {
            String correlationId = resolveCorrelationId();
            if (e instanceof FeignException.NotFound) {
                log.error("[correlationId={}] SSO getUserSummary(userId={}) — user not found (404): {}",
                        correlationId, userId, e.getMessage());
            } else {
                log.error("[correlationId={}] SSO getUserSummary(userId={}) transport failure — {}: {}",
                        correlationId, userId, e.getClass().getSimpleName(), e.getMessage(), e);
            }
            return UserSummary.builder().id(userId).build();
        }
    }

    private String resolveCorrelationId() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                String id = attrs.getRequest().getHeader("X-Correlation-ID");
                return id != null ? id : "none";
            }
        } catch (Exception ignored) {
        }
        return "none";
    }
}
