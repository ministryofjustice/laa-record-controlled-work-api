package uk.gov.justice.laa.rcw.pact;

import static au.com.dius.pact.consumer.dsl.LambdaDsl.newJsonBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactBuilder;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.laa.ia.datastore.client.api.ApplicationApi;
import uk.gov.justice.laa.ia.datastore.client.invoker.ApiClient;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;

/**
 * Consumer contract for {@code GET /api/v0/applications/{id}}, exercised via the real generated
 * {@link ApplicationApi} client pointed directly at the Pact {@link MockServer}.
 *
 * <p>This deliberately bypasses this app's Spring context and OBO (on-behalf-of) token exchange
 * ({@code uk.gov.justice.laa.rcw.config.DatastoreClientConfiguration}) - that wiring is this app's
 * own internal concern, not part of the HTTP wire contract with the datastore being verified here.
 * The two states below must exactly match {@code @State(...)} handlers already defined in {@code
 * laa-info-and-advice-datastore}'s {@code AbstractProviderPactTests}.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = AbstractPactTest.PROVIDER)
class ApplicationPactTest extends AbstractPactTest {

  private static final UUID APPLICATION_ID =
      UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final String CORRELATION_ID = "22222222-2222-2222-2222-222222222222";
  private static final String SERVICE_NAME = CONSUMER;

  // @Pact methods must return V4Pact (pact-jvm 4.7's default/required spec version) - the fluent
  // request/response DSL (PactDslWithProvider, via PactBuilder#usingLegacyDsl()) only builds a V3
  // RequestResponsePact, so it's converted with asV4Pact() before returning.
  @Pact(consumer = CONSUMER)
  V4Pact anApplicationExists(PactBuilder builder) {
    RequestResponsePact pact =
        builder
            .usingLegacyDsl()
            .given("an application exists")
            .uponReceiving("a request for an application that exists")
            .path("/api/v0/applications/" + APPLICATION_ID)
            .method("GET")
            .matchHeader("X-Authorization", "Bearer .+", "Bearer sample-token")
            .matchHeader("X-Correlation-ID", UUID_REGEX, CORRELATION_ID)
            .headers(Map.of("X-Service-Name", SERVICE_NAME))
            .willRespondWith()
            .status(200)
            .body(
                newJsonBody(
                        root -> {
                          root.stringMatcher("id", UUID_REGEX, APPLICATION_ID.toString());
                          root.stringType("providerFirmCode", "1A001L");
                          root.stringType("providerOfficeCode", "1A001L001");
                          root.stringMatcher("applicationState", "DRAFT|COMPLETED", "DRAFT");
                        })
                    .build())
            .toPact();
    return (V4Pact) pact.asV4Pact().unwrap();
  }

  @Pact(consumer = CONSUMER)
  V4Pact anApplicationDoesNotExist(PactBuilder builder) {
    UUID missingId = UUID.fromString("99999999-9999-9999-9999-999999999999");
    RequestResponsePact pact =
        builder
            .usingLegacyDsl()
            .given("an application does not exist")
            .uponReceiving("a request for an application that does not exist")
            .path("/api/v0/applications/" + missingId)
            .method("GET")
            .matchHeader("X-Authorization", "Bearer .+", "Bearer sample-token")
            .matchHeader("X-Correlation-ID", UUID_REGEX, CORRELATION_ID)
            .headers(Map.of("X-Service-Name", SERVICE_NAME))
            .willRespondWith()
            .status(404)
            .toPact();
    return (V4Pact) pact.asV4Pact().unwrap();
  }

  @Test
  @PactTestFor(pactMethod = "anApplicationExists")
  void getApplicationReturnsApplicationWhenItExists(MockServer mockServer) {
    ApplicationApi client = clientFor(mockServer);

    ApplicationResponse response =
        client.getApplication(APPLICATION_ID, "Bearer sample-token", CORRELATION_ID, SERVICE_NAME);

    assertThat(response.getId()).isEqualTo(APPLICATION_ID);
    assertThat(response.getProviderFirmCode()).isNotBlank();
    assertThat(response.getProviderOfficeCode()).isNotBlank();
    assertThat(response.getApplicationState()).isNotNull();
  }

  @Test
  @PactTestFor(pactMethod = "anApplicationDoesNotExist")
  void getApplicationThrowsNotFoundWhenApplicationDoesNotExist(MockServer mockServer) {
    ApplicationApi client = clientFor(mockServer);
    UUID missingId = UUID.fromString("99999999-9999-9999-9999-999999999999");

    assertThatThrownBy(
            () ->
                client.getApplication(
                    missingId, "Bearer sample-token", CORRELATION_ID, SERVICE_NAME))
        .isInstanceOf(HttpClientErrorException.NotFound.class);
  }

  private ApplicationApi clientFor(MockServer mockServer) {
    ApiClient apiClient = new ApiClient(new RestTemplate()).setBasePath(mockServer.getUrl());
    return new ApplicationApi(apiClient);
  }
}
