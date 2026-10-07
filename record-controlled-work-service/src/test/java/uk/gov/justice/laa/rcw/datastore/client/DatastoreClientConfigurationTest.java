package uk.gov.justice.laa.rcw.datastore.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.sentry.Sentry;
import io.sentry.metrics.IMetricsApi;
import io.sentry.metrics.MetricsUnit;
import io.sentry.metrics.SentryMetricsParameters;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

@ExtendWith(MockitoExtension.class)
class DatastoreClientConfigurationTest {

  private static final String METRIC_NAME = "datastore_api_request_duration";
  private static final byte[] REQUEST_BODY = new byte[0];

  @Mock private OAuth2AuthorizedClientManager mockClientManager;
  @Mock private OAuth2AuthorizedClient mockAuthorizedClient;
  @Mock private OAuth2AccessToken mockAccessToken;
  @Mock private Authentication mockAuthentication;
  @Mock private HttpRequest mockRequest;
  @Mock private ClientHttpRequest mockObservationRequest;
  @Mock private ClientHttpRequestExecution mockExecution;
  @Mock private ClientHttpResponse mockResponse;
  @Mock private IMetricsApi mockMetricsApi;

  private final HttpHeaders requestHeaders = new HttpHeaders();
  private MockedStatic<Sentry> mockSentry;
  private DatastoreOboInterceptor interceptor;
  private Logger logger;
  private Level previousLogLevel;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() {
    lenient().when(mockClientManager.authorize(any())).thenReturn(mockAuthorizedClient);
    lenient().when(mockAuthorizedClient.getAccessToken()).thenReturn(mockAccessToken);
    lenient().when(mockAccessToken.getTokenValue()).thenReturn("test-access-token");
    lenient().when(mockRequest.getHeaders()).thenReturn(requestHeaders);
    lenient().when(mockRequest.getMethod()).thenReturn(HttpMethod.GET);
    lenient()
        .when(mockRequest.getURI())
        .thenReturn(URI.create("http://datastore.test/api/v0/applications/1"));

    SecurityContextHolder.getContext().setAuthentication(mockAuthentication);
    logger = (Logger) LoggerFactory.getLogger(DatastoreClientConfiguration.class);
    previousLogLevel = logger.getLevel();
    logger.setLevel(Level.INFO);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    mockSentry = mockStatic(Sentry.class);
    mockSentry.when(Sentry::metrics).thenReturn(mockMetricsApi);
    interceptor = new DatastoreOboInterceptor(mockClientManager, "datastore");
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    logger.detachAppender(appender);
    logger.setLevel(previousLogLevel);
    mockSentry.close();
  }

  @Test
  void shouldRecordRequestDuration_whenDatastoreRequestSucceeds() throws IOException {
    when(mockExecution.execute(mockRequest, REQUEST_BODY)).thenReturn(mockResponse);

    ClientHttpResponse response = interceptor.intercept(mockRequest, REQUEST_BODY, mockExecution);

    assertThat(response).isSameAs(mockResponse);
    assertThat(requestHeaders.getFirst(HttpHeaders.AUTHORIZATION))
        .isEqualTo("Bearer test-access-token");
    assertThat(events("datastore.authorization"))
        .extracting(event -> keyValue(event, "event.phase"))
        .containsExactly("start", "finish");
    assertThat(events("datastore.authorization"))
        .extracting(event -> keyValue(event, "event.outcome"))
        .containsExactly("in_progress", "success");
    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .noneMatch(message -> message.contains("test-access-token"));
    verifyRequestDurationMetric();
  }

  @Test
  void shouldLogAuthorizationFailure_whenManagerThrowsWithoutExposingExceptionMessage()
      throws IOException {
    IllegalStateException authorizationFailure =
        new IllegalStateException("assertion-secret-sentinel");
    when(mockClientManager.authorize(any())).thenThrow(authorizationFailure);

    assertThatThrownBy(() -> interceptor.intercept(mockRequest, REQUEST_BODY, mockExecution))
        .isSameAs(authorizationFailure);

    assertAuthorizationFailedWithoutRawMessage();
    verify(mockExecution, never()).execute(mockRequest, REQUEST_BODY);
  }

  @Test
  void shouldLogAuthorizationFailure_whenPrincipalIsAbsent() {
    SecurityContextHolder.clearContext();

    assertThatThrownBy(() -> interceptor.intercept(mockRequest, REQUEST_BODY, mockExecution))
        .isInstanceOf(IllegalArgumentException.class);

    assertAuthorizationFailedWithoutRawMessage();
    verify(mockClientManager, never()).authorize(any());
  }

