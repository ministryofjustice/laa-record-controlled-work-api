package uk.gov.justice.laa.rcw.service;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.rcw.constants.CorrelationConstants;

/** Supplies request identity values for datastore calls. */
@Component
public class DatastoreRequestContext {

  private final String serviceName;

  /** Creates request context using the configured application name. */
  public DatastoreRequestContext(@Value("${spring.application.name}") String serviceName) {
    this.serviceName = serviceName;
  }

  /** Returns the current correlation ID, generating a UUID when it is absent or blank. */
  public String correlationId() {
    String correlationId = MDC.get(CorrelationConstants.CORRELATION_ID_LOG_KEY);
    return correlationId == null || correlationId.isBlank()
        ? UUID.randomUUID().toString()
        : correlationId;
  }

  /** Returns the configured application name. */
  public String serviceName() {
    return serviceName;
  }
}
