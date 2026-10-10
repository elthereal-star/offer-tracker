package com.offertracker;

import com.offertracker.common.BusinessException;
import com.offertracker.service.RedisAiRequestLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers(disabledWithoutDocker = true)
class RedisAiRequestLimiterTest {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Test
    void sharesTheMinuteLimitAcrossInstancesAndSeparatesUsers() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        try {
            StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
            redis.afterPropertiesSet();
            RedisAiRequestLimiter limiter = new RedisAiRequestLimiter(redis, 2);

            limiter.checkAllowed(41L);
            limiter.checkAllowed(41L);
            BusinessException error = assertThrows(BusinessException.class, () -> limiter.checkAllowed(41L));
            limiter.checkAllowed(42L);

            assertEquals(429, error.getCode());
            assertEquals(2, redis.keys("offer-tracker:rate:ai:*:minute").size());
            assertNotNull(redis.getExpire("offer-tracker:rate:ai:{41}:minute"));
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void requiresAnAuthenticatedOwner() {
        RedisAiRequestLimiter limiter = new RedisAiRequestLimiter(new StringRedisTemplate(), 10);

        BusinessException error = assertThrows(BusinessException.class, () -> limiter.checkAllowed(null));

        assertEquals(401, error.getCode());
    }
}
