package uk.gov.justice.laa.rcw.datastore.client;

import static uk.gov.justice.laa.rcw.datastore.client.DatastoreClientObservability.datastoreOperation;
import static uk.gov.justice.laa.rcw.datastore.client.DatastoreClientObservability.logDatastoreEvent;
import static uk.gov.justice.laa.rcw.datastore.client.DatastoreClientObservability.logOAuthEvent;
import static uk.gov.justice.laa.rcw.datastore.client.DatastoreClientObservability.recordDatastoreRequestDuration;
import static uk.gov.justice.laa.rcw.datastore.client.DatastoreClientObservability.responseStatusCode;
import static uk.gov.justice.laa.rcw.datastore.client.DatastoreClientObservability.safeMethod;
import static uk.gov.justice.laa.rcw.logging.LogAction.DATASTORE_AUTHORIZATION;
import static uk.gov.justice.laa.rcw.logging.LogAction.DATASTORE_TRANSPORT;

import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

record DatastoreOboInterceptor(
    OAuth2AuthorizedClientManager clientManager, String clientRegistrationId)
    implements ClientHttpRequestInterceptor {

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    OAuth2AccessToken accessToken = authorize();
    request.getHeaders().setBearerAuth(accessToken.getTokenValue());
    long startTimeNanos = System.nanoTime();
    String method = safeMethod(request.getMethod().name());
    String operation = datastoreOperation(method, request.getURI().getPath());
    logDatastoreEvent(
        DATASTORE_TRANSPORT,
        "start",
        "in_progress",
        operation,
        method,
        null,
        null,
        null,
        null,
        null,
        null);
    try {
      ClientHttpResponse response = execution.execute(request, body);
      logDatastoreEvent(
          DATASTORE_TRANSPORT,
          "finish",
          "headers_received",
          operation,
          method,
          responseStatusCode(response),
          (System.nanoTime() - startTimeNanos) / 1_000_000.0,
          null,
          null,
          null,
          null);
      return response;
    } catch (IOException | RuntimeException failure) {
      logDatastoreEvent(
          DATASTORE_TRANSPORT,
          "finish",
          "failure",
          operation,
          method,
          null,
          (System.nanoTime() - startTimeNanos) / 1_000_000.0,
          failure,
          null,
          null,
          null);
      throw failure;
    } finally {
      double durationMillis = (System.nanoTime() - startTimeNanos) / 1_000_000.0;
      recordDatastoreRequestDuration(durationMillis, request.getMethod().name());
    }
  }

  private OAuth2AccessToken authorize() {
    long startTimeNanos = System.nanoTime();
    logOAuthEvent(DATASTORE_AUTHORIZATION, "start", "in_progress", startTimeNanos, null, null);
    try {
      Authentication principal = SecurityContextHolder.getContext().getAuthentication();
      OAuth2AuthorizeRequest authorizeRequest =
          OAuth2AuthorizeRequest.withClientRegistrationId(clientRegistrationId)
              .principal(principal)
              .build();

      OAuth2AuthorizedClient authorizedClient = clientManager.authorize(authorizeRequest);
      if (authorizedClient == null) {
        logOAuthEvent(
            DATASTORE_AUTHORIZATION,
            "finish",
            "failure",
            startTimeNanos,
            null,
            "authorized_client_missing");
        return null;
      }

      OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
      logOAuthEvent(DATASTORE_AUTHORIZATION, "finish", "success", startTimeNanos, null, null);
      return accessToken;
    } catch (RuntimeException exception) {
      logOAuthEvent(DATASTORE_AUTHORIZATION, "finish", "failure", startTimeNanos, exception, null);
      throw exception;
    }
  }
}
