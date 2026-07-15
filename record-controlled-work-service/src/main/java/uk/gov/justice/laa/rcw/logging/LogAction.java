package uk.gov.justice.laa.rcw.logging;

/** Canonical {@code event.action} values for structured log events. */
public final class LogAction {

  private LogAction() {}

  // Application actions
  public static final String APPLICATION_LIST = "application.list";
  public static final String APPLICATION_FETCH = "application.fetch";
  public static final String APPLICATION_CREATE = "application.create";
  public static final String APPLICATION_ERROR = "application.error";

  // Request actions
  public static final String REQUEST_INVALID = "request.invalid";
  public static final String REQUEST_VALIDATION_FAILED = "request.validation-failed";
}
