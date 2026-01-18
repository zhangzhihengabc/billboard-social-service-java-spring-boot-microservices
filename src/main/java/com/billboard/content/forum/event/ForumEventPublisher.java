package com.billboard.content.forum.event;

import com.billboard.content.forum.entity.Forum;
import com.billboard.content.forum.entity.Post;
import com.billboard.content.forum.entity.Topic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ForumEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange:forum-events}")
    private String exchange;

    public void publishForumCreated(Forum forum) {
        Map<String, Object> event = createEvent("FORUM_CREATED");
        event.put("forumId", forum.getId().toString());
        event.put("name", forum.getName());
        event.put("slug", forum.getSlug());
        publish("forum.created", event);
    }

    public void publishForumDeleted(Forum forum) {
        Map<String, Object> event = createEvent("FORUM_DELETED");
        event.put("forumId", forum.getId().toString());
        publish("forum.deleted", event);
    }

    public void publishTopicCreated(Topic topic) {
        Map<String, Object> event = createEvent("TOPIC_CREATED");
        event.put("topicId", topic.getId().toString());
        event.put("forumId", topic.getForum().getId().toString());
        event.put("authorId", topic.getAuthorId().toString());
        event.put("title", topic.getTitle());
        publish("forum.topic.created", event);
    }

    public void publishTopicDeleted(Topic topic) {
        Map<String, Object> event = createEvent("TOPIC_DELETED");
        event.put("topicId", topic.getId().toString());
        event.put("forumId", topic.getForum().getId().toString());
        publish("forum.topic.deleted", event);
    }

    public void publishPostCreated(Post post) {
        Map<String, Object> event = createEvent("POST_CREATED");
        event.put("postId", post.getId().toString());
        event.put("topicId", post.getTopic().getId().toString());
        event.put("authorId", post.getAuthorId().toString());
        if (post.getParent() != null) {
            event.put("parentId", post.getParent().getId().toString());
        }
        publish("forum.post.created", event);
    }

    public void publishPostDeleted(Post post) {
        Map<String, Object> event = createEvent("POST_DELETED");
        event.put("postId", post.getId().toString());
        event.put("topicId", post.getTopic().getId().toString());
        publish("forum.post.deleted", event);
    }

    public void publishSolutionMarked(Post post) {
        Map<String, Object> event = createEvent("SOLUTION_MARKED");
        event.put("postId", post.getId().toString());
        event.put("topicId", post.getTopic().getId().toString());
        event.put("authorId", post.getAuthorId().toString());
        publish("forum.solution.marked", event);
    }

    public void publishTopicReplyNotification(Topic topic, Post post, List<UUID> subscriberIds) {
        Map<String, Object> event = createEvent("TOPIC_REPLY_NOTIFICATION");
        event.put("topicId", topic.getId().toString());
        event.put("topicTitle", topic.getTitle());
        event.put("postId", post.getId().toString());
        event.put("authorId", post.getAuthorId().toString());
        event.put("subscriberIds", subscriberIds.stream().map(UUID::toString).toList());
        publish("forum.notification.reply", event);
    }

    private Map<String, Object> createEvent(String eventType) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", eventType);
        event.put("timestamp", LocalDateTime.now().toString());
        event.put("source", "forum-service");
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
