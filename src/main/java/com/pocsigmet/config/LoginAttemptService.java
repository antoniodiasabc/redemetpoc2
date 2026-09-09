package com.pocsigmet.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class LoginAttemptService {

    @Value("${brute-force.max-attempts:5}")
    private int maxAttempts;

    @Value("${brute-force.block-minutes:15}")
    private int blockMinutes;

    private static final String PREFIX = "login:attempts:";
    private static final String BLOCK_PREFIX = "login:blocked:";

    private final StringRedisTemplate redis;

    public LoginAttemptService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void loginFailed(String ip) {
        String key = PREFIX + ip;
        Long attempts = redis.opsForValue().increment(key);
        if (attempts == 1) redis.expire(key, Duration.ofMinutes(blockMinutes));
        if (attempts >= maxAttempts) {
            redis.opsForValue().set(BLOCK_PREFIX + ip, "1", Duration.ofMinutes(blockMinutes));
        }
    }

    public void loginSucceeded(String ip) {
        redis.delete(PREFIX + ip);
        redis.delete(BLOCK_PREFIX + ip);
    }

    public boolean isBlocked(String ip) {
        return Boolean.TRUE.equals(redis.hasKey(BLOCK_PREFIX + ip));
    }
}
