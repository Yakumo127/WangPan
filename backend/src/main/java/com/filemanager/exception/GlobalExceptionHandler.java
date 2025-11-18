package com.filemanager.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.util.Map;
import java.util.HashMap;
import java.io.IOException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final com.filemanager.metrics.DownloadMetrics downloadMetrics;
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public GlobalExceptionHandler(@org.springframework.beans.factory.annotation.Autowired(required = false)
                                  com.filemanager.metrics.DownloadMetrics downloadMetrics) {
        this.downloadMetrics = downloadMetrics;
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NotFoundException ex) {
        if (downloadMetrics != null) downloadMetrics.incError();
        log.warn("NotFound: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", ex.getMessage() == null ? "资源不存在" : ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(ForbiddenException ex) {
        if (downloadMetrics != null) downloadMetrics.incError();
        log.warn("Forbidden: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", ex.getMessage() == null ? "没有权限" : ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        if (downloadMetrics != null) downloadMetrics.incError();
        log.warn("BadRequest: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    // 422: 上传类型不允许
    @ExceptionHandler(UploadTypeNotAllowedException.class)
    public ResponseEntity<Map<String, Object>> handleUploadNotAllowed(UploadTypeNotAllowedException ex) {
        log.warn("Upload type not allowed: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("code", "UPLOAD_TYPE_NOT_ALLOWED");
        body.put("message", ex.getMessage() == null ? "不允许上传该类型文件" : ex.getMessage());
        body.put("allowedSuffixes", ex.getAllowedSuffixes());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    // 409: 配额不足
    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<Map<String, Object>> handleQuotaExceeded(QuotaExceededException ex) {
        log.warn("Quota exceeded: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("code", "QUOTA_EXCEEDED");
        body.put("message", ex.getMessage() == null ? "存储空间不足" : ex.getMessage());
        body.put("required", ex.getRequired());
        if (ex.getAvailable() != null) body.put("available", ex.getAvailable());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException ex) {
        if (downloadMetrics != null) downloadMetrics.incError();
        log.error("Runtime error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", ex.getMessage() == null ? "服务器错误" : ex.getMessage()));
    }

    // 统一处理上传过大
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUpload(MaxUploadSizeExceededException ex) {
        log.warn("Payload too large: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("message", "文件过大，超出限制"));
    }

    // 兜底处理 Multipart 相关异常
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, String>> handleMultipart(MultipartException ex) {
        String msg = ex.getMessage();
        log.warn("Multipart error: {}", msg);
        if (msg != null && msg.toLowerCase().contains("size")) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Map.of("message", "文件过大，超出限制"));
        }
        return ResponseEntity.badRequest().body(Map.of("message", "上传数据不合法"));
    }

    // I/O 异常兜底（例如文件系统读写失败）
    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, String>> handleIOException(IOException ex) {
        if (downloadMetrics != null) downloadMetrics.incError();
        log.error("I/O error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "文件 I/O 异常"));
    }
}
