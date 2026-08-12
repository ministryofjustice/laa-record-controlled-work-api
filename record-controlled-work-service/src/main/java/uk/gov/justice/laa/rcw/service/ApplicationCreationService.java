package uk.gov.justice.laa.rcw.service;

import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_CREATE;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import uk.gov.justice.laa.ia.datastore.client.api.ApplicationApi;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateScopingDataCommand;
import uk.gov.justice.laa.rcw.exception.ApplicationBadRequestException;
import uk.gov.justice.laa.rcw.exception.ApplicationConflictException;
import uk.gov.justice.laa.rcw.exception.ApplicationForbiddenException;
import uk.gov.justice.laa.rcw.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.rcw.exception.ApplicationUnavailableException;
import uk.gov.justice.laa.rcw.exception.ApplicationUpstreamErrorException;
import uk.gov.justice.laa.rcw.logging.StructuredLogger;
import uk.gov.justice.laa.rcw.mapper.ApplicationMapper;
import uk.gov.justice.laa.rcw.model.Application;
import uk.gov.justice.laa.rcw.model.CreateApplicationRequestBody;

/** Service class for creating Applications. */
@Service
@RequiredArgsConstructor
public class ApplicationCreationService {

  private static final StructuredLogger log = StructuredLogger.of(ApplicationCreationService.class);

  private final ApplicationApi applicationApi;
  private final ApplicationMapper applicationMapper;
  private final BearerTokenProvider bearerTokenProvider;
  private final AuthorizedOfficesProvider authorizedOfficesProvider;

  /**
   * Creates an application by forwarding the RCW request to the datastore.
   *
   * @return the created application
   */
  public Application createApplication(CreateApplicationRequestBody applicationRequestBody) {
    checkAuthorizedForOffice(applicationRequestBody.getProviderOfficeCode());
    String bearerToken = bearerTokenProvider.currentBearerToken();
    ApplicationResponse applicationResponse = startApplication(applicationRequestBody, bearerToken);

    updateScopingData(applicationResponse, applicationRequestBody, bearerToken);

    Application application = applicationMapper.toApplication(applicationResponse);

    log.info()
        .action(APPLICATION_CREATE)
        .outcome("success")
        .with("application.id", application.getId())
        .log("Created application {}", application.getId());
    return application;
  }

  private ApplicationResponse startApplication(
      CreateApplicationRequestBody applicationRequestBody, String bearerToken) {
    try {
      return applicationApi.startApplication(
          bearerToken, applicationMapper.toStartApplicationCommand(applicationRequestBody));
    } catch (HttpClientErrorException.BadRequest exception) {
      throw badRequestError("office %s".formatted(applicationRequestBody.getProviderOfficeCode()));
    } catch (HttpServerErrorException exception) {
      throw upstreamError("office %s".formatted(applicationRequestBody.getProviderOfficeCode()));
    } catch (ResourceAccessException exception) {
      throw unavailableError("office %s".formatted(applicationRequestBody.getProviderOfficeCode()));
    }
  }

  private void updateScopingData(
      ApplicationResponse applicationResponse,
      CreateApplicationRequestBody applicationRequestBody,
      String bearerToken) {
    try {
      applicationApi.updateScopingData(
          applicationResponse.getId(),
          bearerToken,
          UpdateScopingDataCommand.builder()
              .eTag(applicationResponse.geteTag())
              .scopingQuestions(applicationRequestBody.getScopingQuestions())
              .build());
    } catch (HttpClientErrorException.NotFound exception) {
      throw notFoundError(applicationResponse.getId());
    } catch (HttpClientErrorException.Conflict exception) {
      throw conflictError(applicationResponse.getId());
    } catch (HttpClientErrorException.BadRequest exception) {
      throw badRequestError("application %s".formatted(applicationResponse.getId()));
    } catch (HttpServerErrorException exception) {
      throw upstreamError("application %s".formatted(applicationResponse.getId()));
    } catch (ResourceAccessException exception) {
      throw unavailableError("application %s".formatted(applicationResponse.getId()));
    }
  }

  private void checkAuthorizedForOffice(String providerOfficeCode) {
    if (!authorizedOfficesProvider.currentAuthorizedOfficeCodes().contains(providerOfficeCode)) {
      throw new ApplicationForbiddenException(
          "Not authorized to create application for office %s".formatted(providerOfficeCode));
    }
  }

  private ApplicationNotFoundException notFoundError(UUID applicationId) {
    return new ApplicationNotFoundException(
        "No application found with id: %s".formatted(applicationId));
  }

  private ApplicationBadRequestException badRequestError(String identifier) {
    return new ApplicationBadRequestException(
        "Datastore rejected the request for %s".formatted(identifier));
  }

  private ApplicationConflictException conflictError(UUID applicationId) {
    return new ApplicationConflictException(
        "Application %s was modified concurrently".formatted(applicationId));
  }

  private ApplicationUpstreamErrorException upstreamError(String identifier) {
    return new ApplicationUpstreamErrorException(
        "Datastore returned an error for %s".formatted(identifier));
  }

  private ApplicationUnavailableException unavailableError(String identifier) {
    return new ApplicationUnavailableException(
        "Datastore is unavailable for %s".formatted(identifier));
  }
}
