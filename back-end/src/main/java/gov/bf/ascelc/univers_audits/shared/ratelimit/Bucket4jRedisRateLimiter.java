package gov.bf.ascelc.univers_audits.shared.ratelimit;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(prefix = "rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class Bucket4jRedisRateLimiter implements RateLimiter {

    private final ProxyManager<String> proxyManager;

    @Override
    public RateLimitResult tryConsume(String key, int capacity, Duration refillPeriod) {
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, refillPeriod))
                .build();

        ConsumptionProbe probe = proxyManager.getProxy(key, () -> configuration)
                .tryConsumeAndReturnRemaining(1);

        long retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
        return new RateLimitResult(probe.isConsumed(), retryAfterSeconds);
    }
}
