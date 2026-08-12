package uk.gov.justice.laa.rcw.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.rcw.exception.ApplicationForbiddenException;

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
}
