package uk.gov.justice.laa.rcw.service;

import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_FETCH;
import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_LIST;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponses;
import uk.gov.justice.laa.rcw.exception.ApplicationConflictException;
import uk.gov.justice.laa.rcw.exception.ApplicationForbiddenException;
import uk.gov.justice.laa.rcw.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.rcw.exception.ApplicationUpstreamErrorException;
import uk.gov.justice.laa.rcw.gateway.ApplicationGateway;
import uk.gov.justice.laa.rcw.logging.StructuredLogger;
import uk.gov.justice.laa.rcw.mapper.ApplicationMapper;
import uk.gov.justice.laa.rcw.mapper.EligibilityMapper;
import uk.gov.justice.laa.rcw.model.Application;
import uk.gov.justice.laa.rcw.model.ApplicationOverview;
import uk.gov.justice.laa.rcw.model.ApplicationState;
import uk.gov.justice.laa.rcw.model.EligibilityIndication;
import uk.gov.justice.laa.rcw.util.ApplicationVersionParser;

/** Service class for querying Applications. */
@Service
@RequiredArgsConstructor
public class ApplicationQueryService {

  private static final StructuredLogger log = StructuredLogger.of(ApplicationQueryService.class);

  private final ApplicationGateway applicationGateway;
  private final ApplicationMapper applicationMapper;
  private final EligibilityMapper eligibilityMapper;
  private final ApplicationGuard applicationGuard;

  /**
   * Gets all Applications.
   *
   * @return the list of Applications
   */
  public List<ApplicationOverview> getApplications(
      Integer page,
      Integer size,
      String officeId,
      ApplicationState status,
      EligibilityIndication eligibilityIndication) {
    ApplicationResponses responses =
        applicationGateway.getApplications(
            page,
            size,
            officeId,
            applicationMapper.toDatastoreApplicationState(status),
            eligibilityMapper.toDatastoreEligibilityIndication(eligibilityIndication));
    List<ApplicationOverview> applications =
        responses.getContent().stream().map(applicationMapper::toApplicationOverview).toList();
    log.info()
        .action(APPLICATION_LIST)
        .outcome("success")
        .log("Retrieved {} applications", applications.size());
    return applications;
  }

  /**
   * Checks that the current user is authorised to access the given application office, throwing
   * {@link ApplicationForbiddenException} if not. Package-private for use by update services.
   *
   * @param applicationId the application id (for error messages)
   * @param providerOfficeCode the office code on the application
   */
  void checkAuthorizedForOffice(UUID applicationId, String providerOfficeCode) {
    applicationGuard.checkAuthorizedForOffice(applicationId, providerOfficeCode);
  }

  /**
   * Checks that the application has not already been recorded (COMPLETED state), throwing {@link
   * ApplicationConflictException} if it has. Package-private for use by update services.
   *
   * @param applicationId the application id (for error messages)
   * @param applicationState the current state of the application
   */
  void checkNotAlreadyRecorded(
      UUID applicationId,
      uk.gov.justice.laa.ia.datastore.client.model.ApplicationState applicationState) {
    if (applicationState
        == uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.COMPLETED) {
      throw new ApplicationConflictException(
          "Application %s has already been recorded and cannot be updated".formatted(applicationId),
          "APPLICATION_ALREADY_RECORDED");
    }
  }

  /**
   * Gets an Application or empty optional if not found.
   *
   * @return the application and its original datastore version, when visible
   */
  public Optional<VersionedApplication> getApplication(UUID applicationId) {
    ApplicationResponse response;
    try {
      response = applicationGateway.fetchApplicationDetails(applicationId);
      applicationGuard.checkVisibleForOffice(applicationId, response.getProviderOfficeCode());
    } catch (ApplicationNotFoundException exception) {
      return Optional.empty();
    }
    OptionalLong version = ApplicationVersionParser.parseVersion(response.geteTag());
    if (version.isEmpty()) {
      throw new ApplicationUpstreamErrorException(
          "Datastore returned an invalid application version",
          "DATASTORE_INVALID_APPLICATION_VERSION");
    }
    log.info()
        .action(APPLICATION_FETCH)
        .outcome("success")
        .with("application.id", applicationId)
        .log("Retrieved application {}", applicationId);
    return Optional.of(
        new VersionedApplication(applicationMapper.toApplication(response), version.getAsLong()));
  }

  /** A mapped application with its original datastore version, outside the public body. */
  public record VersionedApplication(Application application, long version) {}
}
