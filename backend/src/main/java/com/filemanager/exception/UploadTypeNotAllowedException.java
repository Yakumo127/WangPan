package com.filemanager.exception;

import java.util.List;

public class UploadTypeNotAllowedException extends RuntimeException {
    private final List<String> allowedSuffixes;

    public UploadTypeNotAllowedException(String message, List<String> allowedSuffixes) {
        super(message);
        this.allowedSuffixes = allowedSuffixes;
    }

    public List<String> getAllowedSuffixes() { return allowedSuffixes; }
}

