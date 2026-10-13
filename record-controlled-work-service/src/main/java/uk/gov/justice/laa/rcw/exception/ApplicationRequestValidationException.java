package uk.gov.justice.laa.rcw.exception;

/** The exception thrown when an application request fails endpoint validation. */
public class ApplicationRequestValidationException extends ApiRuntimeException {

  public ApplicationRequestValidationException(String reason) {
    super("Invalid request content.", reason);
  }
}
