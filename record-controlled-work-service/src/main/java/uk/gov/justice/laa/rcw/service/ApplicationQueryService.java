package uk.gov.justice.laa.rcw.service;

import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_FETCH;
import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_LIST;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import uk.gov.justice.laa.ia.datastore.client.api.ApplicationApi;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponses;
import uk.gov.justice.laa.rcw.exception.ApplicationBadRequestException;
import uk.gov.justice.laa.rcw.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.rcw.exception.ApplicationUnavailableException;
import uk.gov.justice.laa.rcw.exception.ApplicationUpstreamErrorException;
import uk.gov.justice.laa.rcw.logging.StructuredLogger;
import uk.gov.justice.laa.rcw.mapper.ApplicationMapper;
import uk.gov.justice.laa.rcw.model.Application;
import uk.gov.justice.laa.rcw.model.ApplicationOverview;
import uk.gov.justice.laa.rcw.model.ApplicationState;

/** Service class for querying Applications. */
@Service
@RequiredArgsConstructor
public class ApplicationQueryService {

  private static final StructuredLogger log = StructuredLogger.of(ApplicationQueryService.class);

  private final ApplicationApi applicationApi;
  private final ApplicationMapper applicationMapper;
  private final BearerTokenProvider bearerTokenProvider;

  /**
   * Gets all Applications.
   *
   * @return the list of Applications
   */
  public List<ApplicationOverview> getApplications(
      Integer page, Integer size, String officeId, ApplicationState status) {
    ApplicationResponses responses =
        applicationApi.getApplications(
            bearerTokenProvider.currentBearerToken(),
            page,
            size,
            officeId,
            applicationMapper.toDatastoreApplicationState(status));
    List<ApplicationOverview> applications =
        responses.getContent().stream().map(applicationMapper::toApplicationOverview).toList();
    log.info()
        .action(APPLICATION_LIST)
        .outcome("success")
        .log("Retrieved {} applications", applications.size());
    return applications;
  }

  /**
   * Fetches the raw datastore {@link ApplicationResponse} for an application, throwing typed
   * exceptions for all datastore error conditions. Package-private for use by update services that
   * need the eTag and raw fields before mapping.
   *
   * @param applicationId the application id
   * @return the raw {@link ApplicationResponse}
   */
  ApplicationResponse fetchApplicationResponse(UUID applicationId) {
    try {
      return applicationApi.getApplication(applicationId, bearerTokenProvider.currentBearerToken());
    } catch (HttpClientErrorException.NotFound exception) {
      throw new ApplicationNotFoundException(
          "No application found with id: %s".formatted(applicationId));
    } catch (HttpClientErrorException.BadRequest exception) {
      throw new ApplicationBadRequestException(
          "Datastore rejected the request for application %s".formatted(applicationId));
    } catch (HttpServerErrorException exception) {
      throw new ApplicationUpstreamErrorException(
          "Datastore returned an error for application %s".formatted(applicationId));
    } catch (ResourceAccessException exception) {
      throw new ApplicationUnavailableException(
          "Datastore is unavailable for application %s".formatted(applicationId));
    }
  }

  /**
   * Gets an Application or empty optional if not found.
   *
   * @return {@link Optional} of {@link Application}
   */
  public Optional<Application> getApplication(UUID applicationId) {
    Optional<Application> application;
    try {
      application =
          Optional.of(
              applicationMapper.toApplication(
                  applicationApi.getApplication(
                      applicationId, bearerTokenProvider.currentBearerToken())));
    } catch (HttpClientErrorException.NotFound exception) {
      return Optional.empty();
    }
    log.info()
        .action(APPLICATION_FETCH)
        .outcome("success")
        .with("application.id", applicationId)
        .log("Retrieved application {}", applicationId);
    return application;
  }
}
