package uk.gov.justice.laa.rcw.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationState;
import uk.gov.justice.laa.ia.datastore.client.model.EditApplicationCommand;
import uk.gov.justice.laa.rcw.exception.ApplicationConflictException;
import uk.gov.justice.laa.rcw.exception.ApplicationForbiddenException;
import uk.gov.justice.laa.rcw.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.rcw.exception.ApplicationUpstreamErrorException;
import uk.gov.justice.laa.rcw.gateway.ApplicationGateway;
import uk.gov.justice.laa.rcw.mapper.ApplicationMapper;
import uk.gov.justice.laa.rcw.model.UpdateApplicationDetailsRequestBody;

/** Service for atomically updating an application's editable details. */
@Service
@RequiredArgsConstructor
public class ApplicationDetailsService {

  private final ApplicationMapper applicationMapper;
  private final ApplicationGateway applicationGateway;
  private final ApplicationGuard applicationGuard;

  /**
   * Authorizes and updates a complete details snapshot with the caller's version.
   *
   * @param applicationId the application id
   * @param request the complete editable details snapshot
   * @param version the caller's version precondition
   * @return the new datastore entity tag
   */
  public String updateApplicationDetails(
      UUID applicationId, UpdateApplicationDetailsRequestBody request, long version) {
    ApplicationResponse application = applicationGateway.fetchApplication(applicationId);
    try {
      applicationGuard.checkAuthorizedForOffice(applicationId, application.getProviderOfficeCode());
    } catch (ApplicationForbiddenException exception) {
      throw notFound(applicationId);
    }

    if (application.getApplicationState() == ApplicationState.COMPLETED) {
      throw new ApplicationConflictException(
          "Application %s has already been completed".formatted(applicationId),
          "APPLICATION_COMPLETED");
    }

    EditApplicationCommand command = applicationMapper.toEditApplicationCommand(request, version);
    ResponseEntity<Void> response = applicationGateway.editApplication(applicationId, command);
    return requireValidEtag(response);
  }

  private String requireValidEtag(ResponseEntity<Void> response) {
    if (response != null) {
      String etag = response.getHeaders().getETag();
      if (etag != null && etag.matches("\\\"[0-9]+\\\"")) {
        try {
          Long.parseLong(etag.substring(1, etag.length() - 1));
        } catch (NumberFormatException exception) {
          throw invalidVersion();
        }
        return etag;
      }
    }
    throw invalidVersion();
  }

  private ApplicationNotFoundException notFound(UUID applicationId) {
    return new ApplicationNotFoundException(
        "No application found with id: %s".formatted(applicationId));
  }

  private ApplicationUpstreamErrorException invalidVersion() {
    return new ApplicationUpstreamErrorException(
        "Datastore returned an invalid application version",
        "DATASTORE_INVALID_APPLICATION_VERSION");
  }
}
