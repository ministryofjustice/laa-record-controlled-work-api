package uk.gov.justice.laa.rcw.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jspecify.annotations.NonNull;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Emits a structured JSON log event for each incoming HTTP request. Runs after {@link
 * CorrelationIdFilter} so {@code correlationId} is already in MDC. Actuator paths are excluded to
 * avoid health-probe noise. Replaces {@code CommonsRequestLoggingFilter}.
 */
@Component
@Order(2)
public class RequestLoggingFilter extends OncePerRequestFilter {

  private static final StructuredLogger log = StructuredLogger.of(RequestLoggingFilter.class);

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String method = request.getMethod();
    String path = request.getRequestURI();
    String clientIp = resolveClientIp(request);
    long startTime = System.currentTimeMillis();

    try {
      chain.doFilter(request, response);
    } finally {
      long duration = System.currentTimeMillis() - startTime;
      log.info()
          .action(LogAction.REQUEST_RECEIVED)
          .success()
          .with("http.request.method", method)
          .with("url.path", path)
          .with("http.response.status_code", response.getStatus())
          .with("client.ip", clientIp)
          .with("event.duration_ms", duration)
          .log("Request handled");
    }
  }

  private String resolveClientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getRequestURI().startsWith("/actuator");
  }
}
