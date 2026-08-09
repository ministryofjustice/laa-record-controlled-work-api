package uk.gov.justice.laa.rcw.exception;

/** The exception thrown when the datastore returns a server error. */
public class ApplicationUpstreamErrorException extends RuntimeException {

  /**
   * Constructor for ApplicationUpstreamErrorException.
   *
   * @param message the error message
   */
  public ApplicationUpstreamErrorException(String message) {
    super(message);
  }
}
