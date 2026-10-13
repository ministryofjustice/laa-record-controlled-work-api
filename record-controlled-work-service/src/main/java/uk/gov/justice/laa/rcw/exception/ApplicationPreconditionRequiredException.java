package uk.gov.justice.laa.rcw.exception;

/** The exception thrown when an application update omits its required version precondition. */
public class ApplicationPreconditionRequiredException extends ApiRuntimeException {

  public ApplicationPreconditionRequiredException() {
    super("A strong If-Match application version is required.", "IF_MATCH_REQUIRED");
  }
}
