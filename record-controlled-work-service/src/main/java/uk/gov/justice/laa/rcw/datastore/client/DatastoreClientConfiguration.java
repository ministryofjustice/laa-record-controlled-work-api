package uk.gov.justice.laa.rcw.datastore.client;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.JwtBearerOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizationContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.endpoint.RestClientJwtBearerTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.laa.ia.datastore.client.api.ApplicationApi;
import uk.gov.justice.laa.ia.datastore.client.config.ApplicationResponseHttpMessageConverter;
import uk.gov.justice.laa.ia.datastore.client.config.DatastoreClientProperties;
import uk.gov.justice.laa.ia.datastore.client.invoker.ApiClient;

/** Configures the datastore client and its On-Behalf-Of token exchange. */
@Configuration
public class DatastoreClientConfiguration {

  /** Manages OBO (jwt-bearer) authorized clients for the datastore registration. */
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
    provider.setJwtAssertionResolver(DatastoreClientConfiguration::resolveJwtAssertion);
    RestClientJwtBearerTokenResponseClient responseClient =
        new RestClientJwtBearerTokenResponseClient();
    responseClient.setParametersCustomizer(
        params -> params.add("requested_token_use", "on_behalf_of"));
    provider.setAccessTokenResponseClient(new ObservedJwtBearerTokenResponseClient(responseClient));
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
      OAuth2AuthorizedClientManager datastoreAuthorizedClientManager,
      ObservationRegistry observationRegistry,
      MeterRegistry meterRegistry) {
    meterRegistry.config().meterFilter(new DatastoreHttpClientMeterFilter());
    PoolingHttpClientConnectionManager connectionManager =
        PoolingHttpClientConnectionManagerBuilder.create().build();
    CloseableHttpClient httpClient =
        HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setRetryStrategy(datastoreRetryStrategy())
            .build();
    RestTemplate restTemplate =
        new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
    restTemplate.getMessageConverters().add(0, new ApplicationResponseHttpMessageConverter());
    restTemplate.getMessageConverters().add(0, new EditApplicationCommandHttpMessageConverter());
    restTemplate.setObservationRegistry(observationRegistry);
    restTemplate.setObservationConvention(new DatastoreClientObservationConvention());
    observationRegistry
        .observationConfig()
        .observationHandler(new DatastoreOperationObservationHandler(connectionManager));
    restTemplate
        .getInterceptors()
        .add(
            new DatastoreOboInterceptor(
                datastoreAuthorizedClientManager, props.clientRegistrationId()));

    ApiClient apiClient = new ApiClient(restTemplate).setBasePath(props.baseUrl());
    return new ApplicationApi(apiClient);
  }

  private HttpRequestRetryStrategy datastoreRetryStrategy() {
    HttpRequestRetryStrategy defaultStrategy = DefaultHttpRequestRetryStrategy.INSTANCE;
    return new HttpRequestRetryStrategy() {
      @Override
      public boolean retryRequest(
          HttpRequest request, IOException exception, int execCount, HttpContext context) {
        return isReadRequest(request)
            && defaultStrategy.retryRequest(request, exception, execCount, context);
      }

      @Override
      public boolean retryRequest(HttpResponse response, int execCount, HttpContext context) {
        HttpRequest request = HttpClientContext.cast(context).getRequest();
        return isReadRequest(request) && defaultStrategy.retryRequest(response, execCount, context);
      }

      @Override
      public TimeValue getRetryInterval(HttpResponse response, int execCount, HttpContext context) {
        return defaultStrategy.getRetryInterval(response, execCount, context);
      }
    };
  }

  private boolean isReadRequest(HttpRequest request) {
    return request != null && "GET".equalsIgnoreCase(request.getMethod());
  }
}
