package uk.gov.justice.laa.rcw.exception;

/** The exception thrown when a request body exceeds the configured size limit. */
public class ApplicationRequestTooLargeException extends ApiRuntimeException {

  public ApplicationRequestTooLargeException() {
    super("Request body exceeds the configured size limit.", "REQUEST_BODY_TOO_LARGE");
  }
}
