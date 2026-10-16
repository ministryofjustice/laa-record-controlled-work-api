package uk.gov.justice.laa.rcw.datastore.client;

import static uk.gov.justice.laa.rcw.datastore.client.DatastoreClientObservability.datastoreOperation;
import static uk.gov.justice.laa.rcw.datastore.client.DatastoreClientObservability.failureCategory;
import static uk.gov.justice.laa.rcw.datastore.client.DatastoreClientObservability.logDatastoreEvent;
import static uk.gov.justice.laa.rcw.datastore.client.DatastoreClientObservability.recordDistribution;
import static uk.gov.justice.laa.rcw.datastore.client.DatastoreClientObservability.recordPoolStats;
import static uk.gov.justice.laa.rcw.datastore.client.DatastoreClientObservability.responseStatusCode;
import static uk.gov.justice.laa.rcw.datastore.client.DatastoreClientObservability.safeMethod;
import static uk.gov.justice.laa.rcw.logging.LogAction.DATASTORE_OPERATION;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import java.util.Map;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.pool.PoolStats;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.observation.ClientRequestObservationContext;

final class DatastoreOperationObservationHandler
    implements ObservationHandler<ClientRequestObservationContext> {

  private static final Object OPERATION_START_NANOS = new Object();

  private final PoolingHttpClientConnectionManager connectionManager;

  DatastoreOperationObservationHandler(PoolingHttpClientConnectionManager connectionManager) {
    this.connectionManager = connectionManager;
  }

  @Override
  public boolean supportsContext(Observation.Context context) {
    return context instanceof ClientRequestObservationContext;
  }

  @Override
  public void onStart(ClientRequestObservationContext context) {
    try {
      context.put(OPERATION_START_NANOS, System.nanoTime());
      HttpRequest request = context.getCarrier();
      String method = request == null ? "OTHER" : safeMethod(request.getMethod().name());
      String operation =
          request == null ? "unknown" : datastoreOperation(method, request.getURI().getPath());
      logDatastoreEvent(
          DATASTORE_OPERATION,
          "start",
          "in_progress",
          operation,
          method,
          null,
          null,
          null,
          null,
          null,
          null);
    } catch (RuntimeException ignored) {
      return;
    }
  }

  @Override
  public void onStop(ClientRequestObservationContext context) {
    try {
      HttpRequest request = context.getCarrier();
      String method = request == null ? "OTHER" : safeMethod(request.getMethod().name());
      String operation =
          request == null ? "unknown" : datastoreOperation(method, request.getURI().getPath());
      Integer statusCode = responseStatusCode(context);
      Throwable failure = context.getError();
      String failureCategory = failureCategory(failure, statusCode);
      String outcome = "none".equals(failureCategory) ? "success" : "failure";
      Long startedAtNanos = context.get(OPERATION_START_NANOS);
      double durationMillis =
          startedAtNanos == null ? 0.0 : (System.nanoTime() - startedAtNanos) / 1_000_000.0;
      PoolStats poolStats = connectionManager.getTotalStats();

      logDatastoreEvent(
          DATASTORE_OPERATION,
          "finish",
          outcome,
          operation,
          method,
          statusCode,
          durationMillis,
          failure,
          poolStats,
          connectionManager.getMaxTotal(),
          connectionManager.getDefaultMaxPerRoute());
      recordDistribution(
          "datastore_operation_duration",
          durationMillis,
          Map.of(
              "operation", operation,
              "phase", "complete",
              "method", method,
              "status", statusCode == null ? "none" : statusCode.toString(),
              "failure", failureCategory));
      recordPoolStats(poolStats);
    } catch (RuntimeException ignored) {
      return;
    }
  }
}
