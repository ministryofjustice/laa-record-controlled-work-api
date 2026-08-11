package uk.gov.justice.laa.rcw.service;

import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_CREATE;

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

    updateScopingData(applicationResponse, applicationRequestBody, bearerToken, true);

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
      throw badRequestForOffice(applicationRequestBody.getProviderOfficeCode());
    } catch (HttpServerErrorException exception) {
      throw upstreamErrorForOffice(applicationRequestBody.getProviderOfficeCode());
    } catch (ResourceAccessException exception) {
      throw unavailableErrorForOffice(applicationRequestBody.getProviderOfficeCode());
    }
  }

  private void updateScopingData(
      ApplicationResponse applicationResponse,
      CreateApplicationRequestBody applicationRequestBody,
      String bearerToken,
      boolean retryOnConflict) {
    try {
      applicationApi.updateScopingData(
          applicationResponse.getId(),
          bearerToken,
          UpdateScopingDataCommand.builder()
              .eTag(applicationResponse.geteTag())
              .scopingQuestions(applicationRequestBody.getScopingQuestions())
              .build());
    } catch (HttpClientErrorException.NotFound exception) {
      throw notFoundForApplication(applicationResponse.getId());
    } catch (HttpClientErrorException.Conflict exception) {
      retryScopingUpdateOnConflict(
          applicationResponse, applicationRequestBody, bearerToken, retryOnConflict);
    } catch (HttpClientErrorException.BadRequest exception) {
      throw badRequestForApplication(applicationResponse.getId());
    } catch (HttpServerErrorException exception) {
      throw upstreamErrorForApplication(applicationResponse.getId());
    } catch (ResourceAccessException exception) {
      throw unavailableErrorForApplication(applicationResponse.getId());
    }
  }

  private void retryScopingUpdateOnConflict(
      ApplicationResponse applicationResponse,
      CreateApplicationRequestBody applicationRequestBody,
      String bearerToken,
      boolean retryOnConflict) {
    if (!retryOnConflict) {
      throw conflictError(applicationResponse.getId());
    }

    ApplicationResponse refreshedApplication =
        fetchApplication(applicationResponse.getId(), bearerToken);

    updateScopingData(refreshedApplication, applicationRequestBody, bearerToken, false);
  }

  private ApplicationResponse fetchApplication(java.util.UUID applicationId, String bearerToken) {
    try {
      return applicationApi.getApplication(applicationId, bearerToken);
    } catch (HttpClientErrorException.NotFound exception) {
      throw notFoundForApplication(applicationId);
    } catch (HttpClientErrorException.BadRequest exception) {
      throw badRequestForApplication(applicationId);
    } catch (HttpServerErrorException exception) {
      throw upstreamErrorForApplication(applicationId);
    } catch (ResourceAccessException exception) {
      throw unavailableErrorForApplication(applicationId);
    }
  }

  private void checkAuthorizedForOffice(String providerOfficeCode) {
    if (!authorizedOfficesProvider.currentAuthorizedOfficeCodes().contains(providerOfficeCode)) {
      throw new ApplicationForbiddenException(
          "Not authorized to create application for office %s".formatted(providerOfficeCode));
    }
  }

  private ApplicationNotFoundException notFoundForApplication(java.util.UUID applicationId) {
    return new ApplicationNotFoundException(
        "No application found with id: %s".formatted(applicationId));
  }

  private ApplicationBadRequestException badRequestForApplication(java.util.UUID applicationId) {
    return new ApplicationBadRequestException(
        "Datastore rejected the request for application %s".formatted(applicationId));
  }

  private ApplicationBadRequestException badRequestForOffice(String providerOfficeCode) {
    return new ApplicationBadRequestException(
        "Datastore rejected the request for office %s".formatted(providerOfficeCode));
  }

  private ApplicationConflictException conflictError(java.util.UUID applicationId) {
    return new ApplicationConflictException(
        "Application %s was modified concurrently".formatted(applicationId));
  }

  private ApplicationUpstreamErrorException upstreamErrorForApplication(
      java.util.UUID applicationId) {
    return new ApplicationUpstreamErrorException(
        "Datastore returned an error for application %s".formatted(applicationId));
  }

  private ApplicationUpstreamErrorException upstreamErrorForOffice(String providerOfficeCode) {
    return new ApplicationUpstreamErrorException(
        "Datastore returned an error for office %s".formatted(providerOfficeCode));
  }

  private ApplicationUnavailableException unavailableErrorForApplication(
      java.util.UUID applicationId) {
    return new ApplicationUnavailableException(
        "Datastore is unavailable for application %s".formatted(applicationId));
  }

  private ApplicationUnavailableException unavailableErrorForOffice(String providerOfficeCode) {
    return new ApplicationUnavailableException(
        "Datastore is unavailable for office %s".formatted(providerOfficeCode));
  }
}
