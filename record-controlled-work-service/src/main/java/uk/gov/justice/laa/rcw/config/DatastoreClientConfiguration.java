package uk.gov.justice.laa.rcw.config;

import static uk.gov.justice.laa.rcw.logging.LogAction.DATASTORE_AUTHORIZATION;
import static uk.gov.justice.laa.rcw.logging.LogAction.DATASTORE_TOKEN_EXCHANGE;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.sentry.Sentry;
import io.sentry.metrics.MetricsUnit;
import io.sentry.metrics.SentryMetricsParameters;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.JwtBearerOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizationContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.endpoint.JwtBearerGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.RestClientJwtBearerTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.laa.ia.datastore.client.api.ApplicationApi;
import uk.gov.justice.laa.ia.datastore.client.config.ApplicationResponseHttpMessageConverter;
import uk.gov.justice.laa.ia.datastore.client.config.DatastoreClientProperties;
import uk.gov.justice.laa.ia.datastore.client.invoker.ApiClient;
import uk.gov.justice.laa.ia.datastore.client.model.EditApplicationCommand;
import uk.gov.justice.laa.rcw.logging.SafeFailureDiagnostics;
import uk.gov.justice.laa.rcw.logging.StructuredLogger;

/**
 * Configures the datastore {@link ApplicationApi} client to use a true On-Behalf-Of (jwt-bearer)
 * token exchange for the {@code Authorization} header, while forwarding the original incoming
 * middleware token unchanged via {@code X-Authorization}.
 *
 * <p>This overrides the {@code info-and-advice-datastore-client} library's default {@code
 * applicationApi} bean, which uses a client-credentials grant instead.
 */
@Configuration
@SuppressWarnings({"deprecation", "removal"})
public class DatastoreClientConfiguration {

  private static final StructuredLogger log =
      StructuredLogger.of(DatastoreClientConfiguration.class);

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

  private static final class ObservedJwtBearerTokenResponseClient
      implements OAuth2AccessTokenResponseClient<JwtBearerGrantRequest> {

    private final OAuth2AccessTokenResponseClient<JwtBearerGrantRequest> delegate;

    private ObservedJwtBearerTokenResponseClient(
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
        logOAuthEvent(
            DATASTORE_TOKEN_EXCHANGE, "finish", "failure", startTimeNanos, exception, null);
        throw exception;
      }
    }
  }

  /** Overrides the library's default client-credentials {@link ApplicationApi} bean with OBO. */
  @Bean
  @ConditionalOnMissingBean
  public ApplicationApi applicationApi(
      DatastoreClientProperties props,
      OAuth2AuthorizedClientManager datastoreAuthorizedClientManager) {
    CloseableHttpClient httpClient =
        HttpClients.custom().setRetryStrategy(datastoreRetryStrategy()).build();
    RestTemplate restTemplate =
        new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
    restTemplate.getMessageConverters().add(0, new ApplicationResponseHttpMessageConverter());
    restTemplate.getMessageConverters().add(0, new EditApplicationCommandHttpMessageConverter());
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
          org.apache.hc.core5.http.HttpRequest request,
          IOException exception,
          int execCount,
          HttpContext context) {
        return !isDetailsEdit(request)
            && defaultStrategy.retryRequest(request, exception, execCount, context);
      }

      @Override
      public boolean retryRequest(HttpResponse response, int execCount, HttpContext context) {
        org.apache.hc.core5.http.HttpRequest request = HttpClientContext.cast(context).getRequest();
        return !isDetailsEdit(request)
            && defaultStrategy.retryRequest(response, execCount, context);
      }

      @Override
      public TimeValue getRetryInterval(HttpResponse response, int execCount, HttpContext context) {
        return defaultStrategy.getRetryInterval(response, execCount, context);
      }
    };
  }

  private boolean isDetailsEdit(org.apache.hc.core5.http.HttpRequest request) {
    return request != null
        && "PATCH".equalsIgnoreCase(request.getMethod())
        && request.getRequestUri().endsWith(":edit-application");
  }

  @SuppressWarnings({"deprecation", "removal"})
  private static final class EditApplicationCommandHttpMessageConverter
      extends MappingJackson2HttpMessageConverter {

    EditApplicationCommandHttpMessageConverter() {
      super(editApplicationCommandObjectMapper());
    }

    @Override
    protected boolean supports(Class<?> clazz) {
      return EditApplicationCommand.class.isAssignableFrom(clazz);
    }

    private boolean supports(Type type) {
      Class<?> clazz = ResolvableType.forType(type).resolve();
      return clazz != null && supports(clazz);
    }

    @Override
    public boolean canRead(Class<?> clazz, MediaType mediaType) {
      return supports(clazz) && super.canRead(clazz, mediaType);
    }

    @Override
    public boolean canRead(Type type, Class<?> contextClass, MediaType mediaType) {
      return supports(type) && super.canRead(type, contextClass, mediaType);
    }

    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
      return supports(clazz) && super.canWrite(clazz, mediaType);
    }

    @Override
    public boolean canWrite(Type type, Class<?> clazz, MediaType mediaType) {
      return supports(clazz) && super.canWrite(type, clazz, mediaType);
    }
  }

  private static ObjectMapper editApplicationCommandObjectMapper() {
    ObjectMapper objectMapper =
        JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .addModule(new JsonNullableModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    return objectMapper;
  }

  /**
   * Attaches the OBO-exchanged downstream token as {@code Authorization}. The original incoming
   * token is forwarded separately as an explicit {@code X-Authorization} parameter on each {@link
   * ApplicationApi} call, since the datastore API models it as a required request parameter.
   */
  record DatastoreOboInterceptor(
      OAuth2AuthorizedClientManager clientManager, String clientRegistrationId)
      implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
        HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
      OAuth2AccessToken accessToken = authorize();
      request.getHeaders().setBearerAuth(accessToken.getTokenValue());
      long startTimeNanos = System.nanoTime();
      try {
        return execution.execute(request, body);
      } finally {
        double durationMillis = (System.nanoTime() - startTimeNanos) / 1_000_000.0;
        Sentry.metrics()
            .distribution(
                "datastore_api_request_duration",
                durationMillis,
                MetricsUnit.Duration.MILLISECOND,
                SentryMetricsParameters.create(Map.of("http.method", request.getMethod().name())));
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
        logOAuthEvent(
            DATASTORE_AUTHORIZATION, "finish", "failure", startTimeNanos, exception, null);
        throw exception;
      }
    }
  }

  private static void logOAuthEvent(
      String action,
      String phase,
      String outcome,
      long startTimeNanos,
      Throwable failure,
      String failureCategory) {
    StructuredLogger.BuildStage event;
    if ("failure".equals(outcome)) {
      event = log.warn().action(action).outcome(outcome);
    } else {
      event = log.info().action(action).outcome(outcome);
    }
    event = event.with("event.phase", phase);

    if ("finish".equals(phase)) {
      event = event.with("duration_ms", (System.nanoTime() - startTimeNanos) / 1_000_000.0);
    }
    if (failure != null) {
      event =
          event
              .with("failure.category", SafeFailureDiagnostics.category(failure))
              .with("failure.cause_classes", SafeFailureDiagnostics.causeClasses(failure));
    } else if (failureCategory != null) {
      event =
          event
              .with("failure.category", failureCategory)
              .with("failure.cause_classes", List.of("OAuth2AuthorizedClientMissing"));
    }

    event.log("Datastore {} {}", action, phase);
  }
}
