package com.pocsigmet.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter implements Filter {

    @Value("${rate-limit.requests:100}")
    private int requests;

    @Value("${rate-limit.minutes:1}")
    private int minutes;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private static final String[] EXCLUDED = {"/login", "/logout", "/actuator/health"};

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String path = request.getRequestURI();
        for (String excluded : EXCLUDED) {
            if (path.startsWith(excluded)) {
                chain.doFilter(req, res);
                return;
            }
        }

        String ip = getClientIp(request);
        Bucket bucket = buckets.computeIfAbsent(ip, k ->
            Bucket.builder()
                .addLimit(Bandwidth.builder()
                    .capacity(requests)
                    .refillGreedy(requests, Duration.ofMinutes(minutes))
                    .build())
                .build()
        );

        if (bucket.tryConsume(1)) {
            chain.doFilter(req, res);
        } else {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too Many Requests\",\"retryAfter\":\"" + minutes + "m\"}");
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) return ip.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
