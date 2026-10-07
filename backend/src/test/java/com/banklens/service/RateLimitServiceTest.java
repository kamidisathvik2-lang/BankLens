package com.banklens.service;

import com.banklens.exception.BankLensExceptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitService")
class RateLimitServiceTest {

    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> valueOps;

    @InjectMocks RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(rateLimitService, "analysesPerDay", 10);
        ReflectionTestUtils.setField(rateLimitService, "requestsPerMinute", 30);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("checkAnalysisLimit: allows first request (count=1)")
    void checkAnalysisLimit_firstRequest() {
        when(valueOps.increment(anyString())).thenReturn(1L);

        assertThatNoException().isThrownBy(() ->
                rateLimitService.checkAnalysisLimit("user-123"));

        verify(redisTemplate).expire(anyString(), eq(25L), eq(TimeUnit.HOURS));
    }

    @Test
    @DisplayName("checkAnalysisLimit: allows up to the limit")
    void checkAnalysisLimit_atLimit() {
        when(valueOps.increment(anyString())).thenReturn(10L);

        assertThatNoException().isThrownBy(() ->
                rateLimitService.checkAnalysisLimit("user-123"));
    }

    @Test
    @DisplayName("checkAnalysisLimit: throws when over the limit")
    void checkAnalysisLimit_exceeded() {
        when(valueOps.increment(anyString())).thenReturn(11L);

        assertThatThrownBy(() -> rateLimitService.checkAnalysisLimit("user-123"))
                .isInstanceOf(BankLensExceptions.RateLimitExceededException.class)
                .hasMessageContaining("Daily analysis limit reached");
    }

    @Test
    @DisplayName("checkAnalysisLimit: sets TTL only on first increment")
    void checkAnalysisLimit_ttlOnFirstOnly() {
        when(valueOps.increment(anyString())).thenReturn(5L);

        rateLimitService.checkAnalysisLimit("user-123");

        verify(redisTemplate, never()).expire(anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("checkRequestRate: allows within rate")
    void checkRequestRate_allowed() {
        when(valueOps.increment(anyString())).thenReturn(15L);

        assertThatNoException().isThrownBy(() ->
                rateLimitService.checkRequestRate("user-123"));
    }

    @Test
    @DisplayName("checkRequestRate: throws when over per-minute limit")
    void checkRequestRate_exceeded() {
        when(valueOps.increment(anyString())).thenReturn(31L);

        assertThatThrownBy(() -> rateLimitService.checkRequestRate("user-123"))
                .isInstanceOf(BankLensExceptions.RateLimitExceededException.class)
                .hasMessageContaining("Too many requests");
    }

    @Test
    @DisplayName("getRemainingAnalyses: returns correct remaining count")
    void getRemainingAnalyses_calculatesCorrectly() {
        when(valueOps.get(anyString())).thenReturn("3");

        long remaining = rateLimitService.getRemainingAnalyses("user-123");

        assertThat(remaining).isEqualTo(7L); // 10 - 3
    }

    @Test
    @DisplayName("getRemainingAnalyses: returns full limit when no key exists")
    void getRemainingAnalyses_noKey() {
        when(valueOps.get(anyString())).thenReturn(null);

        long remaining = rateLimitService.getRemainingAnalyses("user-123");

        assertThat(remaining).isEqualTo(10L);
    }

    @Test
    @DisplayName("different users have separate rate limit keys")
    void rateLimits_perUser() {
        when(valueOps.increment(anyString())).thenReturn(1L);

        rateLimitService.checkAnalysisLimit("user-A");
        rateLimitService.checkAnalysisLimit("user-B");

        verify(valueOps, times(2)).increment(anyString());
        verify(valueOps).increment(contains("user-A"));
        verify(valueOps).increment(contains("user-B"));
    }
}
