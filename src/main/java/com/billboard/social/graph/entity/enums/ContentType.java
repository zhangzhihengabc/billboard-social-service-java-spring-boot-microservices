package com.billboard.social.graph.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ContentType {
    POST,
    COMMENT,
    PHOTO,
    VIDEO,
    ALBUM,
    EVENT,
    GROUP,
    STORY,
    ASSIGNMENT,
    COURSE_MATERIAL,
    NOTICE,
    CIRCULAR;

    @JsonCreator
    public static ContentType fromString(String value) {
        if (value == null) return null;
        try {
            return ContentType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown ContentType: " + value);
        }
    }
}
