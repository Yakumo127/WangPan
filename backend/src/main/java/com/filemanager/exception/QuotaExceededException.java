package com.filemanager.exception;

public class QuotaExceededException extends RuntimeException {
    private final long required;
    private final Long available;

    public QuotaExceededException(String message, long required, Long available) {
        super(message);
        this.required = required;
        this.available = available;
    }

    public long getRequired() { return required; }
    public Long getAvailable() { return available; }
}

