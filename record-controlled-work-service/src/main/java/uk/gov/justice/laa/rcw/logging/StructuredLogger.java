package uk.gov.justice.laa.rcw.logging;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * Structured logger that enforces required {@code event.action} and {@code event.outcome} fields
 * before a log message can be emitted.
 *
 * <p>Declare as a static field in place of {@code @Slf4j}:
 *
 * <pre>{@code
 * private static final StructuredLogger log = StructuredLogger.of(MyService.class);
 * }</pre>
 *
 * <p>Usage examples:
 *
 * <pre>{@code
 * log.info().action("application.list").outcome("success").log("Retrieved {} items", count);
 * log.warn().action("request.invalid").outcome("failure").with("item.id", id).log("Not found");
 * log.error(exception).action("application.error").outcome("failure").log("Unexpected error");
 * }</pre>
 */
public final class StructuredLogger {

  private final Logger logger;

  private StructuredLogger(Logger logger) {
    this.logger = logger;
  }

  /** Creates a {@code StructuredLogger} for the given class. */
  public static StructuredLogger of(Class<?> clazz) {
    return new StructuredLogger(LoggerFactory.getLogger(clazz));
  }

  /** Creates a {@code StructuredLogger} wrapping an existing SLF4J {@link Logger}. */
  public static StructuredLogger of(Logger logger) {
    return new StructuredLogger(logger);
  }

  /** Begins a DEBUG event. */
  public ActionStage debug() {
    return new Builder(logger.atDebug());
  }

  /** Begins an INFO event. */
  public ActionStage info() {
    return new Builder(logger.atInfo());
  }

  /** Begins a WARN event. */
  public ActionStage warn() {
    return new Builder(logger.atWarn());
  }

  /** Begins an ERROR event. */
  public ActionStage error() {
    return new Builder(logger.atError());
  }

  /** Begins an ERROR event with a cause. */
  public ActionStage error(Throwable cause) {
    return new Builder(logger.atError().setCause(cause));
  }

  /** First stage: set the required {@code event.action}. */
  public interface ActionStage {
    OutcomeStage action(String action);
  }

  /** Second stage: set the required {@code event.outcome}. */
  public interface OutcomeStage {
    BuildStage outcome(String outcome);
  }

  /** Optional extra key-value fields, then the terminal {@code log()} call. */
  public interface BuildStage {
    BuildStage with(String key, Object value);

    void log(String message, Object... args);
  }

  private static final class Builder implements ActionStage, OutcomeStage, BuildStage {

    private final LoggingEventBuilder builder;
    private String action;
    private String outcome;
    private final Map<String, Object> extras = new LinkedHashMap<>();

    Builder(LoggingEventBuilder builder) {
      this.builder = builder;
    }

    @Override
    public OutcomeStage action(String action) {
      this.action = action;
      return this;
    }

    @Override
    public BuildStage outcome(String outcome) {
      this.outcome = outcome;
      return this;
    }

    @Override
    public BuildStage with(String key, Object value) {
      this.extras.put(key, value);
      return this;
    }

    @Override
    public void log(String message, Object... args) {
      LoggingEventBuilder b =
          builder.addKeyValue("event.action", action).addKeyValue("event.outcome", outcome);
      for (Map.Entry<String, Object> entry : extras.entrySet()) {
        b = b.addKeyValue(entry.getKey(), entry.getValue());
      }
      b.log(message, args);
    }
  }
}
