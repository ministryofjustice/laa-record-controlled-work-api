package uk.gov.justice.laa.rcw.service;

import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_STATUS_UPDATE;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateApplicationCommand;
import uk.gov.justice.laa.rcw.exception.ApplicationConflictException;
import uk.gov.justice.laa.rcw.gateway.ApplicationGateway;
import uk.gov.justice.laa.rcw.logging.StructuredLogger;
import uk.gov.justice.laa.rcw.mapper.ApplicationMapper;
import uk.gov.justice.laa.rcw.model.ApplicationState;

/** Service class for updating application status. */
@Service
@RequiredArgsConstructor
public class ApplicationUpdateService {

  private static final StructuredLogger log = StructuredLogger.of(ApplicationUpdateService.class);

  private final ApplicationMapper applicationMapper;
  private final ApplicationGateway applicationGateway;
  private final ApplicationGuard applicationGuard;

  /**
   * Updates the application status. Datastore requires an eTag for optimistic concurrency, so the
   * current application is fetched first; on conflict, the update is retried once with a refreshed
   * eTag.
   *
   * @param applicationId the application id
   * @param status the target status
   */
  public void updateStatus(UUID applicationId, ApplicationState status) {
    updateStatus(applicationId, status, true);
  }

  private void updateStatus(UUID applicationId, ApplicationState status, boolean retryOnConflict) {
    ApplicationResponse application = applicationGateway.fetchApplication(applicationId);
    applicationGuard.checkAuthorizedForOffice(applicationId, application.getProviderOfficeCode());
    checkNotAlreadyRecorded(applicationId, application.getApplicationState());
    UpdateApplicationCommand command =
        UpdateApplicationCommand.builder()
            .eTag(application.geteTag())
            .applicationState(applicationMapper.toDatastoreApplicationState(status))
            .build();

    try {
      applicationGateway.updateApplication(applicationId, command);
    } catch (ApplicationConflictException exception) {
      if (!retryOnConflict) {
        throw new ApplicationConflictException(
            "Application %s was modified concurrently".formatted(applicationId));
      }
      updateStatus(applicationId, status, false);
      return;
    }

    log.info()
        .action(APPLICATION_STATUS_UPDATE)
        .outcome("success")
        .with("application.id", applicationId)
        .with("application.status", status)
        .log("Updated application status for application {}", applicationId);
  }

  private void checkNotAlreadyRecorded(
      UUID applicationId, uk.gov.justice.laa.ia.datastore.client.model.ApplicationState state) {
    if (state == uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.COMPLETED) {
      throw new ApplicationConflictException(
          "Application %s has already been recorded and cannot be updated".formatted(applicationId),
          "APPLICATION_ALREADY_RECORDED");
    }
  }
}
