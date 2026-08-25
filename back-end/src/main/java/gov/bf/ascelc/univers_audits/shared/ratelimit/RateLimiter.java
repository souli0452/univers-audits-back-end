package gov.bf.ascelc.univers_audits.shared.ratelimit;

import java.time.Duration;

public interface RateLimiter {

    RateLimitResult tryConsume(String key, int capacity, Duration refillPeriod);

    record RateLimitResult(boolean allowed, long retryAfterSeconds) {}
}
