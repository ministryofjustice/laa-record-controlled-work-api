package uk.gov.justice.laa.rcw.logging;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * Structured logger that enforces required event fields ({@code event.action} and {@code
 * event.outcome}) before a log message can be emitted.
 *
 * <p>Declare as a static field in place of {@code @Slf4j}:
 *
 * <pre>{@code
 * private static final StructuredLogger log = StructuredLogger.of(MyService.class);
 * }</pre>
 *
 * <p>{@code info()} and {@code warn()} require an explicit {@code .success()} or {@code .failure()}
 * call. {@code error()} skips that stage — outcome is always {@code failure}:
 *
 * <pre>{@code
 * log.info().action("item.list").success().log("Retrieved {} items", count);
 * log.warn().action("item.not-found").failure().with("item.id", id).log("Item not found: {}", id);
 * log.error(exception).action("application.error").log("An unexpected error occurred");
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

  /** Begins a DEBUG event. Outcome ({@code success} or {@code failure}) must be specified. */
  public ActionStage debug() {
    return new StandardBuilder(logger.atDebug());
  }

  /** Begins an INFO event. Outcome ({@code success} or {@code failure}) must be specified. */
  public ActionStage info() {
    return new StandardBuilder(logger.atInfo());
  }

  /** Begins a WARN event. Outcome ({@code success} or {@code failure}) must be specified. */
  public ActionStage warn() {
    return new StandardBuilder(logger.atWarn());
  }

  /** Begins an ERROR event. Outcome is always {@code failure} — no need to specify it. */
  public ErrorActionStage error() {
    return new ErrorBuilder(logger.atError());
  }

  /**
   * Begins an ERROR event with a cause. Outcome is always {@code failure} — no need to specify it.
   */
  public ErrorActionStage error(Throwable cause) {
    return new ErrorBuilder(logger.atError().setCause(cause));
  }

  /** Stage for {@code info()} and {@code warn()}: action then explicit outcome. */
  public interface ActionStage {
    OutcomeStage action(String action);
  }

  /** Stage for {@code error()}: action then directly to build — outcome is implicit. */
  public interface ErrorActionStage {
    BuildStage action(String action);
  }

  /** Outcome selection for {@code info()} and {@code warn()} events. */
  public interface OutcomeStage {
    BuildStage success();

    BuildStage failure();
  }

  /** Optional extra key-value fields, then the terminal {@code log()} call. */
  public interface BuildStage {
    BuildStage with(String key, Object value);

    void log(String message, Object... args);
  }

  private static final class StandardBuilder implements ActionStage, OutcomeStage, BuildStage {

    private final LoggingEventBuilder builder;
    private String action;
    private String outcome;
    private final Map<String, Object> extras = new LinkedHashMap<>();

    StandardBuilder(LoggingEventBuilder builder) {
      this.builder = builder;
    }

    @Override
    public OutcomeStage action(String action) {
      this.action = action;
      return this;
    }

    @Override
    public BuildStage success() {
      this.outcome = "success";
      return this;
    }

    @Override
    public BuildStage failure() {
      this.outcome = "failure";
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

  private static final class ErrorBuilder implements ErrorActionStage, BuildStage {

    private final LoggingEventBuilder builder;
    private String action;
    private final Map<String, Object> extras = new LinkedHashMap<>();

    ErrorBuilder(LoggingEventBuilder builder) {
      this.builder = builder;
    }

    @Override
    public BuildStage action(String action) {
      this.action = action;
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
          builder.addKeyValue("event.action", action).addKeyValue("event.outcome", "failure");
      for (Map.Entry<String, Object> entry : extras.entrySet()) {
        b = b.addKeyValue(entry.getKey(), entry.getValue());
      }
      b.log(message, args);
    }
  }
}
