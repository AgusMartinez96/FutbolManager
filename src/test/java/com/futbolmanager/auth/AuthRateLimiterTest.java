package com.futbolmanager.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthRateLimiterTest {

    private static final String IP = "10.0.0.1";

    private MutableClock clock;
    private AuthRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T12:00:00Z"));
        limiter = new AuthRateLimiter(clock, 10);
    }

    @Test
    void fourConsecutiveFailuresDoNotBlock() {
        for (int i = 0; i < 4; i++) {
            limiter.recordLoginFailure("ana", IP);
        }

        assertThat(limiter.loginBlockedFor("ana", IP)).isEmpty();
    }

    @Test
    void fifthSixthAndSeventhFailuresBlockFor30And60And120Seconds() {
        for (int i = 0; i < 4; i++) {
            limiter.recordLoginFailure("ana", IP);
        }

        limiter.recordLoginFailure("ana", IP);
        assertThat(limiter.loginBlockedFor("ana", IP)).contains(Duration.ofSeconds(30));

        clock.advance(Duration.ofSeconds(30));
        assertThat(limiter.loginBlockedFor("ana", IP)).isEmpty();
        limiter.recordLoginFailure("ana", IP);
        assertThat(limiter.loginBlockedFor("ana", IP)).contains(Duration.ofSeconds(60));

        clock.advance(Duration.ofSeconds(60));
        limiter.recordLoginFailure("ana", IP);
        assertThat(limiter.loginBlockedFor("ana", IP)).contains(Duration.ofSeconds(120));
    }

    @Test
    void blockNeverExceeds900Seconds() {
        for (int i = 0; i < 40; i++) {
            limiter.recordLoginFailure("ana", IP);
            clock.advance(Duration.ofSeconds(1));
        }

        Duration remaining = limiter.loginBlockedFor("ana", IP).orElseThrow();
        assertThat(remaining).isLessThanOrEqualTo(Duration.ofSeconds(900));
        assertThat(remaining).isGreaterThan(Duration.ofSeconds(800));
    }

    @Test
    void failureCounterResetsAfterFifteenQuietMinutes() {
        for (int i = 0; i < 4; i++) {
            limiter.recordLoginFailure("ana", IP);
        }
        clock.advance(Duration.ofMinutes(15).plusSeconds(1));

        limiter.recordLoginFailure("ana", IP);

        assertThat(limiter.loginBlockedFor("ana", IP)).isEmpty();
    }

    @Test
    void successResetsTheKey() {
        for (int i = 0; i < 5; i++) {
            limiter.recordLoginFailure("ana", IP);
        }
        clock.advance(Duration.ofSeconds(30));

        limiter.recordLoginSuccess("ana", IP);
        for (int i = 0; i < 4; i++) {
            limiter.recordLoginFailure("ana", IP);
        }

        assertThat(limiter.loginBlockedFor("ana", IP)).isEmpty();
    }

    @Test
    void blockIsScopedToTheUsernameAndIpPair() {
        for (int i = 0; i < 5; i++) {
            limiter.recordLoginFailure("ana", IP);
        }

        assertThat(limiter.loginBlockedFor("ANA", IP)).isPresent();
        assertThat(limiter.loginBlockedFor("ana", "10.0.0.2")).isEmpty();
        assertThat(limiter.loginBlockedFor("beto", IP)).isEmpty();
    }

    @Test
    void fiftyFailuresFromOneIpBlockItUntilTheOldestLeavesTheWindow() {
        for (int i = 0; i < 50; i++) {
            limiter.recordLoginFailure("user" + i, IP);
            clock.advance(Duration.ofSeconds(1));
        }

        Duration remaining = limiter.loginBlockedFor("otro", IP).orElseThrow();
        assertThat(remaining).isEqualTo(Duration.ofMinutes(15).minusSeconds(50));
        assertThat(limiter.loginBlockedFor("otro", "10.0.0.2")).isEmpty();

        clock.advance(remaining);
        assertThat(limiter.loginBlockedFor("otro", IP)).isEmpty();
    }

    @Test
    void registrationIsLimitedPerIpPerHour() {
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.registerBlockedFor(IP)).isEmpty();
            clock.advance(Duration.ofMinutes(1));
        }

        assertThat(limiter.registerBlockedFor(IP)).contains(Duration.ofMinutes(50));
        assertThat(limiter.registerBlockedFor("10.0.0.2")).isEmpty();

        clock.advance(Duration.ofMinutes(50));
        assertThat(limiter.registerBlockedFor(IP)).isEmpty();
    }

    @Test
    void retryAfterRoundsUpToWholeSeconds() {
        assertThat(AuthRateLimiter.retryAfterSeconds(Duration.ofMillis(29_001))).isEqualTo(30);
        assertThat(AuthRateLimiter.retryAfterSeconds(Duration.ofSeconds(30))).isEqualTo(30);
        assertThat(AuthRateLimiter.retryAfterSeconds(Duration.ofMillis(1))).isEqualTo(1);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
