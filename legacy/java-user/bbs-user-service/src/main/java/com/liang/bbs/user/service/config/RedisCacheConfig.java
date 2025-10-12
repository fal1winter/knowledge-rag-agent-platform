package com.liang.bbs.user.service.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 */

@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        // 配置 JSON 序列化器
        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        om.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        jackson2JsonRedisSerializer.setObjectMapper(om);

        // 默认缓存配置（TTL = 10分钟，不缓存 null）
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jackson2JsonRedisSerializer));

        // 指定缓存空间的 TTL 配置
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        
        // 用户相关缓存
        cacheConfigs.put("userCache", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigs.put("userAuthCache", defaultConfig.entryTtl(Duration.ofMinutes(10)));  // 用户权限 10分钟
        cacheConfigs.put("hotAuthorsCache", defaultConfig.entryTtl(Duration.ofMinutes(2)));
        
        // 权限相关缓存
        cacheConfigs.put("roleCache", defaultConfig.entryTtl(Duration.ofMinutes(30)));      // 角色 30分钟
        cacheConfigs.put("permissionCache", defaultConfig.entryTtl(Duration.ofMinutes(30))); // 权限 30分钟
        
        // 资源权限缓存
        cacheConfigs.put("resourcePermCache", defaultConfig.entryTtl(Duration.ofMinutes(15))); // 资源权限 15分钟
        
        // 其他缓存
        cacheConfigs.put("productCache", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigs.put("followCache", defaultConfig.entryTtl(Duration.ofMinutes(5)));     // 关注 5分钟
        cacheConfigs.put("likeCache", defaultConfig.entryTtl(Duration.ofMinutes(5)));       // 点赞 5分钟
        
        // 热门榜单缓存 - 1分钟过期
        cacheConfigs.put("popularTargetsCache", defaultConfig.entryTtl(Duration.ofMinutes(1)));  // 热门目标 1分钟

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}
