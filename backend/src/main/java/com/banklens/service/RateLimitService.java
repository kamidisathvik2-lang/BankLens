package com.banklens.service;

import com.banklens.exception.BankLensExceptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${banklens.rate-limit.analyses-per-day}")
    private int analysesPerDay;

    @Value("${banklens.rate-limit.requests-per-minute}")
    private int requestsPerMinute;

    /**
     * Check and increment daily analysis count per user.
     * Uses a sliding window with midnight reset.
     */
    public void checkAnalysisLimit(String userId) {
        String key = "rate:analysis:daily:" + userId + ":" + todayKey();
        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1) {
            // First request today — set TTL to expire at end of day
            redisTemplate.expire(key, 25, TimeUnit.HOURS);
        }

        if (count != null && count > analysesPerDay) {
            throw new BankLensExceptions.RateLimitExceededException(
                    "Daily analysis limit reached (" + analysesPerDay + "/day). Try again tomorrow.");
        }
    }

    /**
     * Check per-minute request rate (sliding window).
     */
    public void checkRequestRate(String userId) {
        String key = "rate:requests:minute:" + userId;
        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }

        if (count != null && count > requestsPerMinute) {
            throw new BankLensExceptions.RateLimitExceededException(
                    "Too many requests. Please wait before trying again.");
        }
    }

    public long getRemainingAnalyses(String userId) {
        String key = "rate:analysis:daily:" + userId + ":" + todayKey();
        Object val = redisTemplate.opsForValue().get(key);
        long used = val == null ? 0L : Long.parseLong(val.toString());
        return Math.max(0, analysesPerDay - used);
    }

    private String todayKey() {
        return java.time.LocalDate.now().toString();
    }
}
