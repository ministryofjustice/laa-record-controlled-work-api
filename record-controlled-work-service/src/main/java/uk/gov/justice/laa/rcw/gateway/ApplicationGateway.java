package uk.gov.justice.laa.rcw.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import uk.gov.justice.laa.ia.datastore.client.api.ApplicationApi;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.rcw.exception.ApplicationBadRequestException;
import uk.gov.justice.laa.rcw.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.rcw.exception.ApplicationUnavailableException;
import uk.gov.justice.laa.rcw.exception.ApplicationUpstreamErrorException;

/** Gateway for datastore application fetch operations. */
@Service
@RequiredArgsConstructor
public class ApplicationGateway {

  private final ApplicationApi applicationApi;
  private final BearerTokenProvider bearerTokenProvider;

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
}
