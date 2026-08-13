package uk.gov.justice.laa.rcw.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
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
import uk.gov.justice.laa.ia.datastore.client.model.StartApplicationCommand;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateApplicationCommand;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateEvidenceCommand;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateMeansDataCommand;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateScopingDataCommand;
import uk.gov.justice.laa.rcw.exception.ApplicationBadRequestException;
import uk.gov.justice.laa.rcw.exception.ApplicationConflictException;
import uk.gov.justice.laa.rcw.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.rcw.exception.ApplicationUnavailableException;
import uk.gov.justice.laa.rcw.exception.ApplicationUpstreamErrorException;
import uk.gov.justice.laa.rcw.service.BearerTokenProvider;

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
  void shouldStartApplication() {
    String officeCode = "AB12CD";
    StartApplicationCommand command =
        StartApplicationCommand.builder().providerOfficeCode(officeCode).build();
    ApplicationResponse response =
        ApplicationResponse.builder()
            .id(UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"))
            .build();
    when(mockApplicationApi.startApplication(BEARER_TOKEN, command)).thenReturn(response);

    ApplicationResponse result = applicationGateway.startApplication(officeCode, command);

    assertThat(result).isEqualTo(response);
    verify(mockApplicationApi).startApplication(eq(BEARER_TOKEN), eq(command));
  }

  @Test
  void
      shouldStartApplication_throwsApplicationBadRequestException_whenDatastoreReturnsBadRequest() {
    String officeCode = "AB12CD";
    when(mockApplicationApi.startApplication(eq(BEARER_TOKEN), any())).thenThrow(badRequest());

    assertThatThrownBy(
            () ->
                applicationGateway.startApplication(
                    officeCode,
                    StartApplicationCommand.builder().providerOfficeCode(officeCode).build()))
        .isExactlyInstanceOf(ApplicationBadRequestException.class)
        .hasMessage("Datastore rejected the request for office %s".formatted(officeCode));
  }

  @Test
  void shouldStartApplication_throwsApplicationUpstreamErrorException_whenDatastoreReturns5xx() {
    String officeCode = "AB12CD";
    when(mockApplicationApi.startApplication(eq(BEARER_TOKEN), any())).thenThrow(serverError());

    assertThatThrownBy(
            () ->
                applicationGateway.startApplication(
                    officeCode,
                    StartApplicationCommand.builder().providerOfficeCode(officeCode).build()))
        .isExactlyInstanceOf(ApplicationUpstreamErrorException.class)
        .hasMessage("Datastore returned an error for office %s".formatted(officeCode));
  }

  @Test
  void shouldStartApplication_throwsApplicationUnavailableException_whenDatastoreIsUnavailable() {
    String officeCode = "AB12CD";
    when(mockApplicationApi.startApplication(eq(BEARER_TOKEN), any()))
        .thenThrow(new ResourceAccessException("Connection refused"));

    assertThatThrownBy(
            () ->
                applicationGateway.startApplication(
                    officeCode,
                    StartApplicationCommand.builder().providerOfficeCode(officeCode).build()))
        .isExactlyInstanceOf(ApplicationUnavailableException.class)
        .hasMessage("Datastore is unavailable for office %s".formatted(officeCode));
  }

  @Test
  void shouldUpdateScopingData() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    UpdateScopingDataCommand command =
        UpdateScopingDataCommand.builder().eTag(5L).scopingQuestions(Map.of("a", "b")).build();

    applicationGateway.updateScopingData(applicationId, command);

    verify(mockApplicationApi).updateScopingData(eq(applicationId), eq(BEARER_TOKEN), eq(command));
  }

  @Test
  void shouldUpdateScopingData_throwsApplicationNotFoundException_whenDatastoreReturnsNotFound() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(notFound())
        .when(mockApplicationApi)
        .updateScopingData(eq(applicationId), eq(BEARER_TOKEN), any());

    assertThatThrownBy(
            () ->
                applicationGateway.updateScopingData(
                    applicationId, UpdateScopingDataCommand.builder().eTag(5L).build()))
        .isExactlyInstanceOf(ApplicationNotFoundException.class)
        .hasMessage("No application found with id: %s".formatted(applicationId));
  }

  @Test
  void shouldUpdateScopingData_throwsApplicationConflictException_whenDatastoreReturnsConflict() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(conflict())
        .when(mockApplicationApi)
        .updateScopingData(eq(applicationId), eq(BEARER_TOKEN), any());

    assertThatThrownBy(
            () ->
                applicationGateway.updateScopingData(
                    applicationId, UpdateScopingDataCommand.builder().eTag(5L).build()))
        .isExactlyInstanceOf(ApplicationConflictException.class)
        .hasMessage("Application %s was modified concurrently".formatted(applicationId));
  }

  @Test
  void shouldUpdateScopingData_throwsBadRequestException_whenDatastoreReturnsBadRequest() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    String message = "Datastore rejected the request for application %s".formatted(applicationId);

    doThrow(badRequest())
        .when(mockApplicationApi)
        .updateScopingData(eq(applicationId), eq(BEARER_TOKEN), any());

    assertThatThrownBy(
            () ->
                applicationGateway.updateScopingData(
                    applicationId, UpdateScopingDataCommand.builder().eTag(5L).build()))
        .isExactlyInstanceOf(ApplicationBadRequestException.class)
        .hasMessage(message);
  }

  @Test
  void shouldUpdateScopingData_throwsApplicationUpstreamErrorException_whenDatastoreReturns5xx() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(serverError())
        .when(mockApplicationApi)
        .updateScopingData(eq(applicationId), eq(BEARER_TOKEN), any());

    assertThatThrownBy(
            () ->
                applicationGateway.updateScopingData(
                    applicationId, UpdateScopingDataCommand.builder().eTag(5L).build()))
        .isExactlyInstanceOf(ApplicationUpstreamErrorException.class)
        .hasMessage("Datastore returned an error for application %s".formatted(applicationId));
  }

  @Test
  void shouldUpdateScopingData_throwsApplicationUnavailableException_whenDatastoreIsUnavailable() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(new ResourceAccessException("Connection refused"))
        .when(mockApplicationApi)
        .updateScopingData(eq(applicationId), eq(BEARER_TOKEN), any());

    assertThatThrownBy(
            () ->
                applicationGateway.updateScopingData(
                    applicationId, UpdateScopingDataCommand.builder().eTag(5L).build()))
        .isExactlyInstanceOf(ApplicationUnavailableException.class)
        .hasMessage("Datastore is unavailable for application %s".formatted(applicationId));
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
  void shouldFetchApplication_throwsApplicationNotFoundException_whenDatastoreReturnsNotFound() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationApi.getApplication(applicationId, BEARER_TOKEN)).thenThrow(notFound());

    assertThatThrownBy(() -> applicationGateway.fetchApplication(applicationId))
        .isExactlyInstanceOf(ApplicationNotFoundException.class)
        .hasMessage("No application found with id: %s".formatted(applicationId));
  }

  @Test
  void
      shouldFetchApplication_throwsApplicationBadRequestException_whenDatastoreReturnsBadRequest() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationApi.getApplication(applicationId, BEARER_TOKEN)).thenThrow(badRequest());

    assertThatThrownBy(() -> applicationGateway.fetchApplication(applicationId))
        .isExactlyInstanceOf(ApplicationBadRequestException.class)
        .hasMessage("Datastore rejected the request for application %s".formatted(applicationId));
  }

  @Test
  void shouldFetchApplication_throwsApplicationUpstreamErrorException_whenDatastoreReturns5xx() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationApi.getApplication(applicationId, BEARER_TOKEN)).thenThrow(serverError());

    assertThatThrownBy(() -> applicationGateway.fetchApplication(applicationId))
        .isExactlyInstanceOf(ApplicationUpstreamErrorException.class)
        .hasMessage("Datastore returned an error for application %s".formatted(applicationId));
  }

  @Test
  void shouldFetchApplication_throwsApplicationUnavailableException_whenDatastoreIsUnavailable() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationApi.getApplication(applicationId, BEARER_TOKEN))
        .thenThrow(new ResourceAccessException("Connection refused"));

    assertThatThrownBy(() -> applicationGateway.fetchApplication(applicationId))
        .isExactlyInstanceOf(ApplicationUnavailableException.class)
        .hasMessage("Datastore is unavailable for application %s".formatted(applicationId));
  }

  @Test
  void shouldUpdateMeansData() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    UpdateMeansDataCommand command =
        UpdateMeansDataCommand.builder().eTag(1L).data("d").result("r").build();

    applicationGateway.updateMeansData(applicationId, command);

    verify(mockApplicationApi).updateMeansData(eq(applicationId), eq(BEARER_TOKEN), eq(command));
  }

  @Test
  void shouldUpdateMeansData_throwsApplicationConflictException_whenDatastoreReturnsConflict() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(conflict())
        .when(mockApplicationApi)
        .updateMeansData(eq(applicationId), eq(BEARER_TOKEN), any());

    assertThatThrownBy(
            () ->
                applicationGateway.updateMeansData(
                    applicationId,
                    UpdateMeansDataCommand.builder().eTag(1L).data("d").result("r").build()))
        .isExactlyInstanceOf(ApplicationConflictException.class)
        .hasMessage("Application %s was modified concurrently".formatted(applicationId));
  }

  @Test
  void shouldUpdateMeansData_throwsApplicationNotFoundException_whenDatastoreReturnsNotFound() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(notFound())
        .when(mockApplicationApi)
        .updateMeansData(eq(applicationId), eq(BEARER_TOKEN), any());

    assertThatThrownBy(
            () ->
                applicationGateway.updateMeansData(
                    applicationId,
                    UpdateMeansDataCommand.builder().eTag(1L).data("d").result("r").build()))
        .isExactlyInstanceOf(ApplicationNotFoundException.class)
        .hasMessage("No application found with id: %s".formatted(applicationId));
  }

  @Test
  void shouldUpdateMeansData_throwsApplicationBadRequestException_whenDatastoreReturnsBadRequest() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(badRequest())
        .when(mockApplicationApi)
        .updateMeansData(eq(applicationId), eq(BEARER_TOKEN), any());

    assertThatThrownBy(
            () ->
                applicationGateway.updateMeansData(
                    applicationId,
                    UpdateMeansDataCommand.builder().eTag(1L).data("d").result("r").build()))
        .isExactlyInstanceOf(ApplicationBadRequestException.class)
        .hasMessage("Datastore rejected the request for application %s".formatted(applicationId));
  }

  @Test
  void shouldUpdateMeansData_throwsApplicationUpstreamErrorException_whenDatastoreReturns5xx() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(serverError())
        .when(mockApplicationApi)
        .updateMeansData(eq(applicationId), eq(BEARER_TOKEN), any());

    assertThatThrownBy(
            () ->
                applicationGateway.updateMeansData(
                    applicationId,
                    UpdateMeansDataCommand.builder().eTag(1L).data("d").result("r").build()))
        .isExactlyInstanceOf(ApplicationUpstreamErrorException.class)
        .hasMessage("Datastore returned an error for application %s".formatted(applicationId));
  }

  @Test
  void shouldUpdateMeansData_throwsApplicationUnavailableException_whenDatastoreIsUnavailable() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(new ResourceAccessException("Connection refused"))
        .when(mockApplicationApi)
        .updateMeansData(eq(applicationId), eq(BEARER_TOKEN), any());

    assertThatThrownBy(
            () ->
                applicationGateway.updateMeansData(
                    applicationId,
                    UpdateMeansDataCommand.builder().eTag(1L).data("d").result("r").build()))
        .isExactlyInstanceOf(ApplicationUnavailableException.class)
        .hasMessage("Datastore is unavailable for application %s".formatted(applicationId));
  }

  @Test
  void shouldUpdateEvidence() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    UpdateEvidenceCommand command =
        UpdateEvidenceCommand.builder().eTag(1L).evidenceExemptionCode("EXEMPT").build();

    applicationGateway.updateEvidence(applicationId, command);

    verify(mockApplicationApi).updateEvidence(eq(applicationId), eq(BEARER_TOKEN), eq(command));
  }

  @Test
  void shouldUpdateEvidence_throwsApplicationConflictException_whenDatastoreReturnsConflict() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    UpdateEvidenceCommand command =
        UpdateEvidenceCommand.builder().eTag(1L).evidenceExemptionCode("EXEMPT").build();
    doThrow(conflict())
        .when(mockApplicationApi)
        .updateEvidence(eq(applicationId), eq(BEARER_TOKEN), any());

    assertThatThrownBy(() -> applicationGateway.updateEvidence(applicationId, command))
        .isExactlyInstanceOf(ApplicationConflictException.class)
        .hasMessage("Application %s was modified concurrently".formatted(applicationId));
  }

  @Test
  void shouldUpdateEvidence_throwsApplicationNotFoundException_whenDatastoreReturnsNotFound() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    UpdateEvidenceCommand command =
        UpdateEvidenceCommand.builder().eTag(1L).evidenceExemptionCode("EXEMPT").build();
    doThrow(notFound())
        .when(mockApplicationApi)
        .updateEvidence(eq(applicationId), eq(BEARER_TOKEN), any());

    assertThatThrownBy(() -> applicationGateway.updateEvidence(applicationId, command))
        .isExactlyInstanceOf(ApplicationNotFoundException.class)
        .hasMessage("No application found with id: %s".formatted(applicationId));
  }

  @Test
  void shouldUpdateEvidence_throwsApplicationBadRequestException_whenDatastoreReturnsBadRequest() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    UpdateEvidenceCommand command =
        UpdateEvidenceCommand.builder().eTag(1L).evidenceExemptionCode("EXEMPT").build();
    doThrow(badRequest())
        .when(mockApplicationApi)
        .updateEvidence(eq(applicationId), eq(BEARER_TOKEN), any());

    assertThatThrownBy(() -> applicationGateway.updateEvidence(applicationId, command))
        .isExactlyInstanceOf(ApplicationBadRequestException.class)
        .hasMessage("Datastore rejected the request for application %s".formatted(applicationId));
  }

  @Test
  void shouldUpdateEvidence_throwsApplicationUpstreamErrorException_whenDatastoreReturns5xx() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    UpdateEvidenceCommand command =
        UpdateEvidenceCommand.builder().eTag(1L).evidenceExemptionCode("EXEMPT").build();
    doThrow(serverError())
        .when(mockApplicationApi)
        .updateEvidence(eq(applicationId), eq(BEARER_TOKEN), any());

    assertThatThrownBy(() -> applicationGateway.updateEvidence(applicationId, command))
        .isExactlyInstanceOf(ApplicationUpstreamErrorException.class)
        .hasMessage("Datastore returned an error for application %s".formatted(applicationId));
  }

  @Test
  void shouldUpdateEvidence_throwsApplicationUnavailableException_whenDatastoreIsUnavailable() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    UpdateEvidenceCommand command =
        UpdateEvidenceCommand.builder().eTag(1L).evidenceExemptionCode("EXEMPT").build();
    doThrow(new ResourceAccessException("Connection refused"))
        .when(mockApplicationApi)
        .updateEvidence(eq(applicationId), eq(BEARER_TOKEN), any());

    assertThatThrownBy(() -> applicationGateway.updateEvidence(applicationId, command))
        .isExactlyInstanceOf(ApplicationUnavailableException.class)
        .hasMessage("Datastore is unavailable for application %s".formatted(applicationId));
  }

  @Test
  void shouldUpdateApplication() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    UpdateApplicationCommand command =
        UpdateApplicationCommand.builder()
            .eTag(2L)
            .applicationState(uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
            .build();

    applicationGateway.updateApplication(applicationId, command);

    verify(mockApplicationApi).updateApplication(eq(applicationId), eq(BEARER_TOKEN), eq(command));
  }

  @Test
  void shouldUpdateApplication_throwsApplicationConflictException_whenDatastoreReturnsConflict() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(conflict())
        .when(mockApplicationApi)
        .updateApplication(eq(applicationId), eq(BEARER_TOKEN), any());

    assertThatThrownBy(
            () ->
                applicationGateway.updateApplication(
                    applicationId,
                    UpdateApplicationCommand.builder()
                        .eTag(2L)
                        .applicationState(
                            uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
                        .build()))
        .isExactlyInstanceOf(ApplicationConflictException.class)
        .hasMessage("Application %s was modified concurrently".formatted(applicationId));
  }

  @Test
  void shouldUpdateApplication_throwsApplicationNotFoundException_whenDatastoreReturnsNotFound() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(notFound())
        .when(mockApplicationApi)
        .updateApplication(eq(applicationId), eq(BEARER_TOKEN), any());

    assertThatThrownBy(
            () ->
                applicationGateway.updateApplication(
                    applicationId,
                    UpdateApplicationCommand.builder()
                        .eTag(2L)
                        .applicationState(
                            uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
                        .build()))
        .isExactlyInstanceOf(ApplicationNotFoundException.class)
        .hasMessage("No application found with id: %s".formatted(applicationId));
  }

  @Test
  void shouldUpdateApplication_throwsApplicationBadRequestException_whenDatastoreReturns400() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(badRequest())
        .when(mockApplicationApi)
        .updateApplication(eq(applicationId), eq(BEARER_TOKEN), any());

    assertThatThrownBy(
            () ->
                applicationGateway.updateApplication(
                    applicationId,
                    UpdateApplicationCommand.builder()
                        .eTag(2L)
                        .applicationState(
                            uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
                        .build()))
        .isExactlyInstanceOf(ApplicationBadRequestException.class)
        .hasMessage("Datastore rejected the request for application %s".formatted(applicationId));
  }

  @Test
  void shouldUpdateApplication_throwsApplicationUpstreamErrorException_whenDatastoreReturns5xx() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(serverError())
        .when(mockApplicationApi)
        .updateApplication(eq(applicationId), eq(BEARER_TOKEN), any());

    assertThatThrownBy(
            () ->
                applicationGateway.updateApplication(
                    applicationId,
                    UpdateApplicationCommand.builder()
                        .eTag(2L)
                        .applicationState(
                            uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
                        .build()))
        .isExactlyInstanceOf(ApplicationUpstreamErrorException.class)
        .hasMessage("Datastore returned an error for application %s".formatted(applicationId));
  }

  @Test
  void shouldUpdateApplication_throwsApplicationUnavailableException_whenDatastoreIsUnavailable() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(new ResourceAccessException("Connection refused"))
        .when(mockApplicationApi)
        .updateApplication(eq(applicationId), eq(BEARER_TOKEN), any());

    assertThatThrownBy(
            () ->
                applicationGateway.updateApplication(
                    applicationId,
                    UpdateApplicationCommand.builder()
                        .eTag(2L)
                        .applicationState(
                            uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
                        .build()))
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

  private static HttpClientErrorException.Conflict conflict() {
    return (HttpClientErrorException.Conflict)
        HttpClientErrorException.create(
            HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY, new byte[0], null);
  }

  private static HttpServerErrorException serverError() {
    return new HttpServerErrorException(HttpStatus.BAD_GATEWAY);
  }
}
