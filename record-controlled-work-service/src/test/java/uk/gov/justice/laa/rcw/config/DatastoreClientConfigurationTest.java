package uk.gov.justice.laa.rcw.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.sentry.Sentry;
import io.sentry.metrics.IMetricsApi;
import io.sentry.metrics.MetricsUnit;
import io.sentry.metrics.SentryMetricsParameters;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
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
  @Mock private ClientHttpRequestExecution mockExecution;
  @Mock private ClientHttpResponse mockResponse;
  @Mock private IMetricsApi mockMetricsApi;

  private final HttpHeaders requestHeaders = new HttpHeaders();
  private MockedStatic<Sentry> mockSentry;
  private DatastoreClientConfiguration.DatastoreOboInterceptor interceptor;

  @BeforeEach
  void setUp() {
    when(mockClientManager.authorize(any())).thenReturn(mockAuthorizedClient);
    when(mockAuthorizedClient.getAccessToken()).thenReturn(mockAccessToken);
    when(mockAccessToken.getTokenValue()).thenReturn("test-access-token");
    when(mockRequest.getHeaders()).thenReturn(requestHeaders);
    when(mockRequest.getMethod()).thenReturn(HttpMethod.GET);

    SecurityContextHolder.getContext().setAuthentication(mockAuthentication);
    mockSentry = mockStatic(Sentry.class);
    mockSentry.when(Sentry::metrics).thenReturn(mockMetricsApi);
    interceptor =
        new DatastoreClientConfiguration.DatastoreOboInterceptor(mockClientManager, "datastore");
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    mockSentry.close();
  }

  @Test
  void shouldRecordRequestDuration_whenDatastoreRequestSucceeds() throws IOException {
    when(mockExecution.execute(mockRequest, REQUEST_BODY)).thenReturn(mockResponse);

    ClientHttpResponse response = interceptor.intercept(mockRequest, REQUEST_BODY, mockExecution);

    assertThat(response).isSameAs(mockResponse);
    assertThat(requestHeaders.getFirst(HttpHeaders.AUTHORIZATION))
        .isEqualTo("Bearer test-access-token");
    verifyRequestDurationMetric();
  }

  @Test
  void shouldRecordRequestDuration_whenDatastoreRequestFails() throws IOException {
    IOException requestFailure = new IOException("Datastore connection failed");
    when(mockExecution.execute(mockRequest, REQUEST_BODY)).thenThrow(requestFailure);

    assertThatThrownBy(() -> interceptor.intercept(mockRequest, REQUEST_BODY, mockExecution))
        .isSameAs(requestFailure);

    verifyRequestDurationMetric();
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
}
