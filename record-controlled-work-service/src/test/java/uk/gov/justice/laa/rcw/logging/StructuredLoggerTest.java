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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    log.info().action(APPLICATION_LIST).success().log("Retrieved {} items", 3);

    ILoggingEvent event = singleEvent();
    assertThat(event.getLevel()).isEqualTo(Level.INFO);
    assertThat(event.getFormattedMessage()).isEqualTo("Retrieved 3 items");
    assertThat(keyValue(event, "event.action")).isEqualTo(APPLICATION_LIST);
    assertThat(keyValue(event, "event.outcome")).isEqualTo("success");
  }

  @Test
  void info_withFailureOutcome_emitsFailure() {
    log.info().action(APPLICATION_FETCH).failure().log("Degraded path");

    assertThat(keyValue(singleEvent(), "event.outcome")).isEqualTo("failure");
  }

  @Test
  void warn_emitsAtWarnLevelWithRequiredFields() {
    log.warn().action(REQUEST_INVALID).failure().log("Invalid request: {}", 99L);

    ILoggingEvent event = singleEvent();
    assertThat(event.getLevel()).isEqualTo(Level.WARN);
    assertThat(event.getFormattedMessage()).isEqualTo("Invalid request: 99");
    assertThat(keyValue(event, "event.action")).isEqualTo(REQUEST_INVALID);
    assertThat(keyValue(event, "event.outcome")).isEqualTo("failure");
  }

  @Test
  void debug_emitsAtDebugLevel() {
    logger.setLevel(Level.DEBUG);

    log.debug().action(APPLICATION_FETCH).success().log("Cache hit for item {}", 7L);

    ILoggingEvent event = singleEvent();
    assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
    assertThat(keyValue(event, "event.action")).isEqualTo(APPLICATION_FETCH);
    assertThat(keyValue(event, "event.outcome")).isEqualTo("success");
  }

  @Test
  void error_implicitlyUsesFailureOutcome() {
    log.error().action(APPLICATION_ERROR).log("Something went wrong");

    ILoggingEvent event = singleEvent();
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    assertThat(keyValue(event, "event.action")).isEqualTo(APPLICATION_ERROR);
    assertThat(keyValue(event, "event.outcome")).isEqualTo("failure");
  }

  @Test
  void error_withCause_attachesThrowableAndImpliesFailure() {
    RuntimeException cause = new RuntimeException("database unavailable");

    log.error(cause).action(APPLICATION_ERROR).log("Unexpected error");

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
        .success()
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
        .success()
        .with("first", 1)
        .with("second", 2)
        .with("third", 3)
        .log("Order check");

    assertThat(singleEvent().getKeyValuePairs())
        .extracting(kv -> kv.key)
        .containsSubsequence("first", "second", "third");
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
