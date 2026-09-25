package uk.gov.justice.laa.rcw.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import uk.gov.justice.laa.rcw.constants.CorrelationConstants;

/**
 * Propagates the {@code X-Correlation-Id} request header into MDC as {@code correlationId},
 * generating a UUID when the header is absent. The MDC key is cleared after each request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain chain)
      throws ServletException, IOException {
    String correlationId = request.getHeader(CorrelationConstants.CORRELATION_ID_HEADER);
    if (correlationId == null || correlationId.isBlank()) {
      correlationId = UUID.randomUUID().toString();
    }
    MDC.put(CorrelationConstants.CORRELATION_ID_LOG_KEY, correlationId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(CorrelationConstants.CORRELATION_ID_LOG_KEY);
    }
  }
}
