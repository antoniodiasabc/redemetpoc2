package com.pocsigmet.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginAttemptService {

    @Value("${brute-force.max-attempts:5}")
    private int maxAttempts;

    @Value("${brute-force.block-minutes:15}")
    private int blockMinutes;

    private record Attempt(int count, Instant blockedUntil) {}

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public void loginFailed(String ip) {
        attempts.compute(ip, (k, a) -> {
            int count = (a == null ? 0 : a.count()) + 1;
            Instant blockedUntil = count >= maxAttempts
                ? Instant.now().plusSeconds(blockMinutes * 60L)
                : Instant.EPOCH;
            return new Attempt(count, blockedUntil);
        });
    }

    public void loginSucceeded(String ip) {
        attempts.remove(ip);
    }

    public boolean isBlocked(String ip) {
        Attempt a = attempts.get(ip);
        if (a == null) return false;
        if (a.blockedUntil().isAfter(Instant.now())) return true;
        if (a.blockedUntil() != Instant.EPOCH) attempts.remove(ip); // expirou
        return false;
    }
}
