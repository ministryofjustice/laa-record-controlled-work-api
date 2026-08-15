package uk.gov.justice.laa.rcw.exception;

/** The exception thrown when the datastore cannot be reached. */
public class ApplicationUnavailableException extends ApiRuntimeException {

  private static final String DEFAULT_REASON = "DATASTORE_UNAVAILABLE";

  /**
   * Constructor for ApplicationUnavailableException.
   *
   * @param message the error message
   */
  public ApplicationUnavailableException(String message) {
    this(message, DEFAULT_REASON);
  }

  /**
   * Constructor for ApplicationUnavailableException.
   *
   * @param message the error message
   * @param reason machine-readable code identifying the exact violation
   */
  public ApplicationUnavailableException(String message, String reason) {
    super(message, reason);
  }
}
