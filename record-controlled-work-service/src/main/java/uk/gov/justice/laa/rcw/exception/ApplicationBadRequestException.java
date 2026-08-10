package uk.gov.justice.laa.rcw.exception;

/** The exception thrown when the datastore rejects a request as invalid. */
public class ApplicationBadRequestException extends RuntimeException {

  /**
   * Constructor for ApplicationBadRequestException.
   *
   * @param message the error message
   */
  public ApplicationBadRequestException(String message) {
    super(message);
  }
}
