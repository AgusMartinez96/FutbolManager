package com.futbolmanager.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * In-memory throttling for login and registration. State lives in this instance only, which is enough
 * for the single-instance deployment; a restart clears every block.
 */
@Component
public class AuthRateLimiter {

    static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    static final Duration REGISTER_WINDOW = Duration.ofHours(1);
    static final int FAILURES_BEFORE_BLOCK = 5;
    static final int MAX_FAILURES_PER_IP = 50;
    static final Duration BASE_BLOCK = Duration.ofSeconds(30);
    static final Duration MAX_BLOCK = Duration.ofSeconds(900);

    private record KeyState(int failures, Instant lastFailure, Instant blockedUntil) {}

    private final Clock clock;
    private final int registerPerIpPerHour;
    private final ConcurrentHashMap<String, KeyState> keyStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Instant>> failuresByIp = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Instant>> registrationsByIp = new ConcurrentHashMap<>();

    public AuthRateLimiter(Clock clock, @Value("${fm.auth.register-per-ip-per-hour}") int registerPerIpPerHour) {
        this.clock = clock;
        this.registerPerIpPerHour = registerPerIpPerHour;
    }

    /** Remaining block for this (username, ip) pair or for the whole ip, whichever lasts longer. */
    public Optional<Duration> loginBlockedFor(String username, String ip) {
        Instant now = clock.instant();
        Duration remaining = Duration.ZERO;

        KeyState state = keyStates.get(key(username, ip));
        if (state != null && state.blockedUntil().isAfter(now)) {
            remaining = Duration.between(now, state.blockedUntil());
        }

        AtomicReference<Duration> ipRemaining = new AtomicReference<>(Duration.ZERO);
        failuresByIp.computeIfPresent(ip, (k, window) -> {
            prune(window, now, FAILURE_WINDOW);
            if (window.size() >= MAX_FAILURES_PER_IP) {
                ipRemaining.set(Duration.between(now, window.peekFirst().plus(FAILURE_WINDOW)));
            }
            return window.isEmpty() ? null : window;
        });
        if (ipRemaining.get().compareTo(remaining) > 0) {
            remaining = ipRemaining.get();
        }
        return remaining.isZero() ? Optional.empty() : Optional.of(remaining);
    }

    public void recordLoginFailure(String username, String ip) {
        Instant now = clock.instant();
        keyStates.compute(key(username, ip), (k, state) -> {
            int failures = state == null || Duration.between(state.lastFailure(), now).compareTo(FAILURE_WINDOW) > 0
                    ? 1
                    : state.failures() + 1;
            Instant blockedUntil = failures >= FAILURES_BEFORE_BLOCK ? now.plus(blockFor(failures)) : Instant.EPOCH;
            return new KeyState(failures, now, blockedUntil);
        });
        failuresByIp.compute(ip, (k, window) -> {
            Deque<Instant> next = window != null ? window : new ArrayDeque<>();
            prune(next, now, FAILURE_WINDOW);
            next.addLast(now);
            return next;
        });
    }

    public void recordLoginSuccess(String username, String ip) {
        keyStates.remove(key(username, ip));
    }

    /** Counts this attempt unless the ip already used its hourly quota, in which case it returns the wait. */
    public Optional<Duration> registerBlockedFor(String ip) {
        Instant now = clock.instant();
        AtomicReference<Duration> blocked = new AtomicReference<>();
        registrationsByIp.compute(ip, (k, window) -> {
            Deque<Instant> next = window != null ? window : new ArrayDeque<>();
            prune(next, now, REGISTER_WINDOW);
            if (next.size() >= registerPerIpPerHour) {
                blocked.set(Duration.between(now, next.peekFirst().plus(REGISTER_WINDOW)));
            } else {
                next.addLast(now);
            }
            return next;
        });
        return Optional.ofNullable(blocked.get());
    }

    /** Whole seconds, rounded up, never below 1: the value for the Retry-After header. */
    public static long retryAfterSeconds(Duration remaining) {
        long millis = remaining.toMillis();
        return Math.max(1, (millis + 999) / 1000);
    }

    private static Duration blockFor(int failures) {
        // The exponent stops growing once the cap is reached, which also avoids overflow.
        int exponent = Math.min(failures - FAILURES_BEFORE_BLOCK, 10);
        Duration block = BASE_BLOCK.multipliedBy(1L << exponent);
        return block.compareTo(MAX_BLOCK) > 0 ? MAX_BLOCK : block;
    }

    private static void prune(Deque<Instant> window, Instant now, Duration length) {
        Instant cutoff = now.minus(length);
        while (!window.isEmpty() && !window.peekFirst().isAfter(cutoff)) {
            window.removeFirst();
        }
    }

    private static String key(String username, String ip) {
        return username.toLowerCase(Locale.ROOT) + "|" + ip;
    }
}
