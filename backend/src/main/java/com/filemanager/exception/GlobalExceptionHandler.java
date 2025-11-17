package com.filemanager.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final com.filemanager.metrics.DownloadMetrics downloadMetrics;

    public GlobalExceptionHandler(@org.springframework.beans.factory.annotation.Autowired(required = false)
                                  com.filemanager.metrics.DownloadMetrics downloadMetrics) {
        this.downloadMetrics = downloadMetrics;
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NotFoundException ex) {
        if (downloadMetrics != null) downloadMetrics.incError();
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", ex.getMessage() == null ? "资源不存在" : ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(ForbiddenException ex) {
        if (downloadMetrics != null) downloadMetrics.incError();
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", ex.getMessage() == null ? "没有权限" : ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        if (downloadMetrics != null) downloadMetrics.incError();
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException ex) {
        if (downloadMetrics != null) downloadMetrics.incError();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", ex.getMessage() == null ? "服务器错误" : ex.getMessage()));
    }

    // 统一处理上传过大
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("message", "文件过大，超出限制"));
    }

    // 兜底处理 Multipart 相关异常
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, String>> handleMultipart(MultipartException ex) {
        String msg = ex.getMessage();
        if (msg != null && msg.toLowerCase().contains("size")) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Map.of("message", "文件过大，超出限制"));
        }
        return ResponseEntity.badRequest().body(Map.of("message", "上传数据不合法"));
    }
}
