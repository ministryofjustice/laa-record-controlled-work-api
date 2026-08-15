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
}