  @Test
  void shouldLogAuthorizationFailure_whenManagerReturnsNoAuthorizedClient() throws IOException {
    when(mockClientManager.authorize(any())).thenReturn(null);

    assertThatThrownBy(() -> interceptor.intercept(mockRequest, REQUEST_BODY, mockExecution))
        .isInstanceOf(NullPointerException.class);

    assertAuthorizationFailedWithoutRawMessage();
    verify(mockExecution, never()).execute(mockRequest, REQUEST_BODY);
  }

  @Test
  void shouldRecordRequestDuration_whenDatastoreRequestFails() throws IOException {
    IOException requestFailure = new IOException("Datastore connection failed");
    when(mockExecution.execute(mockRequest, REQUEST_BODY)).thenThrow(requestFailure);

    assertThatThrownBy(() -> interceptor.intercept(mockRequest, REQUEST_BODY, mockExecution))
        .isSameAs(requestFailure);

    verifyRequestDurationMetric();
  }

  @Test
  void shouldPreserveDatastoreResponse_whenDurationMetricFails() throws IOException {
    when(mockExecution.execute(mockRequest, REQUEST_BODY)).thenReturn(mockResponse);
    doThrow(new IllegalStateException("metrics failure"))
        .when(mockMetricsApi)
        .distribution(eq(METRIC_NAME), any(), eq(MetricsUnit.Duration.MILLISECOND), any());

    ClientHttpResponse response = interceptor.intercept(mockRequest, REQUEST_BODY, mockExecution);

    assertThat(response).isSameAs(mockResponse);
  }

  @Test
  void shouldExcludeRequestUrlValuesFromNativeObservationAttributes() {
    String requestUrl =
        "https://datastore.example/api/v0/applications/record-id-secret?token=query-secret";
    when(mockObservationRequest.getURI()).thenReturn(URI.create(requestUrl));
    when(mockObservationRequest.getMethod()).thenReturn(HttpMethod.GET);
    ClientRequestObservationContext context =
        new ClientRequestObservationContext(mockObservationRequest);
    context.setUriTemplate(requestUrl);
    DatastoreClientObservationConvention convention = new DatastoreClientObservationConvention();

    String lowCardinalityValues = convention.getLowCardinalityKeyValues(context).toString();

    assertThat(lowCardinalityValues)
        .contains("operation=fetch_application")
        .doesNotContain("datastore.example", "record-id-secret", "query-secret");
    assertThat(convention.getHighCardinalityKeyValues(context).toString()).isEqualTo("[]");
  }

  private void verifyRequestDurationMetric() {
    ArgumentCaptor<Double> durationCaptor = ArgumentCaptor.forClass(Double.class);
    ArgumentCaptor<SentryMetricsParameters> parametersCaptor =
        ArgumentCaptor.forClass(SentryMetricsParameters.class);

    verify(mockMetricsApi)
        .distribution(
            eq(METRIC_NAME),
            durationCaptor.capture(),
            eq(MetricsUnit.Duration.MILLISECOND),
            parametersCaptor.capture());

    assertThat(durationCaptor.getValue()).isGreaterThanOrEqualTo(0.0);
    assertThat(
            parametersCaptor
                .getValue()
                .getAttributes()
                .getAttributes()
                .get("http.method")
                .getValue())
        .isEqualTo("GET");
  }

  private void assertAuthorizationFailedWithoutRawMessage() {
    List<ILoggingEvent> events = events("datastore.authorization");
    assertThat(events).hasSize(2);
    assertThat(keyValue(events.getLast(), "event.phase")).isEqualTo("finish");
    assertThat(keyValue(events.getLast(), "event.outcome")).isEqualTo("failure");
    assertThat(events)
        .extracting(ILoggingEvent::getFormattedMessage)
        .noneMatch(message -> message.contains("assertion-secret-sentinel"));
    assertThat(events).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
  }

  private List<ILoggingEvent> events(String action) {
    return appender.list.stream()
        .filter(event -> action.equals(keyValue(event, "event.action")))
        .toList();
  }

  private Object keyValue(ILoggingEvent event, String key) {
    return event.getKeyValuePairs().stream()
        .filter(pair -> pair.key.equals(key))
        .map(pair -> pair.value)
        .findFirst()
        .orElse(null);
  }
}
