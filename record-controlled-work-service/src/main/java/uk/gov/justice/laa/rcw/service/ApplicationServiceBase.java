package uk.gov.justice.laa.rcw.service;

import java.util.UUID;
import uk.gov.justice.laa.rcw.exception.ApplicationBadRequestException;
import uk.gov.justice.laa.rcw.exception.ApplicationConflictException;
import uk.gov.justice.laa.rcw.exception.ApplicationForbiddenException;
import uk.gov.justice.laa.rcw.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.rcw.exception.ApplicationUnavailableException;
import uk.gov.justice.laa.rcw.exception.ApplicationUpstreamErrorException;

/** Shared pure exception factories for application service operations. */
abstract class ApplicationServiceBase {

  protected static void checkAuthorizedForOffice(
      UUID applicationId,
      String providerOfficeCode,
      AuthorizedOfficesProvider authorizedOfficesProvider) {
    if (!authorizedOfficesProvider.currentAuthorizedOfficeCodes().contains(providerOfficeCode)) {
      throw forbiddenError(applicationId);
    }
  }

  protected static ApplicationNotFoundException applicationNotFoundError(UUID applicationId) {
    return new ApplicationNotFoundException(
        "No application found with id: %s".formatted(applicationId));
  }

  protected static ApplicationBadRequestException badRequestError(UUID applicationId) {
    return new ApplicationBadRequestException(
        "Datastore rejected the request for application %s".formatted(applicationId));
  }

  protected static ApplicationUpstreamErrorException upstreamError(UUID applicationId) {
    return new ApplicationUpstreamErrorException(
        "Datastore returned an error for application %s".formatted(applicationId));
  }

  protected static ApplicationUnavailableException unavailableError(UUID applicationId) {
    return new ApplicationUnavailableException(
        "Datastore is unavailable for application %s".formatted(applicationId));
  }

  protected static ApplicationConflictException applicationConflictError(UUID applicationId) {
    return new ApplicationConflictException(
        "Application %s was modified concurrently".formatted(applicationId));
  }

  protected static ApplicationConflictException applicationAlreadyRecordedError(
      UUID applicationId) {
    return new ApplicationConflictException(
        "Application %s has already been recorded and cannot be updated".formatted(applicationId));
  }

  protected static ApplicationForbiddenException forbiddenError(UUID applicationId) {
    return new ApplicationForbiddenException(
        "Not authorized to update application %s".formatted(applicationId));
  }
}
