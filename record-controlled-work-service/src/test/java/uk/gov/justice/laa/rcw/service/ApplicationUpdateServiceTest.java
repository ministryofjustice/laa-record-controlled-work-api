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
import uk.gov.justice.laa.ia.datastore.client.model.UpdateApplicationCommand;
import uk.gov.justice.laa.rcw.exception.ApplicationBadRequestException;
import uk.gov.justice.laa.rcw.exception.ApplicationConflictException;
import uk.gov.justice.laa.rcw.exception.ApplicationForbiddenException;
import uk.gov.justice.laa.rcw.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.rcw.exception.ApplicationUnavailableException;
import uk.gov.justice.laa.rcw.exception.ApplicationUpstreamErrorException;
import uk.gov.justice.laa.rcw.gateway.ApplicationGateway;
import uk.gov.justice.laa.rcw.mapper.ApplicationMapper;
import uk.gov.justice.laa.rcw.model.ApplicationState;

@ExtendWith(MockitoExtension.class)
class ApplicationUpdateServiceTest {

  private static final String ORIGINAL_TOKEN = "original-incoming-token";
  private static final String AUTHORIZED_OFFICE_CODE = "AB12CD";

  @Mock private ApplicationApi mockApplicationApi;
  @Mock private ApplicationMapper mockApplicationMapper;
  @Mock private ApplicationGateway mockApplicationGateway;
  @Mock private AuthorizedOfficesProvider mockAuthorizedOfficesProvider;

  private final BearerTokenProvider bearerTokenProvider = new BearerTokenProvider();
  private ApplicationUpdateService applicationUpdateService;

  @BeforeEach
  void setUp() {
    applicationUpdateService =
        new ApplicationUpdateService(
            mockApplicationApi,
            mockApplicationMapper,
            bearerTokenProvider,
            mockApplicationGateway,
            mockAuthorizedOfficesProvider);
    Jwt jwt =
        Jwt.withTokenValue(ORIGINAL_TOKEN).header("alg", "none").claim("sub", "test-user").build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    lenient()
        .when(mockAuthorizedOfficesProvider.currentAuthorizedOfficeCodes())
        .thenReturn(List.of(AUTHORIZED_OFFICE_CODE));
    lenient()
        .when(mockApplicationMapper.toDatastoreApplicationState(any()))
        .thenReturn(uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.COMPLETED);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldUpdateStatus_fetchesETagAndForwardsStatusToDatastore() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(7L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .applicationState(
                    uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
                .build());

    applicationUpdateService.updateStatus(applicationId, ApplicationState.COMPLETED);

    verify(mockApplicationGateway).fetchApplication(eq(applicationId));

    ArgumentCaptor<String> updateXAuthorizationCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<UpdateApplicationCommand> commandCaptor =
        ArgumentCaptor.forClass(UpdateApplicationCommand.class);
    verify(mockApplicationApi)
        .updateApplication(
            eq(applicationId), updateXAuthorizationCaptor.capture(), commandCaptor.capture());
    assertThat(updateXAuthorizationCaptor.getValue()).isEqualTo("Bearer " + ORIGINAL_TOKEN);
    assertThat(commandCaptor.getValue().geteTag()).isEqualTo(7L);
    assertThat(commandCaptor.getValue().getApplicationState())
        .isEqualTo(uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.COMPLETED);
  }

  @Test
  void shouldUpdateStatus_throwsApplicationNotFoundException_whenApplicationDoesNotExist() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenThrow(
            new ApplicationNotFoundException(
                "No application found with id: %s".formatted(applicationId)));

    assertThatThrownBy(
            () -> applicationUpdateService.updateStatus(applicationId, ApplicationState.COMPLETED))
        .isExactlyInstanceOf(ApplicationNotFoundException.class)
        .hasMessage("No application found with id: %s".formatted(applicationId));

    verify(mockApplicationApi, never()).updateApplication(any(), anyString(), any());
  }

  @Test
  void shouldUpdateStatus_throwsApplicationNotFoundException_whenDatastoreUpdateReturns404() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .applicationState(
                    uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
                .build());
    doThrow(notFound())
        .when(mockApplicationApi)
        .updateApplication(eq(applicationId), anyString(), any());

