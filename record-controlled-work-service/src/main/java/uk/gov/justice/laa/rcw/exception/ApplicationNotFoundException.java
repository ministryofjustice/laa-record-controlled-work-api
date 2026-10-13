package uk.gov.justice.laa.rcw.exception;

/** The exception thrown when an application is not found. */
public class ApplicationNotFoundException extends ApiRuntimeException {

  private static final String DEFAULT_REASON = "APPLICATION_NOT_FOUND";

  /**
   * Constructor for ApplicationNotFoundException.
   *
   * @param message the error message
   */
  public ApplicationNotFoundException(String message) {
    this(message, DEFAULT_REASON);
  }

  /**
   * Constructor for ApplicationNotFoundException.
   *
   * @param message the error message
   * @param reason machine-readable code identifying the exact violation
   */
  public ApplicationNotFoundException(String message, String reason) {
    super(message, reason);
  }

  /** Constructor for ApplicationNotFoundException with its downstream cause. */
  public ApplicationNotFoundException(String message, Throwable cause) {
    this(message, DEFAULT_REASON, cause);
  }

  /** Constructor for ApplicationNotFoundException with a reason and downstream cause. */
  public ApplicationNotFoundException(String message, String reason, Throwable cause) {
    super(message, reason, cause);
  }
}
