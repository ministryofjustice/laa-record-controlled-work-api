package uk.gov.justice.laa.rcw.exception;

/** The exception thrown when an application is not found. */
public class ApplicationNotFoundException extends RuntimeException {

  /**
   * Constructor for ApplicationNotFoundException.
   *
   * @param message the error message
   */
  public ApplicationNotFoundException(String message) {
    super(message);
  }
}
