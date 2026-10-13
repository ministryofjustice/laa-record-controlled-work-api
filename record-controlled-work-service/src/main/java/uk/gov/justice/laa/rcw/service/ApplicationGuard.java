package uk.gov.justice.laa.rcw.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.rcw.exception.ApplicationForbiddenException;
import uk.gov.justice.laa.rcw.exception.ApplicationNotFoundException;

/** Validates authorization for application update operations. */
@Component
@RequiredArgsConstructor
public class ApplicationGuard {

  private final AuthorizedOfficesProvider authorizedOfficesProvider;

  /** Throws forbidden when the provider office is not authorized for the current user. */
  public void checkAuthorizedForOffice(UUID applicationId, String providerOfficeCode) {
    if (!authorizedOfficesProvider.currentAuthorizedOfficeCodes().contains(providerOfficeCode)) {
      throw new ApplicationForbiddenException(
          "Not authorized to update application %s".formatted(applicationId));
    }
  }

  /** Throws not found when the provider office is not visible to the current user. */
  public void checkVisibleForOffice(UUID applicationId, String providerOfficeCode) {
    try {
      checkAuthorizedForOffice(applicationId, providerOfficeCode);
    } catch (ApplicationForbiddenException ignored) {
      throw new ApplicationNotFoundException(
          "No application found with id: %s".formatted(applicationId));
    }
  }
}
