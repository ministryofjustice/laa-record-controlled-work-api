package uk.gov.justice.laa.rcw.exception;

/** The exception thrown when the current user is not authorized for the application's office. */
public class ApplicationForbiddenException extends RuntimeException {

  /**
   * Constructor for ApplicationForbiddenException.
   *
   * @param message the error message
   */
  public ApplicationForbiddenException(String message) {
    super(message);
  }
}
