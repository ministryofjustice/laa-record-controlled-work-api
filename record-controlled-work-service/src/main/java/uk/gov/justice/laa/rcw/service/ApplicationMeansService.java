package uk.gov.justice.laa.rcw.service;

import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_MEANS_UPDATE;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import uk.gov.justice.laa.ia.datastore.client.api.ApplicationApi;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationState;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateMeansDataCommand;
import uk.gov.justice.laa.rcw.gateway.ApplicationGateway;
import uk.gov.justice.laa.rcw.logging.StructuredLogger;

/** Service class for updating application means data. */
@Service
@RequiredArgsConstructor
public class ApplicationMeansService extends ApplicationServiceBase {

  private static final StructuredLogger log = StructuredLogger.of(ApplicationMeansService.class);

  private final ApplicationApi applicationApi;
  private final BearerTokenProvider bearerTokenProvider;
  private final ApplicationGateway applicationGateway;
  private final AuthorizedOfficesProvider authorizedOfficesProvider;

  /**
   * Updates the means data for an application. The datastore requires an eTag for optimistic
   * concurrency, so the current application is fetched first to source it; if the update conflicts
   * with a concurrent modification, the eTag is re-fetched and the update is retried once.
   *
   * @param applicationId the application id
   * @param data the means Q&amp;A data
   * @param result the means calculation result
   */
  public void updateMeans(UUID applicationId, Object data, Object result) {
    updateMeans(applicationId, data, result, true);
  }

  private void updateMeans(
      UUID applicationId, Object data, Object result, boolean retryOnConflict) {
    ApplicationResponse application = applicationGateway.fetchApplication(applicationId);
    checkAuthorizedForOffice(
        applicationId, application.getProviderOfficeCode(), authorizedOfficesProvider);
    checkNotAlreadyRecorded(applicationId, application.getApplicationState());
    UpdateMeansDataCommand command =
        UpdateMeansDataCommand.builder()
            .eTag(application.geteTag())
            .data(data)
            .result(result)
            .build();
    try {
      applicationApi.updateMeansData(
          applicationId, bearerTokenProvider.currentBearerToken(), command);
    } catch (HttpClientErrorException.NotFound exception) {
      throw applicationNotFoundError(applicationId);
    } catch (HttpClientErrorException.Conflict exception) {
      if (!retryOnConflict) {
        throw applicationConflictError(applicationId);
      }
      updateMeans(applicationId, data, result, false);
      return;
    } catch (HttpClientErrorException.BadRequest exception) {
      throw badRequestError(applicationId);
    } catch (HttpServerErrorException exception) {
      throw upstreamError(applicationId);
    } catch (ResourceAccessException exception) {
      throw unavailableError(applicationId);
    }
    log.info()
        .action(APPLICATION_MEANS_UPDATE)
        .outcome("success")
        .with("application.id", applicationId)
        .log("Updated means data for application {}", applicationId);
  }

  private void checkNotAlreadyRecorded(UUID applicationId, ApplicationState applicationState) {
    if (applicationState == ApplicationState.COMPLETED) {
      throw applicationAlreadyRecordedError(applicationId);
    }
  }
}
