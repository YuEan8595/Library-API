package com.library.api.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Gives every request a correlation id and exposes it two ways: in the SLF4J {@link MDC}
 * (so the log pattern can print it on every line the request produces) and in an
 * {@code X-Correlation-Id} response header (so a caller who reports a failure can quote the
 * exact id to grep for).
 *
 * <p>This is the piece that makes {@link LoggingAspect}'s output traceable: without a shared
 * id, concurrent requests interleave their trace lines and you cannot tell which "threw" line
 * belongs to which "-->" line. If the caller already sent an {@code X-Correlation-Id}, it is
 * honoured so a trace can span several services.
 *
 * <p>Ordered {@code HIGHEST_PRECEDENCE} so the id is in place before anything else logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString().substring(0, 8);
        }

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Threads are pooled and reused, so the id must be cleared or it leaks into the
            // next, unrelated request that happens to land on this thread.
            MDC.remove(MDC_KEY);
        }
    }
}
