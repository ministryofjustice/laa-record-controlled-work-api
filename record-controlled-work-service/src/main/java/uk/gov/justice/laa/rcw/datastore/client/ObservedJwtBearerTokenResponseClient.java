package uk.gov.justice.laa.rcw.datastore.client;

import static uk.gov.justice.laa.rcw.datastore.client.DatastoreClientObservability.logOAuthEvent;
import static uk.gov.justice.laa.rcw.logging.LogAction.DATASTORE_TOKEN_EXCHANGE;

import org.springframework.security.oauth2.client.endpoint.JwtBearerGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

final class ObservedJwtBearerTokenResponseClient
    implements OAuth2AccessTokenResponseClient<JwtBearerGrantRequest> {

  private final OAuth2AccessTokenResponseClient<JwtBearerGrantRequest> delegate;

  ObservedJwtBearerTokenResponseClient(
      OAuth2AccessTokenResponseClient<JwtBearerGrantRequest> delegate) {
    this.delegate = delegate;
  }

  @Override
  public OAuth2AccessTokenResponse getTokenResponse(JwtBearerGrantRequest request) {
    long startTimeNanos = System.nanoTime();
    logOAuthEvent(DATASTORE_TOKEN_EXCHANGE, "start", "in_progress", startTimeNanos, null, null);
    try {
      OAuth2AccessTokenResponse response = delegate.getTokenResponse(request);
      logOAuthEvent(DATASTORE_TOKEN_EXCHANGE, "finish", "success", startTimeNanos, null, null);
      return response;
    } catch (RuntimeException exception) {
      logOAuthEvent(DATASTORE_TOKEN_EXCHANGE, "finish", "failure", startTimeNanos, exception, null);
      throw exception;
    }
  }
}
