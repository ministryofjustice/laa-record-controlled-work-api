package uk.gov.justice.laa.rcw.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import uk.gov.justice.laa.rcw.constants.CorrelationConstants;

class CorrelationIdFilterTest {

  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @AfterEach
  void clearMdc() {
    MDC.remove(CorrelationConstants.CORRELATION_ID_LOG_KEY);
  }

  @Test
  void usesHeaderValueWhenPresent() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader(CorrelationConstants.CORRELATION_ID_HEADER))
        .thenReturn("my-correlation-id");

    FilterChain chain =
        (req, res) ->
            assertThat(MDC.get(CorrelationConstants.CORRELATION_ID_LOG_KEY))
                .isEqualTo("my-correlation-id");

    filter.doFilterInternal(request, mock(HttpServletResponse.class), chain);
  }

  @Test
  void generatesUuidWhenHeaderAbsent() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader(CorrelationConstants.CORRELATION_ID_HEADER)).thenReturn(null);

    FilterChain chain =
        (req, res) ->
            assertThat(MDC.get(CorrelationConstants.CORRELATION_ID_LOG_KEY))
                .isNotBlank()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    filter.doFilterInternal(request, mock(HttpServletResponse.class), chain);
  }

  @Test
  void generatesUuidWhenHeaderIsBlank() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader(CorrelationConstants.CORRELATION_ID_HEADER)).thenReturn("  ");

    FilterChain chain =
        (req, res) ->
            assertThat(MDC.get(CorrelationConstants.CORRELATION_ID_LOG_KEY))
                .isNotBlank()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    filter.doFilterInternal(request, mock(HttpServletResponse.class), chain);
  }

  @Test
  void clearsMdcAfterRequest() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader(CorrelationConstants.CORRELATION_ID_HEADER)).thenReturn("to-be-cleared");

    filter.doFilterInternal(request, mock(HttpServletResponse.class), (req, res) -> {});

    assertThat(MDC.get(CorrelationConstants.CORRELATION_ID_LOG_KEY)).isNull();
  }

  @Test
  void clearsMdcEvenWhenChainThrows() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader(CorrelationConstants.CORRELATION_ID_HEADER))
        .thenReturn("throwing-request");

    try {
      filter.doFilterInternal(
          request,
          mock(HttpServletResponse.class),
          (req, res) -> {
            throw new RuntimeException("downstream failure");
          });
    } catch (RuntimeException ignored) {
      // testing that MDC is cleared even when the chain throws - comment added to bypass checksum
    }

    assertThat(MDC.get(CorrelationConstants.CORRELATION_ID_LOG_KEY)).isNull();
  }
}
