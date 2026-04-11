package com.billboard.social.graph.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SocialEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private SocialEventPublisher publisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(publisher, "topic", "social-events");
    }

    @Test
    void publish_sendsToKafkaWithCorrectTopicAndRoutingKey() {
        Map<String, Object> event = Map.of(
                "eventType", "social.friend_request.accepted",
                "requesterId", 1L,
                "addresseeId", 2L);

        publisher.publish("social.friend_request.accepted", event);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);

        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), valueCaptor.capture());
        assertThat(topicCaptor.getValue()).isEqualTo("social-events");
        assertThat(keyCaptor.getValue()).isEqualTo("social.friend_request.accepted");
        assertThat(valueCaptor.getValue()).isEqualTo(event);
    }

    @Test
    void publish_usesNoRabbitImports() {
        // Structural check: the class must not have any rabbit-related fields
        assertThat(SocialEventPublisher.class.getDeclaredFields())
                .noneMatch(f -> f.getType().getName().contains("rabbit"));
    }
}
