package com.filemanager.audit;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AuditedOperation {
    // 业务动作与资源类型：建议使用 UserLog 常量
    String actionType();
    String resourceType();

    // SpEL 表达式，用于解析 userId（默认从参数名 userId 读取）
    String userId() default "#userId";

    // 可选：SpEL 表达式解析资源标识与名称（可基于参数或返回值）
    String resourceId() default ""; // 例如: "#result.id"
    String resourceName() default ""; // 例如: "#result.originalFilename ?: #filename"
    String description() default ""; // 例如: "'合并分片'"
}

