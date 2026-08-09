package uk.gov.justice.laa.rcw.service;

import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_FETCH;
import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_LIST;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.justice.laa.ia.datastore.client.api.ApplicationApi;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponses;
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
