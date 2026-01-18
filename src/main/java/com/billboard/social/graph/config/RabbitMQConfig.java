package com.billboard.social.graph.config;

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

    @Value("${app.rabbitmq.exchange:social-events}")
    private String exchangeName;

    @Bean
    public TopicExchange socialEventsExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue friendshipEventsQueue() {
        return QueueBuilder.durable("friendship-events")
            .withArgument("x-dead-letter-exchange", exchangeName + ".dlx")
            .build();
    }

    @Bean
    public Queue followEventsQueue() {
        return QueueBuilder.durable("follow-events")
            .withArgument("x-dead-letter-exchange", exchangeName + ".dlx")
            .build();
    }

    @Bean
    public Queue reactionEventsQueue() {
        return QueueBuilder.durable("reaction-events")
            .withArgument("x-dead-letter-exchange", exchangeName + ".dlx")
            .build();
    }

    @Bean
    public Binding friendshipEventsBinding(Queue friendshipEventsQueue, TopicExchange socialEventsExchange) {
        return BindingBuilder.bind(friendshipEventsQueue).to(socialEventsExchange).with("friendship.#");
    }

    @Bean
    public Binding followEventsBinding(Queue followEventsQueue, TopicExchange socialEventsExchange) {
        return BindingBuilder.bind(followEventsQueue).to(socialEventsExchange).with("follow.#");
    }

    @Bean
    public Binding reactionEventsBinding(Queue reactionEventsQueue, TopicExchange socialEventsExchange) {
        return BindingBuilder.bind(reactionEventsQueue).to(socialEventsExchange).with("reaction.#");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        template.setExchange(exchangeName);
        return template;
    }
}
