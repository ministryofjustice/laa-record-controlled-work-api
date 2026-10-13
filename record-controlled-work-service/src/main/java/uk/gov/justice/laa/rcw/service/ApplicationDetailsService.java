package uk.gov.justice.laa.rcw.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationState;
import uk.gov.justice.laa.ia.datastore.client.model.EditApplicationCommand;
import uk.gov.justice.laa.rcw.exception.ApplicationConflictException;
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
    ApplicationResponse application = applicationGateway.fetchApplicationDetails(applicationId);
    applicationGuard.checkVisibleForOffice(applicationId, application.getProviderOfficeCode());

    if (application.getApplicationState() == ApplicationState.COMPLETED) {
      throw new ApplicationConflictException(
          "Application %s has already been completed".formatted(applicationId),
          "APPLICATION_COMPLETED");
    }

    EditApplicationCommand command = applicationMapper.toEditApplicationCommand(request, version);
    return applicationGateway.editApplication(applicationId, command);
  }
}
