package com.offertracker;

import com.offertracker.common.BusinessException;
import com.offertracker.service.RedisSmsRequestLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers(disabledWithoutDocker = true)
class RedisSmsRequestLimiterTest {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Test
    void sharesRateCountersInRedisAndRejectsRequestsAboveMinuteLimit() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        try {
            StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
            redis.afterPropertiesSet();
            RedisSmsRequestLimiter limiter = new RedisSmsRequestLimiter(redis);

            limiter.checkAllowed("+8613800000002");

            assertEquals(3, redis.keys("offer-tracker:rate:sms:*").size());
            redis.keys("offer-tracker:rate:sms:*").forEach(key ->
                    assertEquals("1", redis.opsForValue().get(key)));
            assertThrows(BusinessException.class, () -> limiter.checkAllowed("+8613800000002"));
        } finally {
            connectionFactory.destroy();
        }
    }
}
