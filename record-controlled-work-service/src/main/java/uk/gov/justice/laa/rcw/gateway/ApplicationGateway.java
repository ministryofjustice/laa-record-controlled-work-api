package uk.gov.justice.laa.rcw.gateway;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import uk.gov.justice.laa.ia.datastore.client.api.ApplicationApi;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.StartApplicationCommand;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateApplicationCommand;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateEvidenceCommand;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateMeansDataCommand;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateScopingDataCommand;
import uk.gov.justice.laa.rcw.exception.ApplicationBadRequestException;
import uk.gov.justice.laa.rcw.exception.ApplicationConflictException;
import uk.gov.justice.laa.rcw.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.rcw.exception.ApplicationUnavailableException;
import uk.gov.justice.laa.rcw.exception.ApplicationUpstreamErrorException;
import uk.gov.justice.laa.rcw.service.BearerTokenProvider;

/** Gateway for datastore application fetch operations. */
@Service
@RequiredArgsConstructor
public class ApplicationGateway {

  private final ApplicationApi applicationApi;
  private final BearerTokenProvider bearerTokenProvider;

  /**
   * Starts an application in datastore and translates transport-level failures to RCW application
   * exceptions.
   *
   * @param providerOfficeCode provider office code used in error messages
   * @param command start application command
   * @return the created application response from datastore
   */
  public ApplicationResponse startApplication(
      String providerOfficeCode, StartApplicationCommand command) {
    try {
      return applicationApi.startApplication(bearerTokenProvider.currentBearerToken(), command);
    } catch (HttpClientErrorException.BadRequest exception) {
      throw badRequestForOffice(providerOfficeCode);
    } catch (HttpServerErrorException exception) {
      throw upstreamErrorForOffice(providerOfficeCode);
    } catch (ResourceAccessException exception) {
      throw unavailableErrorForOffice(providerOfficeCode);
    }
  }

  /**
   * Updates scoping data in datastore and translates transport-level failures to RCW application
   * exceptions.
   *
   * @param applicationId the application id
   * @param command scoping update command
   */
  public void updateScopingData(UUID applicationId, UpdateScopingDataCommand command) {
    try {
      applicationApi.updateScopingData(
          applicationId, bearerTokenProvider.currentBearerToken(), command);
    } catch (HttpClientErrorException.NotFound exception) {
      throw notFound(applicationId);
    } catch (HttpClientErrorException.Conflict exception) {
      throw conflict(applicationId);
    } catch (HttpClientErrorException.BadRequest exception) {
      throw badRequestForApplication(applicationId);
    } catch (HttpServerErrorException exception) {
      throw upstreamErrorForApplication(applicationId);
    } catch (ResourceAccessException exception) {
      throw unavailableErrorForApplication(applicationId);
    }
  }

  /**
   * Fetches an application from datastore and translates transport-level failures to RCW
   * application exceptions.
   *
   * @param applicationId the application id
   * @return the application response from datastore
   */
  public ApplicationResponse fetchApplication(UUID applicationId) {
    try {
      return applicationApi.getApplication(applicationId, bearerTokenProvider.currentBearerToken());
    } catch (HttpClientErrorException.NotFound exception) {
      throw notFound(applicationId);
    } catch (HttpClientErrorException.BadRequest exception) {
      throw badRequestForApplication(applicationId);
    } catch (HttpServerErrorException exception) {
      throw upstreamErrorForApplication(applicationId);
    } catch (ResourceAccessException exception) {
      throw unavailableErrorForApplication(applicationId);
    }
  }

