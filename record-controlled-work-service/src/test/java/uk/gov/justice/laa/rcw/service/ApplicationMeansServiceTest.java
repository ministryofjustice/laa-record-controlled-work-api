package uk.gov.justice.laa.rcw.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import uk.gov.justice.laa.ia.datastore.client.api.ApplicationApi;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationState;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateMeansDataCommand;
import uk.gov.justice.laa.rcw.exception.ApplicationBadRequestException;
import uk.gov.justice.laa.rcw.exception.ApplicationConflictException;
import uk.gov.justice.laa.rcw.exception.ApplicationForbiddenException;
import uk.gov.justice.laa.rcw.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.rcw.exception.ApplicationUnavailableException;
import uk.gov.justice.laa.rcw.exception.ApplicationUpstreamErrorException;
import uk.gov.justice.laa.rcw.gateway.ApplicationGateway;

@ExtendWith(MockitoExtension.class)
class ApplicationMeansServiceTest {

  private static final String ORIGINAL_TOKEN = "original-incoming-token";
  private static final String AUTHORIZED_OFFICE_CODE = "AB12CD";

  @Mock private ApplicationApi mockApplicationApi;
  @Mock private ApplicationGateway mockApplicationGateway;
  @Mock private AuthorizedOfficesProvider mockAuthorizedOfficesProvider;

  private final BearerTokenProvider bearerTokenProvider = new BearerTokenProvider();
  private ApplicationMeansService applicationMeansService;

  @BeforeEach
  void setUp() {
    applicationMeansService =
        new ApplicationMeansService(
            mockApplicationApi,
            bearerTokenProvider,
            mockApplicationGateway,
            mockAuthorizedOfficesProvider);
    Jwt jwt =
        Jwt.withTokenValue(ORIGINAL_TOKEN).header("alg", "none").claim("sub", "test-user").build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    lenient()
        .when(mockAuthorizedOfficesProvider.currentAuthorizedOfficeCodes())
        .thenReturn(List.of(AUTHORIZED_OFFICE_CODE));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldUpdateMeans_fetchesETagAndForwardsDataAndResultToDatastore() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    Map<String, Object> data = Map.of("level_of_help", "controlled");
    Map<String, Object> result = Map.of("indication", true);
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(7L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .build());

    applicationMeansService.updateMeans(applicationId, data, result);

    verify(mockApplicationGateway).fetchApplication(eq(applicationId));

    ArgumentCaptor<String> updateXAuthorizationCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<UpdateMeansDataCommand> commandCaptor =
        ArgumentCaptor.forClass(UpdateMeansDataCommand.class);
    verify(mockApplicationApi)
        .updateMeansData(
            eq(applicationId), updateXAuthorizationCaptor.capture(), commandCaptor.capture());
    assertThat(updateXAuthorizationCaptor.getValue()).isEqualTo("Bearer " + ORIGINAL_TOKEN);
    assertThat(commandCaptor.getValue())
        .isEqualTo(UpdateMeansDataCommand.builder().eTag(7L).data(data).result(result).build());
  }

