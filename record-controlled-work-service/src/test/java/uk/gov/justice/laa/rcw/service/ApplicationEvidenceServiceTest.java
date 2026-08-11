package uk.gov.justice.laa.rcw.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import uk.gov.justice.laa.ia.datastore.client.model.UpdateEvidenceCommand;
import uk.gov.justice.laa.rcw.exception.ApplicationBadRequestException;
import uk.gov.justice.laa.rcw.exception.ApplicationConflictException;
import uk.gov.justice.laa.rcw.exception.ApplicationForbiddenException;
import uk.gov.justice.laa.rcw.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.rcw.exception.ApplicationUnavailableException;
import uk.gov.justice.laa.rcw.exception.ApplicationUpstreamErrorException;
import uk.gov.justice.laa.rcw.model.UpdateEvidenceRequestBody;

@ExtendWith(MockitoExtension.class)
class ApplicationEvidenceServiceTest {

  private static final String ORIGINAL_TOKEN = "original-incoming-token";
  private static final String AUTHORIZED_OFFICE_CODE = "AB12CD";

  @Mock private ApplicationApi mockApplicationApi;
  @Mock private ApplicationQueryService mockApplicationQueryService;

  private final BearerTokenProvider bearerTokenProvider = new BearerTokenProvider();
  private ApplicationEvidenceService applicationEvidenceService;

  @BeforeEach
  void setUp() {
    applicationEvidenceService =
        new ApplicationEvidenceService(
            mockApplicationApi, bearerTokenProvider, mockApplicationQueryService);
    Jwt jwt =
        Jwt.withTokenValue(ORIGINAL_TOKEN).header("alg", "none").claim("sub", "test-user").build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldUpdateEvidence_fetchesETagAndForwardsFieldsToDatastore() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    UpdateEvidenceRequestBody requestBody =
        new UpdateEvidenceRequestBody()
            .evidenceExemptionCode("EXEMPT")
            .evidenceExemptionReason("reason")
            .incomeEvidenceChecklist(Map.of("payslips", true))
            .expenditureCapitalEvidenceChecklist(Map.of("bankStatements", true));
    when(mockApplicationQueryService.fetchApplicationResponse(applicationId))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(7L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .build());

    applicationEvidenceService.updateEvidence(applicationId, requestBody);

    ArgumentCaptor<String> updateXAuthorizationCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<UpdateEvidenceCommand> commandCaptor =
        ArgumentCaptor.forClass(UpdateEvidenceCommand.class);
    verify(mockApplicationApi)
        .updateEvidence(
            eq(applicationId), updateXAuthorizationCaptor.capture(), commandCaptor.capture());
    assertThat(updateXAuthorizationCaptor.getValue()).isEqualTo("Bearer " + ORIGINAL_TOKEN);
    assertThat(commandCaptor.getValue())
        .isEqualTo(
            UpdateEvidenceCommand.builder()
                .eTag(7L)
                .evidenceExemptionCode("EXEMPT")
                .evidenceExemptionReason("reason")
                .incomeEvidenceChecklist(Map.of("payslips", true))
                .expenditureCapitalEvidenceChecklist(Map.of("bankStatements", true))
                .build());
  }

