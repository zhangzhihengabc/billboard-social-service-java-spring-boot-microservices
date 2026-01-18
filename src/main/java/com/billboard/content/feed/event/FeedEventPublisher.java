package com.billboard.content.feed.event;

import com.billboard.content.feed.entity.Comment;
import com.billboard.content.feed.entity.Post;
import com.billboard.content.feed.entity.enums.ReactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class FeedEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange:feed-events}")
    private String exchange;

    public void publishPostCreated(Post post) {
        Map<String, Object> event = createEvent("POST_CREATED");
        event.put("postId", post.getId().toString());
        event.put("authorId", post.getAuthorId().toString());
        event.put("postType", post.getPostType().name());
        event.put("visibility", post.getVisibility().name());
        if (post.getGroupId() != null) {
            event.put("groupId", post.getGroupId().toString());
        }
        publish("feed.post.created", event);
    }

    public void publishPostDeleted(Post post) {
        Map<String, Object> event = createEvent("POST_DELETED");
        event.put("postId", post.getId().toString());
        event.put("authorId", post.getAuthorId().toString());
        publish("feed.post.deleted", event);
    }

    public void publishPostShared(Post sharedPost, Post originalPost) {
        Map<String, Object> event = createEvent("POST_SHARED");
        event.put("sharedPostId", sharedPost.getId().toString());
        event.put("originalPostId", originalPost.getId().toString());
        event.put("sharerId", sharedPost.getAuthorId().toString());
        event.put("originalAuthorId", originalPost.getAuthorId().toString());
        publish("feed.post.shared", event);
    }

    public void publishCommentCreated(Comment comment) {
        Map<String, Object> event = createEvent("COMMENT_CREATED");
        event.put("commentId", comment.getId().toString());
        event.put("postId", comment.getPost().getId().toString());
        event.put("authorId", comment.getAuthorId().toString());
        event.put("postAuthorId", comment.getPost().getAuthorId().toString());
        if (comment.getParent() != null) {
            event.put("parentId", comment.getParent().getId().toString());
            event.put("parentAuthorId", comment.getParent().getAuthorId().toString());
        }
        publish("feed.comment.created", event);
    }

    public void publishCommentDeleted(Comment comment) {
        Map<String, Object> event = createEvent("COMMENT_DELETED");
        event.put("commentId", comment.getId().toString());
        event.put("postId", comment.getPost().getId().toString());
        publish("feed.comment.deleted", event);
    }

    public void publishReactionAdded(Post post, UUID userId, ReactionType reactionType) {
        Map<String, Object> event = createEvent("REACTION_ADDED");
        event.put("postId", post.getId().toString());
        event.put("userId", userId.toString());
        event.put("postAuthorId", post.getAuthorId().toString());
        event.put("reactionType", reactionType.name());
        publish("feed.reaction.added", event);
    }

    public void publishMentioned(Post post, UUID mentionedUserId) {
        Map<String, Object> event = createEvent("USER_MENTIONED");
        event.put("postId", post.getId().toString());
        event.put("authorId", post.getAuthorId().toString());
        event.put("mentionedUserId", mentionedUserId.toString());
        publish("feed.user.mentioned", event);
    }

    private Map<String, Object> createEvent(String eventType) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", eventType);
        event.put("timestamp", LocalDateTime.now().toString());
        event.put("source", "feed-service");
        return event;
    }

    private void publish(String routingKey, Map<String, Object> event) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, event);
            log.debug("Published event: {} to {}", event.get("eventType"), routingKey);
        } catch (Exception e) {
            log.error("Failed to publish event: {}", e.getMessage());
        }
    }
}
