package uk.gov.justice.laa.rcw.service;

import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_STATUS_UPDATE;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import uk.gov.justice.laa.ia.datastore.client.api.ApplicationApi;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateApplicationCommand;
import uk.gov.justice.laa.rcw.gateway.ApplicationGateway;
import uk.gov.justice.laa.rcw.logging.StructuredLogger;
import uk.gov.justice.laa.rcw.mapper.ApplicationMapper;
import uk.gov.justice.laa.rcw.model.ApplicationState;

/** Service class for updating application status. */
@Service
@RequiredArgsConstructor
public class ApplicationUpdateService extends ApplicationServiceBase {

  private static final StructuredLogger log = StructuredLogger.of(ApplicationUpdateService.class);

  private final ApplicationApi applicationApi;
  private final ApplicationMapper applicationMapper;
  private final BearerTokenProvider bearerTokenProvider;
  private final ApplicationGateway applicationGateway;
  private final AuthorizedOfficesProvider authorizedOfficesProvider;

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
    checkAuthorizedForOffice(
        applicationId, application.getProviderOfficeCode(), authorizedOfficesProvider);
    checkNotAlreadyRecorded(applicationId, application.getApplicationState());
    UpdateApplicationCommand command =
        UpdateApplicationCommand.builder()
            .eTag(application.geteTag())
            .applicationState(applicationMapper.toDatastoreApplicationState(status))
            .build();

    try {
      applicationApi.updateApplication(
          applicationId, bearerTokenProvider.currentBearerToken(), command);
    } catch (HttpClientErrorException.NotFound exception) {
      throw applicationNotFoundError(applicationId);
    } catch (HttpClientErrorException.Conflict exception) {
      if (!retryOnConflict) {
        throw applicationConflictError(applicationId);
      }
      updateStatus(applicationId, status, false);
      return;
    } catch (HttpClientErrorException.BadRequest exception) {
      throw badRequestError(applicationId);
    } catch (HttpServerErrorException exception) {
      throw upstreamError(applicationId);
    } catch (ResourceAccessException exception) {
      throw unavailableError(applicationId);
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
      throw applicationAlreadyRecordedError(applicationId);
    }
  }
}
