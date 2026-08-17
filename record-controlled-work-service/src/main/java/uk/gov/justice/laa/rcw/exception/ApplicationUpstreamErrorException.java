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
}
