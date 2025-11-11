package com.filemanager.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class AuditMetricsService {
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Counter purgeCounter;

    public AuditMetricsService(MeterRegistry registry) {
        this.successCounter = Counter.builder("audit.logs.success").description("Count of successful audit logs").register(registry);
        this.failureCounter = Counter.builder("audit.logs.failed").description("Count of failed audit logs").register(registry);
        this.purgeCounter = Counter.builder("audit.purge.deleted").description("Count of purged logs").register(registry);
    }

    public void incSuccess() { successCounter.increment(); }
    public void incFailure() { failureCounter.increment(); }
    public void incPurged(long n) { if (n > 0) purgeCounter.increment(n); }
}

