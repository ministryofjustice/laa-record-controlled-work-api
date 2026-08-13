package uk.gov.justice.laa.rcw.service;

import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_EVIDENCE_UPDATE;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationState;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateEvidenceCommand;
import uk.gov.justice.laa.rcw.exception.ApplicationConflictException;
import uk.gov.justice.laa.rcw.gateway.ApplicationGateway;
import uk.gov.justice.laa.rcw.logging.StructuredLogger;
import uk.gov.justice.laa.rcw.model.UpdateEvidenceRequestBody;

/** Service class for updating application evidence data. */
@Service
@RequiredArgsConstructor
public class ApplicationEvidenceService {

  private static final StructuredLogger log = StructuredLogger.of(ApplicationEvidenceService.class);

  private final ApplicationGateway applicationGateway;
  private final ApplicationGuard applicationGuard;

  /**
   * Updates the evidence data for an application.
   *
   * @param applicationId the application id
   * @param requestBody the evidence update request body
   */
  public void updateEvidence(UUID applicationId, UpdateEvidenceRequestBody requestBody) {
    ApplicationResponse application = applicationGateway.fetchApplication(applicationId);
    applicationGuard.checkAuthorizedForOffice(applicationId, application.getProviderOfficeCode());
    checkNotAlreadyRecorded(applicationId, application.getApplicationState());
    UpdateEvidenceCommand command =
        UpdateEvidenceCommand.builder()
            .eTag(application.geteTag())
            .evidenceExemptionCode(requestBody.getEvidenceExemptionCode())
            .evidenceExemptionReason(requestBody.getEvidenceExemptionReason())
            .incomeEvidenceChecklist(requestBody.getIncomeEvidenceChecklist())
            .expenditureCapitalEvidenceChecklist(
                requestBody.getExpenditureCapitalEvidenceChecklist())
            .build();
    applicationGateway.updateEvidence(applicationId, command);
    log.info()
        .action(APPLICATION_EVIDENCE_UPDATE)
        .outcome("success")
        .with("application.id", applicationId)
        .log("Updated evidence data for application {}", applicationId);
  }

  private void checkNotAlreadyRecorded(UUID applicationId, ApplicationState applicationState) {
    if (applicationState == ApplicationState.COMPLETED) {
      throw new ApplicationConflictException(
          "Application %s has already been recorded and cannot be updated"
              .formatted(applicationId));
    }
  }
}
