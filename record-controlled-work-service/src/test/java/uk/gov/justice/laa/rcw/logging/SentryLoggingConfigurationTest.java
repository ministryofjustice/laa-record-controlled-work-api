package uk.gov.justice.laa.rcw.logging;

import static org.assertj.core.api.Assertions.assertThat;

import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryLogEvent;
import io.sentry.SentryLogEventAttributeValue;
import io.sentry.SentryLogLevel;
import io.sentry.protocol.SentryId;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SentryLoggingConfigurationTest {

  private final SentryLoggingConfiguration configuration = new SentryLoggingConfiguration();

  @ParameterizedTest
  @ValueSource(
      strings = {
        "accessToken",
        "address",
        "apiKey",
        "authorization",
        "cookie",
        "dateOfBirth",
        "firstName",
        "headers.authorization",
        "headers.cookie",
        "idToken",
        "lastName",
        "niNumber",
        "password",
        "refreshToken",
        "req.headers.authorization",
        "req.headers.cookie",
        "secret",
        "token",
        "Authorization",
        "COOKIE"
      })
  void shouldRedactSensitiveSentryAttributes(String field) {
    SentryLogEvent event =
        new SentryLogEvent(new SentryId(), 0.0, "Test event", SentryLogLevel.INFO);
    event.setAttributes(
        Map.of(
            field,
            new SentryLogEventAttributeValue("string", "sensitive-test-value"),
            "http.response.status_code",
            new SentryLogEventAttributeValue("integer", 200)));

    SentryLogEvent result = configuration.sentryLogRedaction().execute(event);

    assertThat(result).isSameAs(event);
    assertThat(result.getAttributes().get(field).getValue()).isEqualTo("[REDACTED]");
    assertThat(result.getAttributes().get(field).getType()).isEqualTo("string");
    assertThat(result.getAttributes().get("http.response.status_code").getValue()).isEqualTo(200);
    assertThat(result.getAttributes().get("http.response.status_code").getType())
        .isEqualTo("integer");
    assertThat(result.getBody()).isEqualTo("Test event");
  }

  @Test
  void shouldRedactBreadcrumbData() {
    Breadcrumb breadcrumb = new Breadcrumb();
    breadcrumb.setData("req", Map.of("headers", Map.of("cookie", "sensitive-test-value")));
    breadcrumb.setData("status", 200);

    assertThat(configuration.sentryBreadcrumbRedaction().execute(breadcrumb, new Hint()))
        .isSameAs(breadcrumb);
    assertThat(breadcrumb.getData())
        .containsEntry("req", Map.of("headers", Map.of("cookie", "[REDACTED]")))
        .containsEntry("status", 200);
  }

  @Test
  void shouldRedactErrorEventFieldsAndBreadcrumbs() {
    SentryEvent event = new SentryEvent();
    event.setExtra("req.headers.authorization", "sensitive-test-value");
    event.setExtra("status", 200);
    event.setTag("apiKey", "sensitive-test-value");
    Breadcrumb breadcrumb = new Breadcrumb();
    breadcrumb.setData("password", "sensitive-test-value");
    event.addBreadcrumb(breadcrumb);

    assertThat(configuration.sentryEventRedaction().execute(event, new Hint())).isSameAs(event);
    assertThat(event.getExtras())
        .containsEntry("req.headers.authorization", "[REDACTED]")
        .containsEntry("status", 200);
    assertThat(event.getTags()).containsEntry("apiKey", "[REDACTED]");
    assertThat(breadcrumb.getData()).containsEntry("password", "[REDACTED]");
  }

  @Test
  void shouldPreserveEmptyErrorEvents() {
    SentryEvent event = new SentryEvent();

    assertThat(configuration.sentryEventRedaction().execute(event, new Hint())).isSameAs(event);
  }

  @Test
  void shouldPreserveLogsWithoutAttributes() {
    SentryLogEvent event =
        new SentryLogEvent(new SentryId(), 0.0, "Test event", SentryLogLevel.INFO);

    assertThat(configuration.sentryLogRedaction().execute(event)).isSameAs(event);
    assertThat(event.getAttributes()).isNull();
  }

  @Test
  void shouldAlwaysSendErrorLogs() {
    SentryLogEvent event =
        new SentryLogEvent(new SentryId(), 0.0, "Error event", SentryLogLevel.ERROR);

    SentryLogEvent result = configuration.sentryLogSampling().execute(event);

    assertThat(result).isSameAs(event);
  }

  @Test
  void shouldAlwaysSendWarningLogs() {
    SentryLogEvent event =
        new SentryLogEvent(new SentryId(), 0.0, "Warning event", SentryLogLevel.WARNING);

    SentryLogEvent result = configuration.sentryLogSampling().execute(event);

    assertThat(result).isSameAs(event);
  }

  @Test
  void shouldNotSendNullEvent() {
    SentryLogEvent result = configuration.sentryLogSampling().execute(null);

    assertThat(result).isNull();
  }

  @Test
  void shouldNotSendEventWithoutLevel() {
    SentryLogEvent event =
        new SentryLogEvent(new SentryId(), 0.0, "No level event", null);

    SentryLogEvent result = configuration.sentryLogSampling().execute(event);

    assertThat(result).isNull();
  }
}
