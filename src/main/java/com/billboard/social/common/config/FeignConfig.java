package com.billboard.social.common.config;

import feign.Logger;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign Client Configuration
 *
 * BASED ON COMMON-MISTAKES-SOLUTIONS.MD:
 *
 * [Section 7.2] JWT Token Propagation - passes Authorization header to downstream services
 * [Section 7.1] Error handling configuration
 *
 * Without this, inter-service calls will fail with 401 Unauthorized
 * because the JWT token won't be forwarded.
 */
@Configuration
@Slf4j
public class FeignConfig {

    /**
     * [Section 7.2] Request Interceptor for JWT Token Propagation
     *
     * This interceptor copies the Authorization header from the incoming request
     * to outgoing Feign client requests, enabling seamless authentication
     * across microservices.
     */
    @Bean
    public RequestInterceptor authorizationRequestInterceptor() {
        return requestTemplate -> {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes != null) {
                String authHeader = attributes.getRequest().getHeader("Authorization");
                if (authHeader != null && !authHeader.isEmpty()) {
                    requestTemplate.header("Authorization", authHeader);
                    log.debug("Propagating Authorization header to Feign request: {}",
                            requestTemplate.url());
                }
            } else {
                log.debug("No request context available for Feign request: {}",
                        requestTemplate.url());
            }
        };
    }

    /**
     * Additional headers that might be useful for tracing/debugging
     */
    @Bean
    public RequestInterceptor correlationIdInterceptor() {
        return requestTemplate -> {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes != null) {
                // Propagate correlation ID for distributed tracing
                String correlationId = attributes.getRequest().getHeader("X-Correlation-ID");
                if (correlationId != null) {
                    requestTemplate.header("X-Correlation-ID", correlationId);
                }

                // Propagate request ID if present
                String requestId = attributes.getRequest().getHeader("X-Request-ID");
                if (requestId != null) {
                    requestTemplate.header("X-Request-ID", requestId);
                }
            }
        };
    }

    /**
     * Feign logging level for debugging
     * Set to BASIC in production, FULL for debugging
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    /**
     * Custom error decoder (optional - can be used for more specific error handling)
     * The GlobalExceptionHandler already handles FeignException,
     * but this can provide more context if needed
     */
    @Bean
    public ErrorDecoder errorDecoder() {
        return new CustomFeignErrorDecoder();
    }

    /**
     * Custom Feign Error Decoder for enhanced logging
     */
    @Slf4j
    public static class CustomFeignErrorDecoder implements ErrorDecoder {

        private final ErrorDecoder defaultDecoder = new Default();

        @Override
        public Exception decode(String methodKey, feign.Response response) {
            log.error("Feign error - Method: {}, Status: {}, Reason: {}",
                    methodKey,
                    response.status(),
                    response.reason());

            // Let the default decoder create the appropriate FeignException
            // GlobalExceptionHandler will then handle it
            return defaultDecoder.decode(methodKey, response);
        }
    }
}