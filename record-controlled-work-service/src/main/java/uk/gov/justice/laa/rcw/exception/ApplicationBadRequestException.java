package uk.gov.justice.laa.rcw.exception;

/** The exception thrown when the datastore rejects a request as invalid. */
public class ApplicationBadRequestException extends ApiRuntimeException {

  private static final String DEFAULT_REASON = "DATASTORE_REJECTED_REQUEST";

  /**
   * Constructor for ApplicationBadRequestException.
   *
   * @param message the error message
   */
  public ApplicationBadRequestException(String message) {
    this(message, DEFAULT_REASON);
  }

  /**
   * Constructor for ApplicationBadRequestException.
   *
   * @param message the error message
   * @param reason machine-readable code identifying the exact violation
   */
  public ApplicationBadRequestException(String message, String reason) {
    super(message, reason);
  }
}
