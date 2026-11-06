package com.odero.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/** All tunables in one typed place. Values can be overridden in application.yml or env vars. */
@ConfigurationProperties(prefix = "engine")
public record EngineProperties(
        @DefaultValue({"1s", "5s", "30s", "2m", "10m"}) List<Duration> retryTiers,
        @DefaultValue("10m") Duration idempotencyClaimTtl,
        @DefaultValue("24h") Duration idempotencyDoneTtl,
        @DefaultValue("5m") Duration taskLease,
        @DefaultValue("4") int concurrency,
        @DefaultValue("16") int maxConcurrency,
        @DefaultValue("10") int prefetch) {
}
