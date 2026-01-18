package com.billboard.content.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // Forum cache - longer TTL as forums change rarely
        cacheConfigurations.put("forums", defaultConfig.entryTtl(Duration.ofHours(1)));
        
        // Forum hierarchy cache
        cacheConfigurations.put("forum-hierarchy", defaultConfig.entryTtl(Duration.ofHours(1)));
        
        // Topic cache - moderate TTL
        cacheConfigurations.put("topics", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        
        // Post cache - shorter TTL as posts can be edited
        cacheConfigurations.put("posts", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        
        // User subscription cache
        cacheConfigurations.put("subscriptions", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        
        // Vote cache - short TTL
        cacheConfigurations.put("votes", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        
        // Stats cache - short TTL
        cacheConfigurations.put("forum-stats", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }
}
