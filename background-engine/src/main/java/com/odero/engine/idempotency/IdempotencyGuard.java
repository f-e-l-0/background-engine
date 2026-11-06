package com.odero.engine.idempotency;

import com.odero.engine.config.EngineProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed claim using SET NX PX, which is atomic.
 *
 * Edge cases:
 * - Concurrent duplicates: only one SET NX can succeed.
 * - Worker crash mid-task: the claim expires after idempotencyClaimTtl, so redelivery can proceed.
 * - Slow consumer: the claim TTL must exceed the longest normal run. Otherwise a second worker
 *   can start while the first is still working. Postgres lock + status check is the final guard.
 * - Redis outage: the claim call throws, the listener defers the message, and no retry is spent.
 *   Correctness still holds because the database decides. Redis only prevents wasted work.
 */
@Component
public class IdempotencyGuard {

    private static final String PREFIX = "idem:";
    private static final String PROCESSING = "PROCESSING";
    private static final String DONE = "DONE";

    private final StringRedisTemplate redis;
    private final EngineProperties props;

    public IdempotencyGuard(StringRedisTemplate redis, EngineProperties props) {
        this.redis = redis;
        this.props = props;
    }

    public ClaimResult claim(IdempotencyKey key) {
        String redisKey = PREFIX + key.value();
        Boolean claimed = redis.opsForValue().setIfAbsent(redisKey, PROCESSING, props.idempotencyClaimTtl());
        if (Boolean.TRUE.equals(claimed)) {
            return ClaimResult.CLAIMED;
        }
        return DONE.equals(redis.opsForValue().get(redisKey)) ? ClaimResult.ALREADY_DONE : ClaimResult.IN_FLIGHT;
    }

    public void markDone(IdempotencyKey key) {
        redis.opsForValue().set(PREFIX + key.value(), DONE, props.idempotencyDoneTtl());
    }

    public void release(IdempotencyKey key) {
        redis.delete(PREFIX + key.value());
    }
}
