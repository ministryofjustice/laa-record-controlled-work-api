package uk.gov.justice.laa.rcw.exception;

/** The exception thrown when the datastore cannot be reached. */
public class ApplicationUnavailableException extends RuntimeException {

  /**
   * Constructor for ApplicationUnavailableException.
   *
   * @param message the error message
   */
  public ApplicationUnavailableException(String message) {
    super(message);
  }
}
