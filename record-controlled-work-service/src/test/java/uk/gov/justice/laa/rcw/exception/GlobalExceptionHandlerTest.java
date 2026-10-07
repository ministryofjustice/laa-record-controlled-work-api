package uk.gov.justice.laa.rcw.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CONTENT_TOO_LARGE;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_DOWNSTREAM_ERROR;
import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_ERROR;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.stream.Stream;
import javax.net.ssl.SSLException;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.ServletWebRequest;

class GlobalExceptionHandlerTest {

  GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();
  private Logger logger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() {
    logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
  }

  @Test
  void handleGenericException_returnsInternalServerErrorStatusAndErrorMessage() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/applications");
    ResponseEntity<Object> result =
        globalExceptionHandler.handleGenericException(
            new RuntimeException("Something went wrong"), new ServletWebRequest(request));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
    ProblemDetail body = (ProblemDetail) result.getBody();
    assert body != null;
    assertThat(body.getDetail()).isEqualTo("An unexpected application error has occurred.");
    assertThat(body.getProperties()).containsEntry("reason", "INTERNAL_SERVER_ERROR");
  }

  @Test
  void handleGenericException_logsSafeCauseEvidenceWithoutRawExceptionText() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/applications");
    RuntimeException exception =
        new IllegalStateException(
            "assertion-secret-sentinel", new RuntimeException("token-secret-sentinel"));

    globalExceptionHandler.handleGenericException(exception, new ServletWebRequest(request));

    ILoggingEvent event = singleEvent();
    assertThat(event.getThrowableProxy()).isNull();
    assertThat(keyValue(event, "event.action")).isEqualTo(APPLICATION_ERROR);
    assertThat(keyValue(event, "failure.category")).isEqualTo("unknown");
    assertThat(keyValue(event, "failure.cause_classes"))
        .isEqualTo(List.of("IllegalStateException", "RuntimeException"));
    assertThat(event.getFormattedMessage())
        .doesNotContain("assertion-secret-sentinel", "token-secret-sentinel");
  }

  @Test
  void handleApplicationConflict_returnsConflictStatusAndDefaultReason_whenModifiedConcurrently() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("PUT", "/api/v1/applications/99/means");
    ResponseEntity<Object> result =
        globalExceptionHandler.handleApplicationConflict(
            new ApplicationConflictException("Application 99 was modified concurrently"),
            new ServletWebRequest(request));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(CONFLICT);
    ProblemDetail body = (ProblemDetail) result.getBody();
    assert body != null;
    assertThat(body.getProperties()).containsEntry("reason", "CONCURRENT_MODIFICATION");
  }

  @Test
  void handleApplicationConflict_returnsConflictStatusAndGivenReason_whenAlreadyRecorded() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("PUT", "/api/v1/applications/99/means");
    ResponseEntity<Object> result =
        globalExceptionHandler.handleApplicationConflict(
            new ApplicationConflictException(
                "Application 99 has already been recorded and cannot be updated",
                "APPLICATION_ALREADY_RECORDED"),
            new ServletWebRequest(request));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(CONFLICT);
    ProblemDetail body = (ProblemDetail) result.getBody();
    assert body != null;
    assertThat(body.getProperties()).containsEntry("reason", "APPLICATION_ALREADY_RECORDED");
  }

  @Test
  void handleApplicationConflict_returnsPreconditionFailedForVersionConflict() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("PUT", "/api/v1/applications/99/details");
    ResponseEntity<Object> result =
        globalExceptionHandler.handleApplicationConflict(
            new ApplicationConflictException(
                "Application details changed concurrently", "APPLICATION_VERSION_CONFLICT"),
            new ServletWebRequest(request));

    assertThat(result.getStatusCode()).isEqualTo(PRECONDITION_FAILED);
    ProblemDetail body = (ProblemDetail) result.getBody();
    assert body != null;
    assertThat(body.getStatus()).isEqualTo(412);
    assertThat(body.getProperties()).containsEntry("reason", "APPLICATION_VERSION_CONFLICT");
  }

  @Test
  void handleApplicationRequestTooLarge_returnsPayloadTooLargeWithoutRequestValues() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("PUT", "/api/v1/applications/99/details");
    ResponseEntity<Object> result =
        globalExceptionHandler.handleApplicationRequestTooLarge(
            new ApplicationRequestTooLargeException(), new ServletWebRequest(request));

    assertThat(result.getStatusCode()).isEqualTo(CONTENT_TOO_LARGE);
    ProblemDetail body = (ProblemDetail) result.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getDetail()).isEqualTo("Request body exceeds the configured size limit.");
    assertThat(body.getProperties()).containsEntry("reason", "REQUEST_BODY_TOO_LARGE");
  }

  @Test
  void handleApplicationForbidden_returnsForbiddenStatusAndErrorMessage() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("PUT", "/api/v1/applications/99/means");
    ResponseEntity<Object> result =
        globalExceptionHandler.handleApplicationForbidden(
            new ApplicationForbiddenException("Not authorized to update application 99"),
            new ServletWebRequest(request));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(FORBIDDEN);
    ProblemDetail body = (ProblemDetail) result.getBody();
    assert body != null;
    assertThat(body.getDetail()).isEqualTo("Not authorized to update application 99");
    assertThat(body.getProperties()).containsEntry("reason", "OFFICE_NOT_AUTHORIZED");
  }

  @Test
  void handleApplicationBadRequest_returnsBadRequestStatusAndErrorMessage() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("PUT", "/api/v1/applications/99/means");
    ResponseEntity<Object> result =
        globalExceptionHandler.handleApplicationBadRequest(
            new ApplicationBadRequestException("Datastore rejected the request for application 99"),
            new ServletWebRequest(request));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(BAD_REQUEST);
    ProblemDetail body = (ProblemDetail) result.getBody();
    assert body != null;
    assertThat(body.getDetail()).isEqualTo("Datastore rejected the request for application 99");
    assertThat(body.getProperties()).containsEntry("reason", "DATASTORE_REJECTED_REQUEST");
  }

  @Test
  void handleApplicationUpstreamError_returnsBadGatewayStatusAndErrorMessage() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("PUT", "/api/v1/applications/99/means");
    ResponseEntity<Object> result =
        globalExceptionHandler.handleApplicationUpstreamError(
            new ApplicationUpstreamErrorException("Datastore returned an error for application 99"),
            new ServletWebRequest(request));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(BAD_GATEWAY);
    ProblemDetail body = (ProblemDetail) result.getBody();
    assert body != null;
    assertThat(body.getDetail()).isEqualTo("Datastore returned an error for application 99");
    assertThat(body.getProperties()).containsEntry("reason", "DATASTORE_SERVER_ERROR");
  }

  @Test
  void handleApplicationUnavailable_returnsServiceUnavailableStatusAndErrorMessage() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("PUT", "/api/v1/applications/99/means");
    ResponseEntity<Object> result =
        globalExceptionHandler.handleApplicationUnavailable(
            new ApplicationUnavailableException("Datastore is unavailable for application 99"),
            new ServletWebRequest(request));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(SERVICE_UNAVAILABLE);
    ProblemDetail body = (ProblemDetail) result.getBody();
    assert body != null;
    assertThat(body.getDetail()).isEqualTo("Datastore is unavailable for application 99");
    assertThat(body.getProperties()).containsEntry("reason", "DATASTORE_UNAVAILABLE");
  }

  @Test
  void handleApplicationUnavailable_logsDnsCategoryWithoutRawFailureText() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("PUT", "/api/v1/applications/record-id-sentinel/means");
    ApplicationUnavailableException exception =
        new ApplicationUnavailableException("record-id-sentinel");
    exception.initCause(
        new ResourceAccessException(
            "secret-transport-message", new UnknownHostException("secret-host-sentinel")));

    globalExceptionHandler.handleApplicationUnavailable(exception, new ServletWebRequest(request));

    ILoggingEvent event = singleEvent();
    assertThat(event.getThrowableProxy()).isNull();
    assertThat(keyValue(event, "event.action")).isEqualTo(APPLICATION_DOWNSTREAM_ERROR);
    assertThat(keyValue(event, "failure.category")).isEqualTo("dns");
    assertThat(keyValue(event, "failure.cause_classes"))
        .isEqualTo(
            List.of(
                "ApplicationUnavailableException",
                "ResourceAccessException",
                "UnknownHostException"));
    assertThat(event.getFormattedMessage())
        .doesNotContain("record-id-sentinel", "secret-transport-message", "secret-host-sentinel");
  }

  @ParameterizedTest
  @MethodSource("downstreamFailures")
  void handleApplicationUnavailable_logsBoundedFailureCategory(
      Throwable failure, String expectedCategory) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/applications");
    ApplicationUnavailableException exception = new ApplicationUnavailableException("safe detail");
    exception.initCause(failure);

    globalExceptionHandler.handleApplicationUnavailable(exception, new ServletWebRequest(request));

    ILoggingEvent event = singleEvent();
    assertThat(keyValue(event, "failure.category")).isEqualTo(expectedCategory);
    assertThat(event.getThrowableProxy()).isNull();
    assertThat(event.getFormattedMessage()).doesNotContain("diagnostic-secret");
  }

  private static Stream<Arguments> downstreamFailures() {
    return Stream.of(
        Arguments.of(
            new ConnectionRequestTimeoutException("diagnostic-secret"), "pool_lease_timeout"),
        Arguments.of(new ConnectTimeoutException("diagnostic-secret"), "connect_timeout"),
        Arguments.of(new SocketTimeoutException("diagnostic-secret"), "read_timeout"),
        Arguments.of(new SSLException("diagnostic-secret"), "tls"),
        Arguments.of(new SocketException("Connection reset"), "connection_reset"),
        Arguments.of(
            HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "diagnostic-secret", HttpHeaders.EMPTY, new byte[0], null),
            "http_rejection"),
        Arguments.of(
            new HttpMessageNotReadableException("diagnostic-secret", null), "decoding_failure"),
        Arguments.of(new IllegalStateException("diagnostic-secret"), "unknown"));
  }

  private ILoggingEvent singleEvent() {
    assertThat(appender.list).hasSize(1);
    return appender.list.getFirst();
  }

  private Object keyValue(ILoggingEvent event, String key) {
    return event.getKeyValuePairs().stream()
        .filter(pair -> pair.key.equals(key))
        .map(pair -> pair.value)
        .findFirst()
        .orElse(null);
  }
}
