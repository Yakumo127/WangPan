package com.filemanager.audit;

import com.filemanager.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.context.ApplicationContext;
import org.springframework.expression.BeanResolver;
import org.springframework.context.expression.BeanFactoryResolver;

import java.lang.reflect.Method;

@Aspect
@Component
@RequiredArgsConstructor
public class ServiceAuditAspect {

    private final AuditLogService auditLogService;
    private final ApplicationContext applicationContext;

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();
    private static final DefaultParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    @Around("@annotation(audited)")
    public Object aroundAudited(ProceedingJoinPoint pjp, AuditedOperation audited) throws Throwable {
        long start = System.currentTimeMillis();
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        Object[] args = pjp.getArgs();

        Object ret = null;
        Throwable thrown = null;
        try {
            ret = pjp.proceed();
            return ret;
        } catch (Throwable t) {
            thrown = t;
            throw t;
        } finally {
            try {
                // 构建 SpEL 上下文
                StandardEvaluationContext ctx = new StandardEvaluationContext();
                // 参数按名称绑定
                String[] paramNames = NAME_DISCOVERER.getParameterNames(method);
                if (paramNames != null) {
                    for (int i = 0; i < paramNames.length && i < args.length; i++) {
                        ctx.setVariable(paramNames[i], args[i]);
                    }
                }
                // 位置参数绑定：#p0/#a0
                for (int i = 0; i < args.length; i++) {
                    ctx.setVariable("p" + i, args[i]);
                    ctx.setVariable("a" + i, args[i]);
                }
                // 返回值绑定
                ctx.setVariable("result", ret);
                // 支持 @beanName 访问
                ctx.setBeanResolver(new BeanFactoryResolver(applicationContext));

                Long userId = evalLong(ctx, audited.userId());
                String actionType = audited.actionType();
                String resourceType = audited.resourceType();
                Long resourceId = evalLongNullable(ctx, audited.resourceId());
                String resourceName = evalString(ctx, audited.resourceName());
                String description = evalString(ctx, audited.description());
                long cost = System.currentTimeMillis() - start;

                if (thrown == null) {
                    auditLogService.logSuccess(userId, actionType, resourceType, resourceId, resourceName, description, cost);
                } else {
                    String err = thrown.getMessage();
                    auditLogService.logFailure(userId, actionType, resourceType, resourceId, resourceName, description, err, cost);
                }
            } catch (Exception ignore) {}
        }
    }

    private Long evalLong(StandardEvaluationContext ctx, String expr) {
        Object val = PARSER.parseExpression(expr).getValue(ctx);
        if (val == null) return null;
        if (val instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(val));
    }

    private Long evalLongNullable(StandardEvaluationContext ctx, String expr) {
        if (expr == null || expr.isBlank()) return null;
        Object val = PARSER.parseExpression(expr).getValue(ctx);
        if (val == null) return null;
        if (val instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(val)); } catch (Exception e) { return null; }
    }

    private String evalString(StandardEvaluationContext ctx, String expr) {
        if (expr == null || expr.isBlank()) return null;
        Expression e = PARSER.parseExpression(expr);
        Object val = e.getValue(ctx);
        return val == null ? null : String.valueOf(val);
    }
}
