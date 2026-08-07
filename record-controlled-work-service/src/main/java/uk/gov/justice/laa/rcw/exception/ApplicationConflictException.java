package uk.gov.justice.laa.rcw.exception;

/** The exception thrown when an application update conflicts with a concurrent modification. */
public class ApplicationConflictException extends RuntimeException {

  /**
   * Constructor for ApplicationConflictException.
   *
   * @param message the error message
   */
  public ApplicationConflictException(String message) {
    super(message);
  }
}
