package com.ossn.content.forum.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "topic_subscriptions", 
    uniqueConstraints = @UniqueConstraint(columnNames = {"topic_id", "user_id"}),
    indexes = {
        @Index(name = "idx_topic_sub_topic", columnList = "topic_id"),
        @Index(name = "idx_topic_sub_user", columnList = "user_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "topic_id", nullable = false)
    private UUID topicId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "notify_on_reply")
    @Builder.Default
    private Boolean notifyOnReply = true;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
