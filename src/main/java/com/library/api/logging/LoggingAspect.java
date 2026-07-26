package com.library.api.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Traces every controller and service call: one line on entry (method + arguments),
 * one on exit (outcome + elapsed millis), and a distinct line if the call throws.
 *
 * <p>The point of doing this with an aspect rather than hand-written log statements is
 * that the tracing is uniform and lives in exactly one place - no method has to remember
 * to log, and the format can never drift between methods. Combined with the correlation
 * id that {@link CorrelationIdFilter} puts in the MDC, the log for a single request reads
 * as a contiguous, indented call tree, so an error at the bottom can be traced straight
 * back up to the HTTP call that caused it.
 *
 * <p>Logged at DEBUG so production stays quiet by default; flip {@code LOG_LEVEL_APP=DEBUG}
 * to turn the trace on. Failures are logged at ERROR regardless, so they always surface.
 */
@Aspect
@Component
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    /** Longest argument rendering we will print, so a huge payload can't flood the log. */
    private static final int MAX_ARG_LENGTH = 200;

    @Around("com.library.api.logging.Pointcuts.application()")
    public Object trace(ProceedingJoinPoint joinPoint) throws Throwable {
        // Only assemble the strings if DEBUG is actually on - this advice wraps every
        // service and controller call, so the guard keeps the hot path free of work.
        boolean debug = log.isDebugEnabled();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String target = signature.getDeclaringType().getSimpleName() + "." + signature.getName();

        if (debug) {
            safeLog(() -> log.debug("--> {}({})", target, renderArgs(signature, joinPoint.getArgs())));
        }

        long start = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            if (debug) {
                long ms = (System.nanoTime() - start) / 1_000_000;
                safeLog(() -> log.debug("<-- {} returned {} ({} ms)", target, describe(result), ms));
            }
            return result;
        } catch (Throwable ex) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            // The message, not the stack trace: the GlobalExceptionHandler logs the full
            // trace once at the boundary. Here we just mark which method it came out of, so
            // the trace shows the failure's position in the call tree without N stack dumps.
            safeLog(() -> log.error("<-- {} threw {}: {} ({} ms)",
                    target, ex.getClass().getSimpleName(), ex.getMessage(), ms));
            throw ex;
        }
    }

    /**
     * Runs a logging action, swallowing anything it throws.
     *
     * <p>Tracing is diagnostics, not business logic: if rendering an argument or return value
     * ever fails, that must never turn a working request into an error. This is the backstop
     * that guarantees the aspect can only ever <em>observe</em> a call, never break it.
     */
    private void safeLog(Runnable logAction) {
        try {
            logAction.run();
        } catch (Throwable loggingFailure) {
            log.warn("Trace logging failed (call itself was unaffected): {}", loggingFailure.toString());
        }
    }

    /**
     * Renders arguments, redacting any parameter whose name looks sensitive so passwords
     * and the like never reach the log. Parameter names survive because the build compiles
     * with {@code -parameters} (Spring Boot's default), so this is reliable, not best-effort.
     */
    private String renderArgs(MethodSignature signature, Object[] args) {
        if (args.length == 0) {
            return "";
        }
        Parameter[] params = signature.getMethod().getParameters();
        return IntStream.range(0, args.length)
                .mapToObj(i -> {
                    String name = i < params.length ? params[i].getName() : "arg" + i;
                    return isSensitive(name) ? name + "=****" : name + "=" + describe(args[i]);
                })
                .collect(Collectors.joining(", "));
    }

    private boolean isSensitive(String name) {
        String n = name.toLowerCase();
        return n.contains("password") || n.contains("secret") || n.contains("token");
    }

    private String describe(Object value) {
        if (value == null) {
            return "null";
        }
        String rendered = renderArray(value);
        if (rendered == null) {
            rendered = value.toString();
        }
        return rendered.length() > MAX_ARG_LENGTH
                ? rendered.substring(0, MAX_ARG_LENGTH) + "...(" + rendered.length() + " chars)"
                : rendered;
    }

    /**
     * Renders any array type, or returns null if the value is not an array.
     *
     * <p>{@code getClass().isArray()} is true for primitive arrays too (byte[], int[], ...),
     * but those are <em>not</em> {@code Object[]}, so the old blanket cast to {@code Object[]}
     * threw {@link ClassCastException} the moment a method returned, say, a {@code byte[]}
     * (springdoc's /v3/api-docs does exactly that). Each primitive array type is handled
     * explicitly; {@code byte[]} is summarised rather than dumped so a large binary payload
     * can't flood the log.
     */
    String renderArray(Object value) {
        if (!value.getClass().isArray()) {
            return null;
        }
        if (value instanceof Object[] a)  return Arrays.deepToString(a);
        if (value instanceof byte[] a)    return "byte[" + a.length + "]";
        if (value instanceof int[] a)     return Arrays.toString(a);
        if (value instanceof long[] a)    return Arrays.toString(a);
        if (value instanceof double[] a)  return Arrays.toString(a);
        if (value instanceof boolean[] a) return Arrays.toString(a);
        if (value instanceof char[] a)    return Arrays.toString(a);
        if (value instanceof float[] a)   return Arrays.toString(a);
        if (value instanceof short[] a)   return Arrays.toString(a);
        return value.toString();
    }
}