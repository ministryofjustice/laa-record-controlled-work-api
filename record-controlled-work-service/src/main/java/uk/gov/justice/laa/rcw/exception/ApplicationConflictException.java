package uk.gov.justice.laa.rcw.exception;

/** The exception thrown when an application update conflicts with a concurrent modification. */
public class ApplicationConflictException extends ApiRuntimeException {

  private static final String DEFAULT_REASON = "CONCURRENT_MODIFICATION";

  /**
   * Constructor for ApplicationConflictException.
   *
   * @param message the error message
   */
  public ApplicationConflictException(String message) {
    this(message, DEFAULT_REASON);
  }

  /**
   * Constructor for ApplicationConflictException.
   *
   * @param message the error message
   * @param reason machine-readable code identifying the exact violation
   */
  public ApplicationConflictException(String message, String reason) {
    super(message, reason);
  }

  /** Constructor for ApplicationConflictException with its downstream cause. */
  public ApplicationConflictException(String message, Throwable cause) {
    this(message, DEFAULT_REASON, cause);
  }

  /** Constructor for ApplicationConflictException with a reason and downstream cause. */
  public ApplicationConflictException(String message, String reason, Throwable cause) {
    super(message, reason, cause);
  }
}
