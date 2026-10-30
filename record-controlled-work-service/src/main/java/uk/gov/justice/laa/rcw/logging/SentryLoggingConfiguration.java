package uk.gov.justice.laa.rcw.logging;

import io.sentry.SentryLogEventAttributeValue;
import io.sentry.SentryLogLevel;
import io.sentry.SentryOptions;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Applies field redaction to Sentry Logs before they leave the application. */
@Configuration(proxyBeanMethods = false)
public class SentryLoggingConfiguration {

  /**
   * Creates the callback used to scrub structured breadcrumb data.
   *
   * @return the Sentry breadcrumb redaction callback
   */
  @Bean
  public SentryOptions.BeforeBreadcrumbCallback sentryBreadcrumbRedaction() {
    return (breadcrumb, hint) -> {
      breadcrumb.getData().replaceAll(LogRedactor::redact);
      return breadcrumb;
    };
  }

  /**
   * Creates the callback used to scrub structured error event data.
   *
   * @return the Sentry error event redaction callback
   */
  @Bean
  public SentryOptions.BeforeSendCallback sentryEventRedaction() {
    return (event, hint) -> {
      if (event.getExtras() != null) {
        event.getExtras().replaceAll(LogRedactor::redact);
      }
      if (event.getTags() != null) {
        event.getTags().replaceAll((key, value) -> (String) LogRedactor.redact(key, value));
      }
      if (event.getBreadcrumbs() != null) {
        event
            .getBreadcrumbs()
            .forEach(breadcrumb -> breadcrumb.getData().replaceAll(LogRedactor::redact));
      }
      return event;
    };
  }

  /**
   * Creates the callback used by Sentry to redact and sample log attributes before upload.
   * Redacts sensitive data and applies sampling based on severity: ERROR and WARN levels are
   * always sent, INFO logs are sampled at 10% to reduce quota consumption while maintaining
   * visibility into application health.
   *
   * @return the Sentry Logs redaction and sampling callback
   */
  @Bean
  public SentryOptions.Logs.BeforeSendLogCallback sentryLogRedaction() {
    return event -> {
      if (event == null) {
        return null;
      }

      // Apply sampling based on log level
      SentryLogLevel level = event.getLevel();
      if (level == null) {
        return null;
      }

      // Always send errors and warnings; sample info logs at 10%
      if (SentryLogLevel.ERROR != level && SentryLogLevel.WARN != level) {
        if (Math.random() >= 0.1) {
          return null;
        }
      }

      // Apply redaction to log attributes
      if (event.getAttributes() != null) {
        Map<String, SentryLogEventAttributeValue> attributes = new LinkedHashMap<>();
        event
            .getAttributes()
            .forEach(
                (key, attribute) -> {
                  Object value = attribute == null ? null : attribute.getValue();
                  Object redacted = LogRedactor.redact(key, value);
                  attributes.put(
                      key,
                      redacted == value
                          ? attribute
                          : new SentryLogEventAttributeValue("string", redacted));
                });
        event.setAttributes(attributes);
      }

      return event;
    };
  }
}
