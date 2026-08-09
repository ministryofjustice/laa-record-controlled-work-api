package uk.gov.justice.laa.rcw.service;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.stereotype.Component;

/** Provides the office codes the current authenticated user is authorized for. */
@Component
public class AuthorizedOfficesProvider {

  private static final String LAA_ACCOUNTS_CLAIM = "LAA_ACCOUNTS";

  /** Returns the office codes from the {@code LAA_ACCOUNTS} claim, or an empty list if none. */
  public List<String> currentAuthorizedOfficeCodes() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof AbstractOAuth2TokenAuthenticationToken<?> tokenAuth
        && tokenAuth.getToken() instanceof Jwt jwt) {
      List<String> officeCodes = jwt.getClaimAsStringList(LAA_ACCOUNTS_CLAIM);
      return officeCodes != null ? officeCodes : List.of();
    }
    return List.of();
  }
}