  @Test
  void shouldUpdateEvidence_throwsApplicationNotFoundException_whenApplicationDoesNotExist() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationQueryService.fetchApplicationResponse(applicationId))
        .thenThrow(
            new ApplicationNotFoundException(
                "No application found with id: %s".formatted(applicationId)));

    assertThatThrownBy(
            () ->
                applicationEvidenceService.updateEvidence(
                    applicationId, new UpdateEvidenceRequestBody()))
        .isInstanceOf(ApplicationNotFoundException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationApi, never()).updateEvidence(any(), anyString(), any());
  }

  @Test
  void shouldUpdateEvidence_throwsApplicationNotFoundException_whenDatastoreUpdateReturns404() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationQueryService.fetchApplicationResponse(applicationId))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .build());
    doThrow(notFound())
        .when(mockApplicationApi)
        .updateEvidence(eq(applicationId), anyString(), any());

    assertThatThrownBy(
            () ->
                applicationEvidenceService.updateEvidence(
                    applicationId, new UpdateEvidenceRequestBody()))
        .isInstanceOf(ApplicationNotFoundException.class)
        .hasMessageContaining(applicationId.toString());
  }

  @Test
  void shouldUpdateEvidence_throwsApplicationConflictException_whenDatastoreUpdateReturns409() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationQueryService.fetchApplicationResponse(applicationId))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .build());
    doThrow(conflict())
        .when(mockApplicationApi)
        .updateEvidence(eq(applicationId), anyString(), any());

    assertThatThrownBy(
            () ->
                applicationEvidenceService.updateEvidence(
                    applicationId, new UpdateEvidenceRequestBody()))
        .isInstanceOf(ApplicationConflictException.class)
        .hasMessageContaining(applicationId.toString());
  }

  @Test
  void shouldUpdateEvidence_throwsApplicationConflictException_whenApplicationAlreadyRecorded() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationQueryService.fetchApplicationResponse(applicationId))
        .thenReturn(ApplicationResponse.builder().eTag(1L).build());
    doThrow(
            new ApplicationConflictException(
                "Application %s has already been recorded".formatted(applicationId)))
        .when(mockApplicationQueryService)
        .checkNotAlreadyRecorded(eq(applicationId), any());

    assertThatThrownBy(
            () ->
                applicationEvidenceService.updateEvidence(
                    applicationId, new UpdateEvidenceRequestBody()))
        .isInstanceOf(ApplicationConflictException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationApi, never()).updateEvidence(any(), anyString(), any());
  }

  @Test
  void shouldUpdateEvidence_throwsBadRequestException_whenFetchingApplicationReturns400() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationQueryService.fetchApplicationResponse(applicationId))
        .thenThrow(
            new ApplicationBadRequestException(
                "Datastore rejected the request for application %s".formatted(applicationId)));

    assertThatThrownBy(
            () ->
                applicationEvidenceService.updateEvidence(
                    applicationId, new UpdateEvidenceRequestBody()))
        .isInstanceOf(ApplicationBadRequestException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationApi, never()).updateEvidence(any(), anyString(), any());
  }

  @Test
  void shouldUpdateEvidence_throwsApplicationBadRequestException_whenDatastoreUpdateReturns400() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationQueryService.fetchApplicationResponse(applicationId))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .build());
    doThrow(badRequest())
        .when(mockApplicationApi)
        .updateEvidence(eq(applicationId), anyString(), any());

    assertThatThrownBy(
            () ->
                applicationEvidenceService.updateEvidence(
                    applicationId, new UpdateEvidenceRequestBody()))
        .isInstanceOf(ApplicationBadRequestException.class)
        .hasMessageContaining(applicationId.toString());
  }

  @Test
  void shouldUpdateEvidence_throwsApplicationUpstreamErrorException_whenFetchingReturns5xx() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationQueryService.fetchApplicationResponse(applicationId))
        .thenThrow(
            new ApplicationUpstreamErrorException(
                "Datastore returned an error for application %s".formatted(applicationId)));

    assertThatThrownBy(
            () ->
                applicationEvidenceService.updateEvidence(
                    applicationId, new UpdateEvidenceRequestBody()))
        .isInstanceOf(ApplicationUpstreamErrorException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationApi, never()).updateEvidence(any(), anyString(), any());
  }

  @Test
  void
      shouldUpdateEvidence_throwsApplicationUpstreamErrorException_whenDatastoreUpdateReturns5xx() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationQueryService.fetchApplicationResponse(applicationId))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .build());
    doThrow(serverError())
        .when(mockApplicationApi)
        .updateEvidence(eq(applicationId), anyString(), any());

    assertThatThrownBy(
            () ->
                applicationEvidenceService.updateEvidence(
                    applicationId, new UpdateEvidenceRequestBody()))
        .isInstanceOf(ApplicationUpstreamErrorException.class)
        .hasMessageContaining(applicationId.toString());
  }

  @Test
  void shouldUpdateEvidence_throwsApplicationUnavailableException_whenFetchingFailsToConnect() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationQueryService.fetchApplicationResponse(applicationId))
        .thenThrow(
            new ApplicationUnavailableException(
                "Datastore is unavailable for application %s".formatted(applicationId)));

    assertThatThrownBy(
            () ->
                applicationEvidenceService.updateEvidence(
                    applicationId, new UpdateEvidenceRequestBody()))
        .isInstanceOf(ApplicationUnavailableException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationApi, never()).updateEvidence(any(), anyString(), any());
  }

  @Test
  void shouldUpdateEvidence_throwsUnavailableException_whenDatastoreUpdateFailsToConnect() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationQueryService.fetchApplicationResponse(applicationId))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .build());
    doThrow(new ResourceAccessException("Connection refused"))
        .when(mockApplicationApi)
        .updateEvidence(eq(applicationId), anyString(), any());

    assertThatThrownBy(
            () ->
                applicationEvidenceService.updateEvidence(
                    applicationId, new UpdateEvidenceRequestBody()))
        .isInstanceOf(ApplicationUnavailableException.class)
        .hasMessageContaining(applicationId.toString());
  }

  @Test
  void shouldUpdateEvidence_throwsApplicationForbiddenException_whenOfficeCodeNotAuthorized() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationQueryService.fetchApplicationResponse(applicationId))
        .thenReturn(ApplicationResponse.builder().eTag(1L).build());
    doThrow(
            new ApplicationForbiddenException(
                "Not authorized to update application %s".formatted(applicationId)))
        .when(mockApplicationQueryService)
        .checkAuthorizedForOffice(eq(applicationId), any());

    assertThatThrownBy(
            () ->
                applicationEvidenceService.updateEvidence(
                    applicationId, new UpdateEvidenceRequestBody()))
        .isInstanceOf(ApplicationForbiddenException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationApi, never()).updateEvidence(any(), anyString(), any());
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
