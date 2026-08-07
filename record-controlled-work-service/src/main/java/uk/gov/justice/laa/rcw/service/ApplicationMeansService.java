package uk.gov.justice.laa.rcw.service;

import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_MEANS_UPDATE;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.justice.laa.ia.datastore.client.api.ApplicationApi;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateMeansDataCommand;
import uk.gov.justice.laa.rcw.exception.ApplicationConflictException;
import uk.gov.justice.laa.rcw.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.rcw.logging.StructuredLogger;

/** Service class for updating application means data. */
@Service
@RequiredArgsConstructor
public class ApplicationMeansService {

  private static final StructuredLogger log = StructuredLogger.of(ApplicationMeansService.class);

  private final ApplicationApi applicationApi;
  private final BearerTokenProvider bearerTokenProvider;

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
    Long etag = fetchApplication(applicationId).geteTag();
    UpdateMeansDataCommand command =
        UpdateMeansDataCommand.builder().eTag(etag).data(data).result(result).build();
    try {
      applicationApi.updateMeansData(
          applicationId, bearerTokenProvider.currentBearerToken(), command);
    } catch (HttpClientErrorException.NotFound exception) {
      throw applicationNotFound(applicationId);
    } catch (HttpClientErrorException.Conflict exception) {
      if (!retryOnConflict) {
        throw new ApplicationConflictException(
            "Application %s was modified concurrently".formatted(applicationId));
      }
      updateMeans(applicationId, data, result, false);
      return;
    }
    log.info()
        .action(APPLICATION_MEANS_UPDATE)
        .outcome("success")
        .with("application.id", applicationId)
        .log("Updated means data for application {}", applicationId);
  }

  private ApplicationResponse fetchApplication(UUID applicationId) {
    try {
      return applicationApi.getApplication(applicationId, bearerTokenProvider.currentBearerToken());
    } catch (HttpClientErrorException.NotFound exception) {
      throw applicationNotFound(applicationId);
    }
  }

  private ApplicationNotFoundException applicationNotFound(UUID applicationId) {
    return new ApplicationNotFoundException(
        "No application found with id: %s".formatted(applicationId));
  }
}
