package uk.gov.justice.laa.rcw.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import javax.net.ssl.SSLException;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.client.HttpStatusCodeException;

/** Produces bounded diagnostic fields without exporting exception messages. */
public final class SafeFailureDiagnostics {

  private static final int MAX_CAUSE_DEPTH = 8;

  private SafeFailureDiagnostics() {}

  /**
   * Classifies a failure chain without exposing exception messages.
   *
   * @param failure the failure to classify
   * @return a bounded failure category
   */
  public static String category(Throwable failure) {
    for (Throwable cause : causeChain(failure).causes()) {
      String category = categoryOf(cause);
      if (category != null) {
        return category;
      }
    }
    return "unknown";
  }

  /**
   * Returns bounded exception class names from a failure's cause chain.
   *
   * @param failure the failure to inspect
   * @return exception class names without message values
   */
  public static List<String> causeClasses(Throwable failure) {
    CauseChain chain = causeChain(failure);
    List<String> classes =
        chain.causes().stream().map(cause -> cause.getClass().getSimpleName()).toList();
    if (chain.truncated()) {
      List<String> boundedClasses = new ArrayList<>(classes);
      boundedClasses.add("CAUSE_CHAIN_TRUNCATED");
      return List.copyOf(boundedClasses);
    }
    return classes;
  }

  private static String categoryOf(Throwable cause) {
    if (cause instanceof ConnectionRequestTimeoutException) {
      return "pool_lease_timeout";
    }
    if (cause instanceof ConnectTimeoutException) {
      return "connect_timeout";
    }
    if (cause instanceof UnknownHostException) {
      return "dns";
    }
    if (cause instanceof SocketTimeoutException) {
      return "read_timeout";
    }
    if (cause instanceof SSLException) {
      return "tls";
    }
    if (cause instanceof SocketException socketException
        && isConnectionReset(socketException.getMessage())) {
      return "connection_reset";
    }
    if (cause instanceof HttpStatusCodeException) {
      return "http_rejection";
    }
    if (cause instanceof HttpMessageConversionException
        || cause instanceof JsonProcessingException) {
      return "decoding_failure";
    }
    return null;
  }

  private static boolean isConnectionReset(String message) {
    return "Connection reset".equalsIgnoreCase(message)
        || "Connection reset by peer".equalsIgnoreCase(message);
  }

  private static CauseChain causeChain(Throwable failure) {
    List<Throwable> causes = new ArrayList<>();
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    Throwable current = failure;
    while (current != null && causes.size() < MAX_CAUSE_DEPTH && seen.add(current)) {
      causes.add(current);
      current = current.getCause();
    }
    return new CauseChain(List.copyOf(causes), current != null);
  }

  private record CauseChain(List<Throwable> causes, boolean truncated) {}
}
