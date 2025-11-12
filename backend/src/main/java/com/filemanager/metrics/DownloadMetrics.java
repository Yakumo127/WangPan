package com.filemanager.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class DownloadMetrics {
    private final MeterRegistry registry;

    private Counter requestCounter;
    private Counter fullCounter;
    private Counter partialCounter;
    private Counter headCounter;
    private Counter notModifiedCounter;
    private Counter errorCounter;
    private DistributionSummary bytesSummary;

    public DownloadMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    public void init() {
        this.requestCounter = registry.counter("file_download_requests_total");
        this.fullCounter = registry.counter("file_download_full_total");
        this.partialCounter = registry.counter("file_download_partial_total");
        this.headCounter = registry.counter("file_download_head_total");
        this.notModifiedCounter = registry.counter("file_download_not_modified_total");
        this.errorCounter = registry.counter("file_download_errors_total");
        this.bytesSummary = DistributionSummary
                .builder("file_download_bytes")
                .baseUnit("bytes")
                .register(registry);
    }

    public void incRequest() { requestCounter.increment(); }
    public void incFull(long bytes) { fullCounter.increment(); bytesSummary.record(bytes); }
    public void incPartial(long bytes) { partialCounter.increment(); bytesSummary.record(bytes); }
    public void incHead() { headCounter.increment(); }
    public void incNotModified() { notModifiedCounter.increment(); }
    public void incError() { errorCounter.increment(); }
}

