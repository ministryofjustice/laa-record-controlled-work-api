package uk.gov.justice.laa.rcw.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import uk.gov.justice.laa.ia.datastore.client.api.ApplicationApi;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponses;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationState;
import uk.gov.justice.laa.ia.datastore.client.model.DeclarationCommand;
import uk.gov.justice.laa.ia.datastore.client.model.EditApplicationCommand;
import uk.gov.justice.laa.ia.datastore.client.model.EligibilityIndication;
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
import uk.gov.justice.laa.rcw.service.DatastoreRequestContext;
import uk.gov.justice.laa.rcw.util.ApplicationVersionParser;

/** Gateway for datastore application fetch operations. */
@Service
@RequiredArgsConstructor
public class ApplicationGateway {

  private static final JsonMapper RESPONSE_MAPPER =
      JsonMapper.builder()
          .addModule(new JavaTimeModule())
          .addModule(new JsonNullableModule())
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .build();

  private final ApplicationApi applicationApi;
  private final BearerTokenProvider bearerTokenProvider;
  private final DatastoreRequestContext datastoreRequestContext;

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
      return applicationApi.startApplication(
          bearerTokenProvider.currentBearerToken(),
          datastoreRequestContext.correlationId(),
          datastoreRequestContext.serviceName(),
          command);
    } catch (HttpClientErrorException.BadRequest exception) {
      throw badRequestForOffice(providerOfficeCode, exception);
    } catch (HttpServerErrorException exception) {
      throw upstreamErrorForOffice(providerOfficeCode, exception);
    } catch (ResourceAccessException exception) {
      throw unavailableErrorForOffice(providerOfficeCode, exception);
    }
  }

  /**
   * Fetches applications from datastore and translates transport-level failures to RCW application
   * exceptions.
   *
   * @param page the page number
   * @param size the page size
   * @param providerOfficeCode the provider office code used in error messages
   * @param status the application state filter
   * @param eligibilityIndication the eligibility indication filter
   * @return the paginated application responses from datastore
   */
  public ApplicationResponses getApplications(
      Integer page,
      Integer size,
      String providerOfficeCode,
      ApplicationState status,
      EligibilityIndication eligibilityIndication) {
    try {
      return applicationApi.getApplications(
          bearerTokenProvider.currentBearerToken(),
          datastoreRequestContext.correlationId(),
          datastoreRequestContext.serviceName(),
          page,
          size,
          providerOfficeCode,
          status,
          eligibilityIndication);
    } catch (HttpClientErrorException.BadRequest exception) {
      throw badRequestForOffice(providerOfficeCode, exception);
    } catch (HttpServerErrorException exception) {
      throw upstreamErrorForOffice(providerOfficeCode, exception);
    } catch (ResourceAccessException exception) {
      throw unavailableErrorForOffice(providerOfficeCode, exception);
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
          applicationId,
          bearerTokenProvider.currentBearerToken(),
          datastoreRequestContext.correlationId(),
          datastoreRequestContext.serviceName(),
          command);
    } catch (HttpClientErrorException.NotFound exception) {
      throw notFound(applicationId, exception);
    } catch (HttpClientErrorException.Conflict exception) {
      throw conflict(applicationId, exception);
    } catch (HttpClientErrorException.BadRequest exception) {
      throw badRequestForApplication(applicationId, exception);
    } catch (HttpServerErrorException exception) {
      throw upstreamErrorForApplication(applicationId, exception);
    } catch (ResourceAccessException exception) {
      throw unavailableErrorForApplication(applicationId, exception);
    }
  }

  /**
   * Fetches GET details without allowing numeric coercion to invent a datastore version.
   *
   * @param applicationId the application id
   * @return the application response with an unknown version if the body version is invalid
   */
  public ApplicationResponse fetchApplicationDetails(UUID applicationId) {
    try {
      ApplicationResponse response = fetchApplication(applicationId);
      if (response == null) {
        throw new ApplicationUpstreamErrorException(
            "Datastore returned an invalid application version",
            "DATASTORE_INVALID_APPLICATION_VERSION");
      }
      return response;
    } catch (HttpClientErrorException.Forbidden exception) {
      throw notFound(applicationId, exception);
    } catch (RestClientException | IllegalArgumentException exception) {
      throw new ApplicationUpstreamErrorException(
          "Datastore returned an invalid application response",
          "DATASTORE_INVALID_RESPONSE",
          exception);
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
      return applicationApi.getApplication(
          applicationId,
          bearerTokenProvider.currentBearerToken(),
          datastoreRequestContext.correlationId(),
          datastoreRequestContext.serviceName());
    } catch (HttpClientErrorException.NotFound exception) {
      throw notFound(applicationId, exception);
    } catch (HttpClientErrorException.BadRequest exception) {
      throw badRequestForApplication(applicationId, exception);
    } catch (HttpServerErrorException exception) {
      throw upstreamErrorForApplication(applicationId, exception);
    } catch (ResourceAccessException exception) {
      throw unavailableErrorForApplication(applicationId, exception);
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
          applicationId,
          bearerTokenProvider.currentBearerToken(),
          datastoreRequestContext.correlationId(),
          datastoreRequestContext.serviceName(),
          command);
    } catch (HttpClientErrorException.NotFound exception) {
      throw notFound(applicationId, exception);
    } catch (HttpClientErrorException.Conflict exception) {
      throw conflict(applicationId, exception);
    } catch (HttpClientErrorException.BadRequest exception) {
      throw badRequestForApplication(applicationId, exception);
    } catch (HttpServerErrorException exception) {
      throw upstreamErrorForApplication(applicationId, exception);
    } catch (ResourceAccessException exception) {
      throw unavailableErrorForApplication(applicationId, exception);
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
          applicationId,
          bearerTokenProvider.currentBearerToken(),
          datastoreRequestContext.correlationId(),
          datastoreRequestContext.serviceName(),
          command);
    } catch (HttpClientErrorException.NotFound exception) {
      throw notFound(applicationId, exception);
    } catch (HttpClientErrorException.Conflict exception) {
      throw conflict(applicationId, exception);
    } catch (HttpClientErrorException.BadRequest exception) {
      throw badRequestForApplication(applicationId, exception);
    } catch (HttpServerErrorException exception) {
      throw upstreamErrorForApplication(applicationId, exception);
    } catch (ResourceAccessException exception) {
      throw unavailableErrorForApplication(applicationId, exception);
    }
  }

  /**
   * Updates declaration data in datastore and translates transport-level failures to RCW
   * application exceptions.
   *
   * @param applicationId the application id
   * @param command declaration update command
   */
  public void updateDeclarationData(UUID applicationId, DeclarationCommand command) {
    try {
      applicationApi.updateDeclarationData(
          applicationId,
          bearerTokenProvider.currentBearerToken(),
          datastoreRequestContext.correlationId(),
          datastoreRequestContext.serviceName(),
          command);
    } catch (HttpClientErrorException.NotFound exception) {
      throw notFound(applicationId, exception);
    } catch (HttpClientErrorException.Conflict exception) {
      throw conflict(applicationId, exception);
    } catch (HttpClientErrorException.BadRequest exception) {
      throw badRequestForApplication(applicationId, exception);
    } catch (HttpServerErrorException exception) {
      throw upstreamErrorForApplication(applicationId, exception);
    } catch (ResourceAccessException exception) {
      throw unavailableErrorForApplication(applicationId, exception);
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
          applicationId,
          bearerTokenProvider.currentBearerToken(),
          datastoreRequestContext.correlationId(),
          datastoreRequestContext.serviceName(),
          command);
    } catch (HttpClientErrorException.NotFound exception) {
      throw notFound(applicationId, exception);
    } catch (HttpClientErrorException.Conflict exception) {
      throw conflict(applicationId, exception);
    } catch (HttpClientErrorException.BadRequest exception) {
      throw badRequestForApplication(applicationId, exception);
    } catch (HttpServerErrorException exception) {
      throw upstreamErrorForApplication(applicationId, exception);
    } catch (ResourceAccessException exception) {
      throw unavailableErrorForApplication(applicationId, exception);
    }
  }

  /**
   * Edits application details and returns the validated downstream ETag.
   *
   * @param applicationId the application id
   * @param command complete editable details and caller version
   * @return the validated datastore ETag
   */
  public String editApplication(UUID applicationId, EditApplicationCommand command) {
    try {
      ResponseEntity<Void> response =
          applicationApi.editApplicationWithHttpInfo(
              applicationId,
              bearerTokenProvider.currentBearerToken(),
              datastoreRequestContext.correlationId(),
              datastoreRequestContext.serviceName(),
              command);
      return requireValidEtag(response);
    } catch (HttpClientErrorException.NotFound exception) {
      throw notFound(applicationId, exception);
    } catch (HttpClientErrorException.Forbidden exception) {
      throw notFound(applicationId, exception);
    } catch (HttpClientErrorException.Conflict exception) {
      throw detailsConflict(applicationId, exception);
    } catch (HttpClientErrorException.BadRequest exception) {
      throw badRequestForApplication(applicationId, exception);
    } catch (HttpServerErrorException exception) {
      throw upstreamErrorForApplication(applicationId, exception);
    } catch (ResourceAccessException exception) {
      throw unavailableErrorForApplication(applicationId, exception);
    }
  }

  private String requireValidEtag(ResponseEntity<Void> response) {
    if (response != null) {
      String etag = response.getHeaders().getETag();
      if (ApplicationVersionParser.parseIfMatch(etag).isPresent()) {
        return etag;
      }
    }
    throw new ApplicationUpstreamErrorException(
        "Datastore returned an invalid application version",
        "DATASTORE_INVALID_APPLICATION_VERSION");
  }

  private ApplicationNotFoundException notFound(UUID applicationId, Throwable cause) {
    return new ApplicationNotFoundException(
        "No application found with id: %s".formatted(applicationId), cause);
  }

  private ApplicationConflictException conflict(UUID applicationId, Throwable cause) {
    return new ApplicationConflictException(
        "Application %s was modified concurrently".formatted(applicationId), cause);
  }

  private ApplicationConflictException detailsConflict(
      UUID applicationId, HttpClientErrorException.Conflict exception) {
    String reason = downstreamConflictReason(exception);
    if ("APPLICATION_VERSION_CONFLICT".equals(reason)) {
      return new ApplicationConflictException(
          "Application %s was modified concurrently".formatted(applicationId), reason, exception);
    }
    if ("APPLICATION_COMPLETED".equals(reason)) {
      return new ApplicationConflictException(
          "Application %s has already been completed".formatted(applicationId), reason, exception);
    }
    return conflict(applicationId, exception);
  }

  private String downstreamConflictReason(HttpClientErrorException.Conflict exception) {
    try {
      JsonNode body = RESPONSE_MAPPER.readTree(exception.getResponseBodyAsString());
      JsonNode reason = body == null ? null : body.get("reason");
      return reason != null && reason.isTextual() ? reason.textValue() : null;
    } catch (JsonProcessingException | IllegalArgumentException ignored) {
      return null;
    }
  }

  private ApplicationBadRequestException badRequestForApplication(
      UUID applicationId, Throwable cause) {
    return new ApplicationBadRequestException(
        "Datastore rejected the request for application %s".formatted(applicationId), cause);
  }

  private ApplicationUpstreamErrorException upstreamErrorForApplication(
      UUID applicationId, Throwable cause) {
    return new ApplicationUpstreamErrorException(
        "Datastore returned an error for application %s".formatted(applicationId), cause);
  }

  private ApplicationUnavailableException unavailableErrorForApplication(
      UUID applicationId, Throwable cause) {
    return new ApplicationUnavailableException(
        "Datastore is unavailable for application %s".formatted(applicationId), cause);
  }

  private ApplicationBadRequestException badRequestForOffice(
      String providerOfficeCode, Throwable cause) {
    return new ApplicationBadRequestException(
        "Datastore rejected the request for office %s".formatted(providerOfficeCode), cause);
  }

  private ApplicationUpstreamErrorException upstreamErrorForOffice(
      String providerOfficeCode, Throwable cause) {
    return new ApplicationUpstreamErrorException(
        "Datastore returned an error for office %s".formatted(providerOfficeCode), cause);
  }

  private ApplicationUnavailableException unavailableErrorForOffice(
      String providerOfficeCode, Throwable cause) {
    return new ApplicationUnavailableException(
        "Datastore is unavailable for office %s".formatted(providerOfficeCode), cause);
  }
}
