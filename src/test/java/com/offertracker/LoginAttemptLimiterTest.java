package com.offertracker;

import com.offertracker.common.BusinessException;
import com.offertracker.service.RedisLoginAttemptLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginAttemptLimiterTest {
    @Test
    void blocksAtFiveFailuresAndClearsTheCounter() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(org.mockito.ArgumentMatchers.anyString())).thenReturn(6L);
        RedisLoginAttemptLimiter limiter = new RedisLoginAttemptLimiter(redis);

        BusinessException error = assertThrows(BusinessException.class,
                () -> limiter.checkAllowed("+8613800000002"));

        assertEquals(429, error.getCode());
        limiter.clear("+8613800000002");
        verify(redis).delete(org.mockito.ArgumentMatchers.anyString());
    }
}
