package com.billboard.social.common.client;

import com.billboard.social.common.exception.ResourceNotFoundException;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@Slf4j
public class UserServiceClientFallbackFactory implements FallbackFactory<UserServiceClient> {

    @Override
    public UserServiceClient create(Throwable cause) {
        return userId -> {
            String correlationId = resolveCorrelationId();
            log.error("[correlationId={}] SSO getUserSummary(userId={}) failed — {}: {}",
                    correlationId, userId, cause.getClass().getSimpleName(), cause.getMessage(), cause);

            if (cause instanceof FeignException.NotFound) {
                throw new ResourceNotFoundException("User not found: " + userId);
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("SSO service unavailable for userId=" + userId, cause);
        };
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
            // no request context (async / scheduled task)
        }
        return "none";
    }
}
