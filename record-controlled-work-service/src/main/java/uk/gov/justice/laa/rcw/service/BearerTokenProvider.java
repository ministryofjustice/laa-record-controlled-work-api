package uk.gov.justice.laa.rcw.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.stereotype.Component;

/** Provides the bearer token to forward to downstream services. */
@Component
public class BearerTokenProvider {

  /** Forwards the original incoming middleware token unchanged, as required by the datastore. */
  public String currentBearerToken() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof AbstractOAuth2TokenAuthenticationToken<?> tokenAuth) {
      return "Bearer " + tokenAuth.getToken().getTokenValue();
    }
    throw new IllegalStateException("No authenticated token available to forward to the datastore");
  }
}
