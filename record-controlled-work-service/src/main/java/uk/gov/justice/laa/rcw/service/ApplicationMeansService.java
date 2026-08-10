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
import uk.gov.justice.laa.rcw.exception.ApplicationBadRequestException;
import uk.gov.justice.laa.rcw.exception.ApplicationConflictException;
import uk.gov.justice.laa.rcw.exception.ApplicationForbiddenException;
import uk.gov.justice.laa.rcw.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.rcw.exception.ApplicationUnavailableException;
import uk.gov.justice.laa.rcw.exception.ApplicationUpstreamErrorException;
import uk.gov.justice.laa.rcw.logging.StructuredLogger;

/** Service class for updating application means data. */
@Service
@RequiredArgsConstructor
public class ApplicationMeansService {

  private static final StructuredLogger log = StructuredLogger.of(ApplicationMeansService.class);

  private final ApplicationApi applicationApi;
  private final BearerTokenProvider bearerTokenProvider;
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
    ApplicationResponse application = fetchApplication(applicationId);
    checkAuthorizedForOffice(applicationId, application.getProviderOfficeCode());
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
      throw applicationNotFound(applicationId);
    } catch (HttpClientErrorException.Conflict exception) {
      if (!retryOnConflict) {
        throw new ApplicationConflictException(
            "Application %s was modified concurrently".formatted(applicationId));
      }
      updateMeans(applicationId, data, result, false);
      return;
    } catch (HttpClientErrorException.BadRequest exception) {
      throw applicationBadRequest(applicationId);
    } catch (HttpServerErrorException exception) {
      throw applicationUpstreamError(applicationId);
    } catch (ResourceAccessException exception) {
      throw applicationUnavailable(applicationId);
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
    } catch (HttpClientErrorException.BadRequest exception) {
      throw applicationBadRequest(applicationId);
    } catch (HttpServerErrorException exception) {
      throw applicationUpstreamError(applicationId);
    } catch (ResourceAccessException exception) {
      throw applicationUnavailable(applicationId);
    }
  }

  private ApplicationNotFoundException applicationNotFound(UUID applicationId) {
    return new ApplicationNotFoundException(
        "No application found with id: %s".formatted(applicationId));
  }

  private void checkAuthorizedForOffice(UUID applicationId, String providerOfficeCode) {
    if (!authorizedOfficesProvider.currentAuthorizedOfficeCodes().contains(providerOfficeCode)) {
      throw new ApplicationForbiddenException(
          "Not authorized to update application %s".formatted(applicationId));
    }
  }

  private void checkNotAlreadyRecorded(UUID applicationId, ApplicationState applicationState) {
    if (applicationState == ApplicationState.COMPLETED) {
      throw new ApplicationConflictException(
          "Application %s has already been recorded and cannot be updated"
              .formatted(applicationId));
    }
  }

  private ApplicationBadRequestException applicationBadRequest(UUID applicationId) {
    return new ApplicationBadRequestException(
        "Datastore rejected the request for application %s".formatted(applicationId));
  }

  private ApplicationUpstreamErrorException applicationUpstreamError(UUID applicationId) {
    return new ApplicationUpstreamErrorException(
        "Datastore returned an error for application %s".formatted(applicationId));
  }

  private ApplicationUnavailableException applicationUnavailable(UUID applicationId) {
    return new ApplicationUnavailableException(
        "Datastore is unavailable for application %s".formatted(applicationId));
  }
}
