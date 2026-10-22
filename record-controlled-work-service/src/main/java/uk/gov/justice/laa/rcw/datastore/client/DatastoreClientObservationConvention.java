package uk.gov.justice.laa.rcw.datastore.client;

import static uk.gov.justice.laa.rcw.datastore.client.DatastoreClientObservability.datastoreOperation;
import static uk.gov.justice.laa.rcw.datastore.client.DatastoreClientObservability.failureCategory;
import static uk.gov.justice.laa.rcw.datastore.client.DatastoreClientObservability.responseStatusCode;
import static uk.gov.justice.laa.rcw.datastore.client.DatastoreClientObservability.safeMethodName;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention;

final class DatastoreClientObservationConvention extends DefaultClientRequestObservationConvention {

  @Override
  public KeyValues getLowCardinalityKeyValues(ClientRequestObservationContext context) {
    HttpRequest request = context.getCarrier();
    String method = safeMethodName(request);
    String operation = datastoreOperation(method, request);
    Integer statusCode = responseStatusCode(context);
    String status =
        statusCode == null
            ? context.getError() == null ? "none" : "io_error"
            : statusCode.toString();
    String failure = failureCategory(context.getError(), statusCode);
    return KeyValues.of(
        KeyValue.of("failure", failure),
        KeyValue.of("method", method),
        KeyValue.of("operation", operation),
        KeyValue.of("phase", "complete"),
        KeyValue.of("status", status));
  }

  @Override
  public KeyValues getHighCardinalityKeyValues(ClientRequestObservationContext context) {
    return KeyValues.empty();
  }

  @Override
  public String getContextualName(ClientRequestObservationContext context) {
    HttpRequest request = context.getCarrier();
    String operation = datastoreOperation(safeMethodName(request), request);
    return "datastore " + operation;
  }
}
