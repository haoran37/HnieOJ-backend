package com.hnieacm.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/13
 * @Description: Redis 配置
 */
@Configuration
public class RedisConfig {

    /**
     * @MethodName authCacheRedisTemplate
     * @Param connectionFactory
     * @Description 创建认证缓存 RedisTemplate
     * @Return @return {@link ReactiveRedisTemplate }<{@link String }, {@link String }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/13
     */
    @Bean("authCacheRedisTemplate")
    public ReactiveRedisTemplate<String, String> authCacheRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        
        // 使用 String 序列化器
        StringRedisSerializer serializer = new StringRedisSerializer();
        
        // 构建序列化上下文
        RedisSerializationContext<String, String> context = RedisSerializationContext
                .<String, String>newSerializationContext(serializer)
                .key(serializer)
                .value(serializer)
                .hashKey(serializer)
                .hashValue(serializer)
                .build();
        
        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }
}