  @Test
  void shouldUpdateMeans_throwsApplicationNotFoundException_whenDatastoreUpdateReturns404() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .build());
    doThrow(notFound())
        .when(mockApplicationApi)
        .updateMeansData(eq(applicationId), anyString(), any());

    assertThatThrownBy(() -> applicationMeansService.updateMeans(applicationId, Map.of(), Map.of()))
        .isInstanceOf(ApplicationNotFoundException.class)
        .hasMessageContaining(applicationId.toString());
  }

  @Test
  void shouldUpdateMeans_retriesOnceWithFreshETag_whenDatastoreReturnsConflict() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    Map<String, Object> data = Map.of("level_of_help", "controlled");
    Map<String, Object> result = Map.of("indication", true);
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .build())
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(2L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .build());
    doThrow(conflict())
        .doNothing()
        .when(mockApplicationApi)
        .updateMeansData(eq(applicationId), anyString(), any());

    applicationMeansService.updateMeans(applicationId, data, result);

    verify(mockApplicationGateway, times(2)).fetchApplication(eq(applicationId));
    ArgumentCaptor<UpdateMeansDataCommand> commandCaptor =
        ArgumentCaptor.forClass(UpdateMeansDataCommand.class);
    verify(mockApplicationApi, times(2))
        .updateMeansData(eq(applicationId), anyString(), commandCaptor.capture());
    assertThat(commandCaptor.getAllValues())
        .extracting(UpdateMeansDataCommand::geteTag)
        .containsExactly(1L, 2L);
  }

  @Test
  void shouldUpdateMeans_throwsApplicationConflictException_whenConflictPersistsAfterRetry() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .build());
    doThrow(conflict())
        .when(mockApplicationApi)
        .updateMeansData(eq(applicationId), anyString(), any());

    assertThatThrownBy(() -> applicationMeansService.updateMeans(applicationId, Map.of(), Map.of()))
        .isInstanceOf(ApplicationConflictException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationGateway, times(2)).fetchApplication(eq(applicationId));
    verify(mockApplicationApi, times(2)).updateMeansData(eq(applicationId), anyString(), any());
  }

  @Test
  void shouldUpdateMeans_throwsApplicationConflictException_whenApplicationAlreadyRecorded() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .applicationState(ApplicationState.COMPLETED)
                .build());

    assertThatThrownBy(() -> applicationMeansService.updateMeans(applicationId, Map.of(), Map.of()))
        .isInstanceOf(ApplicationConflictException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationApi, never()).updateMeansData(any(), anyString(), any());
  }

  @Test
  void shouldUpdateMeans_throwsApplicationBadRequestException_whenDatastoreUpdateReturns400() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .build());
    doThrow(badRequest())
        .when(mockApplicationApi)
        .updateMeansData(eq(applicationId), anyString(), any());

    assertThatThrownBy(() -> applicationMeansService.updateMeans(applicationId, Map.of(), Map.of()))
        .isInstanceOf(ApplicationBadRequestException.class)
        .hasMessageContaining(applicationId.toString());
  }

  @Test
  void shouldUpdateMeans_throwsApplicationUpstreamErrorException_whenDatastoreUpdateReturns5xx() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .build());
    doThrow(serverError())
        .when(mockApplicationApi)
        .updateMeansData(eq(applicationId), anyString(), any());

    assertThatThrownBy(() -> applicationMeansService.updateMeans(applicationId, Map.of(), Map.of()))
        .isInstanceOf(ApplicationUpstreamErrorException.class)
        .hasMessageContaining(applicationId.toString());
  }

  @Test
  void shouldUpdateMeans_throwsApplicationUnavailableException_whenDatastoreUpdateFailsToConnect() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .build());
    doThrow(new ResourceAccessException("Connection refused"))
        .when(mockApplicationApi)
        .updateMeansData(eq(applicationId), anyString(), any());

    assertThatThrownBy(() -> applicationMeansService.updateMeans(applicationId, Map.of(), Map.of()))
        .isInstanceOf(ApplicationUnavailableException.class)
        .hasMessageContaining(applicationId.toString());
  }

  @Test
  void shouldUpdateMeans_throwsApplicationForbiddenException_whenOfficeCodeNotAuthorized() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder().eTag(1L).providerOfficeCode("OTHER-OFFICE").build());

    assertThatThrownBy(() -> applicationMeansService.updateMeans(applicationId, Map.of(), Map.of()))
        .isInstanceOf(ApplicationForbiddenException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationApi, never()).updateMeansData(any(), anyString(), any());
  }

  @Test
  void shouldUpdateMeans_throwsApplicationForbiddenException_whenNoOfficesAreAuthorized() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .build());
    when(mockAuthorizedOfficesProvider.currentAuthorizedOfficeCodes()).thenReturn(List.of());

    assertThatThrownBy(() -> applicationMeansService.updateMeans(applicationId, Map.of(), Map.of()))
        .isInstanceOf(ApplicationForbiddenException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationApi, never()).updateMeansData(any(), anyString(), any());
  }

  private static HttpClientErrorException.NotFound notFound() {
    return (HttpClientErrorException.NotFound)
        HttpClientErrorException.create(
            HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null);
  }

  private static HttpClientErrorException.Conflict conflict() {
    return (HttpClientErrorException.Conflict)
        HttpClientErrorException.create(
            HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY, new byte[0], null);
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
