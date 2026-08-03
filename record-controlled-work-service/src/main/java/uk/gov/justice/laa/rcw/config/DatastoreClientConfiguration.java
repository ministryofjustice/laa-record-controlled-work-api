package uk.gov.justice.laa.rcw.config;

import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.JwtBearerOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizationContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.endpoint.RestClientJwtBearerTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.laa.ia.datastore.client.api.ApplicationApi;
import uk.gov.justice.laa.ia.datastore.client.config.DatastoreClientProperties;
import uk.gov.justice.laa.ia.datastore.client.invoker.ApiClient;

/**
 * Configures the datastore {@link ApplicationApi} client to use a true On-Behalf-Of (jwt-bearer)
 * token exchange for the {@code Authorization} header, while forwarding the original incoming
 * middleware token unchanged via {@code X-Authorization}.
 *
 * <p>This overrides the {@code info-and-advice-datastore-client} library's default {@code
 * applicationApi} bean, which uses a client-credentials grant instead.
 */
@Configuration
public class DatastoreClientConfiguration {

  /** Manages OBO (jwt-bearer) authorized clients for the {@code datastore} registration. */
  @Bean
  public OAuth2AuthorizedClientManager datastoreAuthorizedClientManager(
      ClientRegistrationRepository clientRegistrationRepository) {
    OAuth2AuthorizedClientProvider jwtBearerProvider = jwtBearerProvider();
    OAuth2AuthorizedClientProvider authorizedClientProvider =
        OAuth2AuthorizedClientProviderBuilder.builder().provider(jwtBearerProvider).build();

    AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(
            clientRegistrationRepository,
            new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository));
    manager.setAuthorizedClientProvider(authorizedClientProvider);
    return manager;
  }

  private JwtBearerOAuth2AuthorizedClientProvider jwtBearerProvider() {
    JwtBearerOAuth2AuthorizedClientProvider provider =
        new JwtBearerOAuth2AuthorizedClientProvider();
    // the incoming token is not guaranteed to be a JwtAuthenticationToken, so resolve it manually
    provider.setJwtAssertionResolver(DatastoreClientConfiguration::resolveJwtAssertion);
    // Entra ID's OBO endpoint requires this non-standard parameter (AADSTS900144 otherwise)
    RestClientJwtBearerTokenResponseClient responseClient =
        new RestClientJwtBearerTokenResponseClient();
    responseClient.setParametersCustomizer(
        params -> params.add("requested_token_use", "on_behalf_of"));
    provider.setAccessTokenResponseClient(responseClient);
    return provider;
  }

  private static Jwt resolveJwtAssertion(OAuth2AuthorizationContext context) {
    if (context.getPrincipal() instanceof AbstractOAuth2TokenAuthenticationToken<?> tokenAuth
        && tokenAuth.getToken() instanceof Jwt jwt) {
      return jwt;
    }
    throw new IllegalStateException(
        "No JWT available on the current authentication to use as an OBO assertion");
  }

  /** Overrides the library's default client-credentials {@link ApplicationApi} bean with OBO. */
  @Bean
  @ConditionalOnMissingBean
  public ApplicationApi applicationApi(
      DatastoreClientProperties props,
      OAuth2AuthorizedClientManager datastoreAuthorizedClientManager) {
    RestTemplate restTemplate = new RestTemplate();
    restTemplate
        .getInterceptors()
        .add(
            new DatastoreOboInterceptor(
                datastoreAuthorizedClientManager, props.clientRegistrationId()));

    ApiClient apiClient = new ApiClient(restTemplate).setBasePath(props.baseUrl());
    return new ApplicationApi(apiClient);
  }

  /**
   * Attaches the OBO-exchanged downstream token as {@code Authorization}. The original incoming
   * token is forwarded separately as an explicit {@code X-Authorization} parameter on each {@link
   * ApplicationApi} call, since the datastore API models it as a required request parameter.
   */
  private record DatastoreOboInterceptor(
      OAuth2AuthorizedClientManager clientManager, String clientRegistrationId)
      implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
        HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
      Authentication principal = SecurityContextHolder.getContext().getAuthentication();
      OAuth2AuthorizeRequest authorizeRequest =
          OAuth2AuthorizeRequest.withClientRegistrationId(clientRegistrationId)
              .principal(principal)
              .build();
      OAuth2AccessToken accessToken = clientManager.authorize(authorizeRequest).getAccessToken();
      request.getHeaders().setBearerAuth(accessToken.getTokenValue());
      return execution.execute(request, body);
    }
  }
}
