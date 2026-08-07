package uk.gov.justice.laa.rcw.service;

import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_CREATE;
import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_FETCH;
import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_LIST;
import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_MEANS_UPDATE;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.justice.laa.ia.datastore.client.api.ApplicationApi;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponses;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateMeansDataCommand;
import uk.gov.justice.laa.rcw.exception.ApplicationConflictException;
import uk.gov.justice.laa.rcw.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.rcw.logging.StructuredLogger;
import uk.gov.justice.laa.rcw.mapper.ApplicationMapper;
import uk.gov.justice.laa.rcw.model.Application;
import uk.gov.justice.laa.rcw.model.ApplicationOverview;
import uk.gov.justice.laa.rcw.model.ApplicationState;
import uk.gov.justice.laa.rcw.model.CreateApplicationRequestBody;
import uk.gov.justice.laa.rcw.model.CreateApplicationResponseBody;

/** Service class for handling Application requests. */
@Service
@RequiredArgsConstructor
public class ApplicationService {

  private static final StructuredLogger log = StructuredLogger.of(ApplicationService.class);

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
    Optional<Application> application =
        Optional.of(StubApplicationFactory.stubApplication(applicationId));
    log.info()
        .action(APPLICATION_FETCH)
        .outcome("success")
        .with("application.id", applicationId)
        .log("Retrieved application {}", applicationId);
    return application;
  }

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

  /**
   * Create application. This is a temporary return so that we can test the integration before
   * connecting to the data store. TODO: Replace with Data Store API call
   *
   * @return the request body with the created ID
   */
  public CreateApplicationResponseBody createApplication(
      CreateApplicationRequestBody applicationRequestBody) {

    CreateApplicationResponseBody responseBody = new CreateApplicationResponseBody();

    BeanUtils.copyProperties(applicationRequestBody, responseBody);

    responseBody.id(UUID.fromString("69e24085-60f9-43c5-9574-7544502f6905"));

    log.info()
        .action(APPLICATION_CREATE)
        .outcome("success")
        .with("application.id", responseBody.getId())
        .log("Created application {}", responseBody.getId());
    return responseBody;
  }
}
