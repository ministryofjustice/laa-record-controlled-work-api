package uk.gov.justice.laa.rcw.logging;

/** Canonical {@code event.action} values for structured log events. */
public final class LogAction {

  private LogAction() {}

  // Application actions
  public static final String APPLICATION_LIST = "application.list";
  public static final String APPLICATION_FETCH = "application.fetch";
  public static final String APPLICATION_CREATE = "application.create";
  public static final String APPLICATION_MEANS_UPDATE = "application.means-update";
  public static final String APPLICATION_DECLARATION_UPDATE = "application.declaration-update";
  public static final String APPLICATION_STATUS_UPDATE = "application.status-update";
  public static final String APPLICATION_EVIDENCE_UPDATE = "application.evidence-update";
  public static final String APPLICATION_ERROR = "application.error";
  public static final String APPLICATION_DOWNSTREAM_ERROR = "application.downstream-error";
  public static final String DATASTORE_AUTHORIZATION = "datastore.authorization";
  public static final String DATASTORE_TOKEN_EXCHANGE = "datastore.token-exchange";
  public static final String DATASTORE_OPERATION = "datastore.operation";
  public static final String DATASTORE_TRANSPORT = "datastore.transport";

  // Request actions
  public static final String REQUEST_RECEIVED = "request.received";
  public static final String REQUEST_INVALID = "request.invalid";
  public static final String REQUEST_VALIDATION_FAILED = "request.validation-failed";
}