  /**
   * Updates means data in datastore and translates transport-level failures to RCW application
   * exceptions.
   *
   * @param applicationId the application id
   * @param command means update command
   */
  public void updateMeansData(UUID applicationId, UpdateMeansDataCommand command) {
    try {
      applicationApi.updateMeansData(
          applicationId, bearerTokenProvider.currentBearerToken(), command);
    } catch (HttpClientErrorException.NotFound exception) {
      throw notFound(applicationId);
    } catch (HttpClientErrorException.Conflict exception) {
      throw conflict(applicationId);
    } catch (HttpClientErrorException.BadRequest exception) {
      throw badRequestForApplication(applicationId);
    } catch (HttpServerErrorException exception) {
      throw upstreamErrorForApplication(applicationId);
    } catch (ResourceAccessException exception) {
      throw unavailableErrorForApplication(applicationId);
    }
  }

  /**
   * Updates evidence data in datastore and translates transport-level failures to RCW application
   * exceptions.
   *
   * @param applicationId the application id
   * @param command evidence update command
   */
  public void updateEvidence(UUID applicationId, UpdateEvidenceCommand command) {
    try {
      applicationApi.updateEvidence(
          applicationId, bearerTokenProvider.currentBearerToken(), command);
    } catch (HttpClientErrorException.NotFound exception) {
      throw notFound(applicationId);
    } catch (HttpClientErrorException.Conflict exception) {
      throw conflict(applicationId);
    } catch (HttpClientErrorException.BadRequest exception) {
      throw badRequestForApplication(applicationId);
    } catch (HttpServerErrorException exception) {
      throw upstreamErrorForApplication(applicationId);
    } catch (ResourceAccessException exception) {
      throw unavailableErrorForApplication(applicationId);
    }
  }

  /**
   * Updates application status in datastore and translates transport-level failures to RCW
   * application exceptions.
   *
   * @param applicationId the application id
   * @param command application status update command
   */
  public void updateApplication(UUID applicationId, UpdateApplicationCommand command) {
    try {
      applicationApi.updateApplication(
          applicationId, bearerTokenProvider.currentBearerToken(), command);
    } catch (HttpClientErrorException.NotFound exception) {
      throw notFound(applicationId);
    } catch (HttpClientErrorException.Conflict exception) {
      throw conflict(applicationId);
    } catch (HttpClientErrorException.BadRequest exception) {
      throw badRequestForApplication(applicationId);
    } catch (HttpServerErrorException exception) {
      throw upstreamErrorForApplication(applicationId);
    } catch (ResourceAccessException exception) {
      throw unavailableErrorForApplication(applicationId);
    }
  }

  private ApplicationNotFoundException notFound(UUID applicationId) {
    return new ApplicationNotFoundException(
        "No application found with id: %s".formatted(applicationId));
  }

  private ApplicationConflictException conflict(UUID applicationId) {
    return new ApplicationConflictException(
        "Application %s was modified concurrently".formatted(applicationId));
  }

  private ApplicationBadRequestException badRequestForApplication(UUID applicationId) {
    return new ApplicationBadRequestException(
        "Datastore rejected the request for application %s".formatted(applicationId));
  }

  private ApplicationUpstreamErrorException upstreamErrorForApplication(UUID applicationId) {
    return new ApplicationUpstreamErrorException(
        "Datastore returned an error for application %s".formatted(applicationId));
  }

  private ApplicationUnavailableException unavailableErrorForApplication(UUID applicationId) {
    return new ApplicationUnavailableException(
        "Datastore is unavailable for application %s".formatted(applicationId));
  }

  private ApplicationBadRequestException badRequestForOffice(String providerOfficeCode) {
    return new ApplicationBadRequestException(
        "Datastore rejected the request for office %s".formatted(providerOfficeCode));
  }

  private ApplicationUpstreamErrorException upstreamErrorForOffice(String providerOfficeCode) {
    return new ApplicationUpstreamErrorException(
        "Datastore returned an error for office %s".formatted(providerOfficeCode));
  }

  private ApplicationUnavailableException unavailableErrorForOffice(String providerOfficeCode) {
    return new ApplicationUnavailableException(
        "Datastore is unavailable for office %s".formatted(providerOfficeCode));
  }
}
