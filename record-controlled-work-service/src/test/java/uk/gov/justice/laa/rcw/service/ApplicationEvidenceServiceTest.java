package uk.gov.justice.laa.rcw.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationState;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateEvidenceCommand;
import uk.gov.justice.laa.rcw.exception.ApplicationBadRequestException;
import uk.gov.justice.laa.rcw.exception.ApplicationConflictException;
import uk.gov.justice.laa.rcw.exception.ApplicationForbiddenException;
import uk.gov.justice.laa.rcw.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.rcw.gateway.ApplicationGateway;
import uk.gov.justice.laa.rcw.model.UpdateEvidenceRequestBody;

@ExtendWith(MockitoExtension.class)
class ApplicationEvidenceServiceTest {

  private static final String AUTHORIZED_OFFICE_CODE = "AB12CD";

  @Mock private ApplicationGateway mockApplicationGateway;
  @Mock private ApplicationGuard mockApplicationGuard;

  @Test
  void shouldUpdateEvidence_forwardsCommandToGateway() {
    ApplicationEvidenceService applicationEvidenceService =
        new ApplicationEvidenceService(mockApplicationGateway, mockApplicationGuard);
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    UpdateEvidenceRequestBody requestBody =
        new UpdateEvidenceRequestBody()
            .evidenceExemptionCode("EXEMPT")
            .evidenceExemptionReason("reason")
            .incomeEvidenceChecklist(Map.of("payslips", true))
            .expenditureCapitalEvidenceChecklist(Map.of("bankStatements", true));
    when(mockApplicationGateway.fetchApplication(applicationId))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(7L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .build());

    applicationEvidenceService.updateEvidence(applicationId, requestBody);

    ArgumentCaptor<UpdateEvidenceCommand> commandCaptor =
        ArgumentCaptor.forClass(UpdateEvidenceCommand.class);
    verify(mockApplicationGateway).updateEvidence(eq(applicationId), commandCaptor.capture());
    verify(mockApplicationGuard).checkAuthorizedForOffice(applicationId, AUTHORIZED_OFFICE_CODE);
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
    ApplicationEvidenceService applicationEvidenceService =
        new ApplicationEvidenceService(mockApplicationGateway, mockApplicationGuard);
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(applicationId))
        .thenThrow(
            new ApplicationNotFoundException(
                "No application found with id: %s".formatted(applicationId)));

    assertThatThrownBy(
            () ->
                applicationEvidenceService.updateEvidence(
                    applicationId, new UpdateEvidenceRequestBody()))
        .isInstanceOf(ApplicationNotFoundException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationGateway, never()).updateEvidence(any(), any());
  }

  @Test
  void shouldUpdateEvidence_throwsApplicationForbiddenException_whenOfficeCodeNotAuthorized() {
    ApplicationEvidenceService applicationEvidenceService =
        new ApplicationEvidenceService(mockApplicationGateway, mockApplicationGuard);
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(applicationId))
        .thenReturn(ApplicationResponse.builder().eTag(1L).build());
    doThrow(
            new ApplicationForbiddenException(
                "Not authorized to update application %s".formatted(applicationId)))
        .when(mockApplicationGuard)
        .checkAuthorizedForOffice(eq(applicationId), any());

    assertThatThrownBy(
            () ->
                applicationEvidenceService.updateEvidence(
                    applicationId, new UpdateEvidenceRequestBody()))
        .isInstanceOf(ApplicationForbiddenException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationGateway, never()).updateEvidence(any(), any());
  }

  @Test
  void shouldUpdateEvidence_throwsApplicationConflictException_whenApplicationAlreadyRecorded() {
    ApplicationEvidenceService applicationEvidenceService =
        new ApplicationEvidenceService(mockApplicationGateway, mockApplicationGuard);
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(applicationId))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .applicationState(ApplicationState.COMPLETED)
                .build());

    assertThatThrownBy(
            () ->
                applicationEvidenceService.updateEvidence(
                    applicationId, new UpdateEvidenceRequestBody()))
        .isInstanceOf(ApplicationConflictException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationGateway, never()).updateEvidence(any(), any());
  }

  @Test
  void shouldUpdateEvidence_propagatesException_whenGatewayUpdateFails() {
    ApplicationEvidenceService applicationEvidenceService =
        new ApplicationEvidenceService(mockApplicationGateway, mockApplicationGuard);
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(applicationId))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .build());
    doThrow(
            new ApplicationBadRequestException(
                "Datastore rejected the request for application %s".formatted(applicationId)))
        .when(mockApplicationGateway)
        .updateEvidence(eq(applicationId), any());

    assertThatThrownBy(
            () ->
                applicationEvidenceService.updateEvidence(
                    applicationId, new UpdateEvidenceRequestBody()))
        .isInstanceOf(ApplicationBadRequestException.class)
        .hasMessageContaining(applicationId.toString());
  }
}
