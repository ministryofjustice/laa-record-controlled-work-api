package uk.gov.justice.laa.rcw.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import uk.gov.justice.laa.ia.datastore.client.api.ApplicationApi;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.rcw.exception.ApplicationBadRequestException;
import uk.gov.justice.laa.rcw.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.rcw.exception.ApplicationUnavailableException;
import uk.gov.justice.laa.rcw.exception.ApplicationUpstreamErrorException;

@ExtendWith(MockitoExtension.class)
class ApplicationGatewayTest {

  private static final String BEARER_TOKEN = "Bearer original-incoming-token";

  @Mock private ApplicationApi mockApplicationApi;
  @Mock private BearerTokenProvider mockBearerTokenProvider;

  private ApplicationGateway applicationGateway;

  @BeforeEach
  void setUp() {
    applicationGateway = new ApplicationGateway(mockApplicationApi, mockBearerTokenProvider);
    when(mockBearerTokenProvider.currentBearerToken()).thenReturn(BEARER_TOKEN);
  }

  @Test
  void shouldFetchApplication() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    ApplicationResponse response =
        ApplicationResponse.builder().id(applicationId).providerOfficeCode("AB12CD").build();
    when(mockApplicationApi.getApplication(applicationId, BEARER_TOKEN)).thenReturn(response);

    ApplicationResponse result = applicationGateway.fetchApplication(applicationId);

    assertThat(result).isEqualTo(response);
    verify(mockApplicationApi).getApplication(eq(applicationId), eq(BEARER_TOKEN));
  }

  @Test
  void shouldNotFetchApplication_whenDatastoreReturnsNotFoundThenThrowsException() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationApi.getApplication(applicationId, BEARER_TOKEN)).thenThrow(notFound());

    assertThatThrownBy(() -> applicationGateway.fetchApplication(applicationId))
        .isExactlyInstanceOf(ApplicationNotFoundException.class)
        .hasMessage("No application found with id: %s".formatted(applicationId));
  }

  @Test
  void shouldNotFetchApplication_whenDatastoreReturnsBadRequestThenThrowsException() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationApi.getApplication(applicationId, BEARER_TOKEN)).thenThrow(badRequest());

    assertThatThrownBy(() -> applicationGateway.fetchApplication(applicationId))
        .isExactlyInstanceOf(ApplicationBadRequestException.class)
        .hasMessage("Datastore rejected the request for application %s".formatted(applicationId));
  }

  @Test
  void shouldNotFetchApplication_whenDatastoreReturnsServerErrorThenThrowsException() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationApi.getApplication(applicationId, BEARER_TOKEN)).thenThrow(serverError());

    assertThatThrownBy(() -> applicationGateway.fetchApplication(applicationId))
        .isExactlyInstanceOf(ApplicationUpstreamErrorException.class)
        .hasMessage("Datastore returned an error for application %s".formatted(applicationId));
  }

  @Test
  void shouldNotFetchApplication_whenDatastoreIsUnavailableThenThrowsException() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationApi.getApplication(applicationId, BEARER_TOKEN))
        .thenThrow(new ResourceAccessException("Connection refused"));

    assertThatThrownBy(() -> applicationGateway.fetchApplication(applicationId))
        .isExactlyInstanceOf(ApplicationUnavailableException.class)
        .hasMessage("Datastore is unavailable for application %s".formatted(applicationId));
  }

  private static HttpClientErrorException.NotFound notFound() {
    return (HttpClientErrorException.NotFound)
        HttpClientErrorException.create(
            HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null);
  }

  private static HttpClientErrorException.BadRequest badRequest() {
    return (HttpClientErrorException.BadRequest)
        HttpClientErrorException.create(
            HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, new byte[0], null);
  }

  private static HttpServerErrorException serverError() {
    return new HttpServerErrorException(HttpStatus.BAD_GATEWAY);
  }
}
