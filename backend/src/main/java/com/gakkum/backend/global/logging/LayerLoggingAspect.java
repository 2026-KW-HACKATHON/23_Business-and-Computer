package com.gakkum.backend.global.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LayerLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LayerLoggingAspect.class);

    @Around("execution(public * *(..)) && ("
            + "execution(* com.gakkum.backend..controller..*(..)) || "
            + "execution(* com.gakkum.backend..facade..*(..)) || "
            + "execution(* com.gakkum.backend..service..*(..)) || "
            + "execution(* com.gakkum.backend..client..*(..)) || "
            + "this(org.springframework.data.repository.Repository)) "
            + "&& !execution(* java.lang.Object.*(..))")
    public Object logLayer(ProceedingJoinPoint joinPoint) throws Throwable {
        if (MDC.get(RequestLogContext.TID_KEY) == null) {
            return joinPoint.proceed();
        }

        int depth = RequestLogContext.depth();
        String prefix = "| ".repeat(depth);
        String method = className(joinPoint) + "." + joinPoint.getSignature().getName();
        log.info("{}--> {}", prefix, method);
        RequestLogContext.setDepth(depth + 1);
        long startedAt = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            log.info("{}<-- {} {}", prefix, method, RequestLogContext.elapsed(startedAt));
            return result;
        } catch (Throwable exception) {
            log.info("{}<X- {} {} exception={}", prefix, method,
                    RequestLogContext.elapsed(startedAt), exception.getClass().getSimpleName());
            throw exception;
        } finally {
            RequestLogContext.setDepth(depth);
        }
    }

    private String className(ProceedingJoinPoint joinPoint) {
        if (joinPoint.getThis() instanceof Repository) {
            for (Class<?> type : ClassUtils.getAllInterfaces(joinPoint.getThis())) {
                if (Repository.class.isAssignableFrom(type) && type.getName().startsWith("com.gakkum.backend.")) {
                    return type.getSimpleName();
                }
            }
        }
        return AopUtils.getTargetClass(joinPoint.getTarget()).getSimpleName();
    }
}
