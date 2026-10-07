package uk.gov.justice.laa.rcw.exception;

/** The exception thrown when the datastore returns a server error. */
public class ApplicationUpstreamErrorException extends ApiRuntimeException {

  private static final String DEFAULT_REASON = "DATASTORE_SERVER_ERROR";

  /**
   * Constructor for ApplicationUpstreamErrorException.
   *
   * @param message the error message
   */
  public ApplicationUpstreamErrorException(String message) {
    this(message, DEFAULT_REASON);
  }

  /**
   * Constructor for ApplicationUpstreamErrorException.
   *
   * @param message the error message
   * @param reason machine-readable code identifying the exact violation
   */
  public ApplicationUpstreamErrorException(String message, String reason) {
    super(message, reason);
  }

  /** Constructor for ApplicationUpstreamErrorException with its downstream cause. */
  public ApplicationUpstreamErrorException(String message, Throwable cause) {
    this(message, DEFAULT_REASON, cause);
  }

  /** Constructor for ApplicationUpstreamErrorException with a reason and downstream cause. */
  public ApplicationUpstreamErrorException(String message, String reason, Throwable cause) {
    super(message, reason, cause);
  }
}
