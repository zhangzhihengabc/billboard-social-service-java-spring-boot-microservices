package com.billboard.content.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.exchange:forum-events}")
    private String exchangeName;

    @Bean
    public TopicExchange forumExchange() {
        return new TopicExchange(exchangeName);
    }

    @Bean
    public Queue forumCreatedQueue() {
        return QueueBuilder.durable("forum.created.queue").build();
    }

    @Bean
    public Queue topicCreatedQueue() {
        return QueueBuilder.durable("forum.topic.created.queue").build();
    }

    @Bean
    public Queue postCreatedQueue() {
        return QueueBuilder.durable("forum.post.created.queue").build();
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable("forum.notification.queue").build();
    }

    @Bean
    public Binding forumCreatedBinding(Queue forumCreatedQueue, TopicExchange forumExchange) {
        return BindingBuilder.bind(forumCreatedQueue).to(forumExchange).with("forum.created");
    }

    @Bean
    public Binding topicCreatedBinding(Queue topicCreatedQueue, TopicExchange forumExchange) {
        return BindingBuilder.bind(topicCreatedQueue).to(forumExchange).with("forum.topic.*");
    }

    @Bean
    public Binding postCreatedBinding(Queue postCreatedQueue, TopicExchange forumExchange) {
        return BindingBuilder.bind(postCreatedQueue).to(forumExchange).with("forum.post.*");
    }

    @Bean
    public Binding notificationBinding(Queue notificationQueue, TopicExchange forumExchange) {
        return BindingBuilder.bind(notificationQueue).to(forumExchange).with("forum.notification.*");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }
}
