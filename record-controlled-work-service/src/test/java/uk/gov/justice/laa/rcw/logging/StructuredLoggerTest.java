package uk.gov.justice.laa.rcw.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_ERROR;
import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_FETCH;
import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_LIST;
import static uk.gov.justice.laa.rcw.logging.LogAction.REQUEST_INVALID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

class StructuredLoggerTest {

  private Logger logger;
  private ListAppender<ILoggingEvent> appender;
  private StructuredLogger log;

  @BeforeEach
  void setUp() {
    logger = (Logger) LoggerFactory.getLogger(StructuredLoggerTest.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    log = StructuredLogger.of(logger);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
  }

  @Test
  void info_emitsAtInfoLevelWithRequiredFields() {
    log.info().action(APPLICATION_LIST).outcome("success").log("Retrieved {} items", 3);

    ILoggingEvent event = singleEvent();
    assertThat(event.getLevel()).isEqualTo(Level.INFO);
    assertThat(event.getFormattedMessage()).isEqualTo("Retrieved 3 items");
    assertThat(keyValue(event, "event.action")).isEqualTo(APPLICATION_LIST);
    assertThat(keyValue(event, "event.outcome")).isEqualTo("success");
  }

  @Test
  void info_withFailureOutcome_emitsFailure() {
    log.info().action(APPLICATION_FETCH).outcome("failure").log("Degraded path");

    assertThat(keyValue(singleEvent(), "event.outcome")).isEqualTo("failure");
  }

  @Test
  void warn_emitsAtWarnLevelWithRequiredFields() {
    log.warn().action(REQUEST_INVALID).outcome("failure").log("Invalid request: {}", 99L);

    ILoggingEvent event = singleEvent();
    assertThat(event.getLevel()).isEqualTo(Level.WARN);
    assertThat(event.getFormattedMessage()).isEqualTo("Invalid request: 99");
    assertThat(keyValue(event, "event.action")).isEqualTo(REQUEST_INVALID);
    assertThat(keyValue(event, "event.outcome")).isEqualTo("failure");
  }

  @Test
  void debug_emitsAtDebugLevel() {
    logger.setLevel(Level.DEBUG);

    log.debug().action(APPLICATION_FETCH).outcome("success").log("Cache hit for item {}", 7L);

    ILoggingEvent event = singleEvent();
    assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
    assertThat(keyValue(event, "event.action")).isEqualTo(APPLICATION_FETCH);
    assertThat(keyValue(event, "event.outcome")).isEqualTo("success");
  }

  @Test
  void error_implicitlyUsesFailureOutcome() {
    log.error().action(APPLICATION_ERROR).outcome("failure").log("Something went wrong");

    ILoggingEvent event = singleEvent();
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    assertThat(keyValue(event, "event.action")).isEqualTo(APPLICATION_ERROR);
    assertThat(keyValue(event, "event.outcome")).isEqualTo("failure");
  }

  @Test
  void error_withCause_attachesThrowableAndImpliesFailure() {
    RuntimeException cause = new RuntimeException("database unavailable");

    log.error(cause).action(APPLICATION_ERROR).outcome("failure").log("Unexpected error");

    ILoggingEvent event = singleEvent();
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    assertThat(event.getThrowableProxy()).isNotNull();
    assertThat(event.getThrowableProxy().getMessage()).isEqualTo("database unavailable");
    assertThat(keyValue(event, "event.outcome")).isEqualTo("failure");
  }

  @Test
  void with_includesExtraKeyValuePairs() {
    log.info()
        .action(APPLICATION_FETCH)
        .outcome("success")
        .with("item.id", 42L)
        .with("http.response.status_code", 200)
        .log("Retrieved item");

    ILoggingEvent event = singleEvent();
    assertThat(keyValue(event, "item.id")).isEqualTo(42L);
    assertThat(keyValue(event, "http.response.status_code")).isEqualTo(200);
  }

  @Test
  void with_preservesInsertionOrder() {
    log.info()
        .action(APPLICATION_FETCH)
        .outcome("success")
        .with("first", 1)
        .with("second", 2)
        .with("third", 3)
        .log("Order check");

    assertThat(singleEvent().getKeyValuePairs())
        .extracting(kv -> kv.key)
        .containsSubsequence("first", "second", "third");
  }

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
  void with_redactsSensitiveFields(String field) {
    log.info()
        .action(APPLICATION_FETCH)
        .outcome("success")
        .with(field, "sensitive-test-value")
        .log("Redaction check");

    assertThat(keyValue(singleEvent(), field)).isEqualTo("[REDACTED]");
  }

  @Test
  void with_redactsNestedFieldsWithoutMutatingInput() {
    Map<String, Object> headers = Map.of("authorization", "sensitive-test-value", "status", 200);
    Map<String, Object> request = Map.of("headers", headers);
    List<Map<String, Object>> items = List.of(Map.of("firstName", "sensitive-test-value"));

    log.info()
        .action(APPLICATION_FETCH)
        .outcome("success")
        .with("req", request)
        .with("items", items)
        .with("array", items.toArray())
        .log("Nested redaction check");

    ILoggingEvent event = singleEvent();
    assertThat(keyValue(event, "req"))
        .isEqualTo(Map.of("headers", Map.of("authorization", "[REDACTED]", "status", 200)));
    assertThat(keyValue(event, "items")).isEqualTo(List.of(Map.of("firstName", "[REDACTED]")));
    assertThat(keyValue(event, "array")).isEqualTo(List.of(Map.of("firstName", "[REDACTED]")));
    assertThat(headers).containsEntry("authorization", "sensitive-test-value");
    assertThat(items.getFirst()).containsEntry("firstName", "sensitive-test-value");
  }

  @Test
  void log_redactsStructuredArgumentsWithoutMutatingInput() {
    Map<String, Object> argument = Map.of("token", "sensitive-test-value", "status", 200);

    log.info().action(APPLICATION_FETCH).outcome("success").log("Request {}", argument);

    assertThat(singleEvent().getFormattedMessage())
        .contains("[REDACTED]", "200")
        .doesNotContain("sensitive-test-value");
    assertThat(argument).containsEntry("token", "sensitive-test-value");
  }

  private ILoggingEvent singleEvent() {
    assertThat(appender.list).hasSize(1);
    return appender.list.getFirst();
  }

  private Object keyValue(ILoggingEvent event, String key) {
    return event.getKeyValuePairs().stream()
        .filter(kv -> kv.key.equals(key))
        .map(kv -> kv.value)
        .findFirst()
        .orElse(null);
  }
}
