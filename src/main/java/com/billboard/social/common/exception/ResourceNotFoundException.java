package com.billboard.social.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceType;
    private final Object resourceId;

    public ResourceNotFoundException(String message) {
        super(message);
        this.resourceType = null;
        this.resourceId = null;
    }

    // For Long IDs (user IDs)
    public ResourceNotFoundException(String resourceType, String fieldName, Long resourceId) {
        super(String.format("%s not found with %s: %s", resourceType, fieldName, resourceId));
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    // For UUID IDs (entity IDs like Group, Event, etc.)
    public ResourceNotFoundException(String resourceType, String fieldName, UUID resourceId) {
        super(String.format("%s not found with %s: %s", resourceType, fieldName, resourceId));
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public Object getResourceId() {
        return resourceId;
    }
}