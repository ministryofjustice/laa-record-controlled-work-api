package uk.gov.justice.laa.rcw.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import uk.gov.justice.laa.rcw.constants.CorrelationConstants;

class DatastoreRequestContextTest {

  private final DatastoreRequestContext requestContext =
      new DatastoreRequestContext("laa-record-controlled-work-api");

  @AfterEach
  void clearMdc() {
    MDC.remove(CorrelationConstants.CORRELATION_ID_LOG_KEY);
  }

  @Test
  void shouldReturnMdcCorrelationIdWhenPresent() {
    MDC.put(CorrelationConstants.CORRELATION_ID_LOG_KEY, "request-correlation-id");

    assertThat(requestContext.correlationId()).isEqualTo("request-correlation-id");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "  ", "\t"})
  void shouldGenerateUuidWhenMdcCorrelationIdIsMissingOrBlank(String correlationId) {
    if (correlationId == null) {
      MDC.remove(CorrelationConstants.CORRELATION_ID_LOG_KEY);
    } else {
      MDC.put(CorrelationConstants.CORRELATION_ID_LOG_KEY, correlationId);
    }

    String generatedCorrelationId = requestContext.correlationId();

    assertThat(UUID.fromString(generatedCorrelationId).toString())
        .isEqualTo(generatedCorrelationId);
  }

  @Test
  void shouldReturnConfiguredServiceName() {
    assertThat(requestContext.serviceName()).isEqualTo("laa-record-controlled-work-api");
  }
}