    assertThatThrownBy(
            () -> applicationUpdateService.updateStatus(applicationId, ApplicationState.COMPLETED))
        .isInstanceOf(ApplicationNotFoundException.class)
        .hasMessageContaining(applicationId.toString());
  }

  @Test
  void shouldUpdateStatus_retriesOnceWithFreshETag_whenDatastoreReturnsConflict() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .applicationState(
                    uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
                .build())
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(2L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .applicationState(
                    uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
                .build());
    doThrow(conflict())
        .doNothing()
        .when(mockApplicationApi)
        .updateApplication(eq(applicationId), anyString(), any());

    applicationUpdateService.updateStatus(applicationId, ApplicationState.COMPLETED);

    verify(mockApplicationGateway, times(2)).fetchApplication(eq(applicationId));
    ArgumentCaptor<UpdateApplicationCommand> commandCaptor =
        ArgumentCaptor.forClass(UpdateApplicationCommand.class);
    verify(mockApplicationApi, times(2))
        .updateApplication(eq(applicationId), anyString(), commandCaptor.capture());
    assertThat(commandCaptor.getAllValues())
        .extracting(UpdateApplicationCommand::geteTag)
        .containsExactly(1L, 2L);
  }

  @Test
  void shouldUpdateStatus_throwsApplicationConflictException_whenConflictPersistsAfterRetry() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .applicationState(
                    uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
                .build());
    doThrow(conflict())
        .when(mockApplicationApi)
        .updateApplication(eq(applicationId), anyString(), any());

    assertThatThrownBy(
            () -> applicationUpdateService.updateStatus(applicationId, ApplicationState.COMPLETED))
        .isInstanceOf(ApplicationConflictException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationGateway, times(2)).fetchApplication(eq(applicationId));
    verify(mockApplicationApi, times(2)).updateApplication(eq(applicationId), anyString(), any());
  }

  @Test
  void shouldUpdateStatus_throwsApplicationConflictException_whenApplicationAlreadyRecorded() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .applicationState(
                    uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.COMPLETED)
                .build());

    assertThatThrownBy(
            () -> applicationUpdateService.updateStatus(applicationId, ApplicationState.COMPLETED))
        .isInstanceOf(ApplicationConflictException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationApi, never()).updateApplication(any(), anyString(), any());
  }

  @Test
  void shouldUpdateStatus_throwsApplicationBadRequestException_whenDatastoreUpdateReturns400() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .applicationState(
                    uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
                .build());
    doThrow(badRequest())
        .when(mockApplicationApi)
        .updateApplication(eq(applicationId), anyString(), any());

    assertThatThrownBy(
            () -> applicationUpdateService.updateStatus(applicationId, ApplicationState.COMPLETED))
        .isInstanceOf(ApplicationBadRequestException.class)
        .hasMessageContaining(applicationId.toString());
  }

  @Test
  void shouldUpdateStatus_throwsApplicationUpstreamErrorException_whenDatastoreUpdateReturns5xx() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .applicationState(
                    uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
                .build());
    doThrow(serverError())
        .when(mockApplicationApi)
        .updateApplication(eq(applicationId), anyString(), any());

    assertThatThrownBy(
            () -> applicationUpdateService.updateStatus(applicationId, ApplicationState.COMPLETED))
        .isInstanceOf(ApplicationUpstreamErrorException.class)
        .hasMessageContaining(applicationId.toString());
  }

  @Test
  void
      shouldUpdateStatus_throwsApplicationUnavailableException_whenDatastoreUpdateFailsToConnect() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .applicationState(
                    uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
                .build());
    doThrow(new ResourceAccessException("Connection refused"))
        .when(mockApplicationApi)
        .updateApplication(eq(applicationId), anyString(), any());

    assertThatThrownBy(
            () -> applicationUpdateService.updateStatus(applicationId, ApplicationState.COMPLETED))
        .isInstanceOf(ApplicationUnavailableException.class)
        .hasMessageContaining(applicationId.toString());
  }

  @Test
  void shouldUpdateStatus_throwsApplicationForbiddenException_whenOfficeCodeNotAuthorized() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode("OTHER-OFFICE")
                .applicationState(
                    uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
                .build());

    assertThatThrownBy(
            () -> applicationUpdateService.updateStatus(applicationId, ApplicationState.COMPLETED))
        .isInstanceOf(ApplicationForbiddenException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationApi, never()).updateApplication(any(), anyString(), any());
  }

  @Test
  void shouldUpdateStatus_throwsApplicationForbiddenException_whenNoOfficesAreAuthorized() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .applicationState(
                    uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
                .build());
    when(mockAuthorizedOfficesProvider.currentAuthorizedOfficeCodes()).thenReturn(List.of());

    assertThatThrownBy(
            () -> applicationUpdateService.updateStatus(applicationId, ApplicationState.COMPLETED))
        .isInstanceOf(ApplicationForbiddenException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationApi, never()).updateApplication(any(), anyString(), any());
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
