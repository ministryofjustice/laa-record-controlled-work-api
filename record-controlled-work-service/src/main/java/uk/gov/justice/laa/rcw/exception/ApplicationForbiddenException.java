package uk.gov.justice.laa.rcw.exception;

/** The exception thrown when the current user is not authorized for the application's office. */
public class ApplicationForbiddenException extends ApiRuntimeException {

  private static final String DEFAULT_REASON = "OFFICE_NOT_AUTHORIZED";

  /**
   * Constructor for ApplicationForbiddenException.
   *
   * @param message the error message
   */
  public ApplicationForbiddenException(String message) {
    this(message, DEFAULT_REASON);
  }

  /**
   * Constructor for ApplicationForbiddenException.
   *
   * @param message the error message
   * @param reason machine-readable code identifying the exact violation
   */
  public ApplicationForbiddenException(String message, String reason) {
    super(message, reason);
  }
}
