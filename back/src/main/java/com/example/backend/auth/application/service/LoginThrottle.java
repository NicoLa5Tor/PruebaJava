package com.example.backend.auth.application.service;

import com.example.backend.auth.domain.exception.TooManyAttemptsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Brute-force protection for password sign-in: counts failures per account and per client address in a sliding
 * window. Successful sign-ins clear the account's counter. In memory, which is enough for a single instance;
 * several instances would move these counters to a shared store such as Redis.
 */
@Component
public class LoginThrottle {
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final int MAX_FAILURES_PER_ACCOUNT = 5;
    static final int MAX_FAILURES_PER_ADDRESS = 20;

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    public void checkAllowed(String email, String clientIp) {
        Instant now = clock.instant();
        check(accountKey(email), MAX_FAILURES_PER_ACCOUNT, now);
        check(addressKey(clientIp), MAX_FAILURES_PER_ADDRESS, now);
    }

    public void recordFailure(String email, String clientIp) {
        Instant now = clock.instant();
        record(accountKey(email), now);
        record(addressKey(clientIp), now);
    }

    public void recordSuccess(String email) {
        failures.remove(accountKey(email));
    }

    private void check(String key, int limit, Instant now) {
        Deque<Instant> attempts = failures.get(key);
        if (attempts == null) {
            return;
        }
        synchronized (attempts) {
            prune(attempts, now);
            if (attempts.isEmpty()) {
                failures.remove(key, attempts);
                return;
            }
            if (attempts.size() >= limit) {
                throw new TooManyAttemptsException(Duration.between(now, attempts.peekFirst().plus(WINDOW)));
            }
        }
    }

    private void record(String key, Instant now) {
        Deque<Instant> attempts = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts, now);
            attempts.addLast(now);
        }
    }

    private static void prune(Deque<Instant> attempts, Instant now) {
        Instant oldestAllowed = now.minus(WINDOW);
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(oldestAllowed)) {
            attempts.removeFirst();
        }
    }

    private static String accountKey(String email) {
        return "account:" + email;
    }

    private static String addressKey(String clientIp) {
        return "address:" + (clientIp == null ? "unknown" : clientIp);
    }
}
