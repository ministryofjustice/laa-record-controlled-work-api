package uk.gov.justice.laa.rcw.datastore.client;

import io.sentry.Sentry;
import io.sentry.metrics.MetricsUnit;
import io.sentry.metrics.SentryMetricsParameters;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.apache.hc.core5.pool.PoolStats;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import uk.gov.justice.laa.rcw.logging.SafeFailureDiagnostics;
import uk.gov.justice.laa.rcw.logging.StructuredLogger;

final class DatastoreClientObservability {

  private static final StructuredLogger log =
      StructuredLogger.of(DatastoreClientConfiguration.class);

  private DatastoreClientObservability() {}

  static void recordDatastoreRequestDuration(double durationMillis, String method) {
    try {
      Sentry.metrics()
          .distribution(
              "datastore_api_request_duration",
              durationMillis,
              MetricsUnit.Duration.MILLISECOND,
              SentryMetricsParameters.create(Map.of("http.method", method)));
    } catch (RuntimeException ignored) {
      return;
    }
  }

  static Integer responseStatusCode(ClientRequestObservationContext context) {
    ClientHttpResponse response = context.getResponse();
    return response == null ? null : responseStatusCode(response);
  }

  static Integer responseStatusCode(ClientHttpResponse response) {
    try {
      return response.getStatusCode().value();
    } catch (IOException | RuntimeException ignored) {
      return null;
    }
  }

  static String failureCategory(Throwable failure, Integer statusCode) {
    if (failure != null) {
      return SafeFailureDiagnostics.category(failure);
    }
    return statusCode != null && statusCode >= 400 ? "http_rejection" : "none";
  }

  static String safeMethod(String method) {
    if (method == null) {
      return "OTHER";
    }
    return switch (method) {
      case "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS" -> method;
      default -> "OTHER";
    };
  }

  static String datastoreOperation(String method, String requestUri) {
    if (requestUri == null) {
      return "unknown";
    }
    String path;
    try {
      path = URI.create(requestUri).getPath();
    } catch (IllegalArgumentException ignored) {
      return "unknown";
    }
    if (path == null) {
      return "unknown";
    }
    if (path.endsWith(":edit-application")) {
      return "edit_application";
    }
    if (path.endsWith(":update-application")) {
      return "update_application";
    }
    if (path.endsWith(":update-client-details")) {
      return "update_client_details";
    }
    if (path.endsWith(":update-declaration-data")) {
      return "update_declaration_data";
    }
    if (path.endsWith(":update-evidence")) {
      return "update_evidence";
    }
    if (path.endsWith(":update-means-data")) {
      return "update_means_data";
    }
    if (path.endsWith(":update-scoping-data")) {
      return "update_scoping_data";
    }
    if (path.endsWith(":start-application")) {
      return "start_application";
    }
    if (path.endsWith("/applications")) {
      return "POST".equals(method) ? "start_application" : "get_applications";
    }
    if ("GET".equals(method) && path.matches(".*/applications/[^/]+")) {
      return "fetch_application";
    }
    return "unknown";
  }

  static void logDatastoreEvent(
      String action,
      String phase,
      String outcome,
      String operation,
      String method,
      Integer statusCode,
      Double durationMillis,
      Throwable failure,
      PoolStats poolStats,
      Integer maxTotal,
      Integer maxPerRoute) {
    try {
      StructuredLogger.BuildStage event;
      if ("failure".equals(outcome)) {
        event = log.warn().action(action).outcome(outcome);
      } else {
        event = log.info().action(action).outcome(outcome);
      }
      event =
          event
              .with("event.phase", phase)
              .with("datastore.operation", operation)
              .with("http.request.method", method);
      if (statusCode != null) {
        event = event.with("http.response.status_code", statusCode);
      }
      if (durationMillis != null) {
        event = event.with("duration_ms", durationMillis);
      }
      if (failure != null) {
        event =
            event
                .with("failure.category", SafeFailureDiagnostics.category(failure))
                .with("failure.cause_classes", SafeFailureDiagnostics.causeClasses(failure));
      }
      if (poolStats != null) {
        event =
            event
                .with("pool.leased", poolStats.getLeased())
                .with("pool.pending", poolStats.getPending())
                .with("pool.available", poolStats.getAvailable())
                .with("pool.max", poolStats.getMax())
                .with("pool.max_total", maxTotal)
                .with("pool.default_max_per_route", maxPerRoute);
      }
      event.log("Datastore {} {}", operation, phase);
    } catch (RuntimeException ignored) {
      return;
    }
  }

  static void recordDistribution(String name, double value, Map<String, Object> attributes) {
    try {
      Sentry.metrics()
          .distribution(
              name,
              value,
              MetricsUnit.Duration.MILLISECOND,
              SentryMetricsParameters.create(attributes));
    } catch (RuntimeException ignored) {
      return;
    }
  }

  static void recordPoolStats(PoolStats poolStats) {
    try {
      Sentry.metrics().gauge("datastore_pool_leased", (double) poolStats.getLeased());
      Sentry.metrics().gauge("datastore_pool_pending", (double) poolStats.getPending());
      Sentry.metrics().gauge("datastore_pool_available", (double) poolStats.getAvailable());
      Sentry.metrics().gauge("datastore_pool_max", (double) poolStats.getMax());
    } catch (RuntimeException ignored) {
      return;
    }
  }

  static void logOAuthEvent(
      String action,
      String phase,
      String outcome,
      long startTimeNanos,
      Throwable failure,
      String failureCategory) {
    try {
      StructuredLogger.BuildStage event;
      if ("failure".equals(outcome)) {
        event = log.warn().action(action).outcome(outcome);
      } else {
        event = log.info().action(action).outcome(outcome);
      }
      event = event.with("event.phase", phase);

      if ("finish".equals(phase)) {
        event = event.with("duration_ms", (System.nanoTime() - startTimeNanos) / 1_000_000.0);
      }
      if (failure != null) {
        event =
            event
                .with("failure.category", SafeFailureDiagnostics.category(failure))
                .with("failure.cause_classes", SafeFailureDiagnostics.causeClasses(failure));
      } else if (failureCategory != null) {
        event =
            event
                .with("failure.category", failureCategory)
                .with("failure.cause_classes", List.of("OAuth2AuthorizedClientMissing"));
      }

      event.log("Datastore {} {}", action, phase);
    } catch (RuntimeException ignored) {
      return;
    }
  }
}
