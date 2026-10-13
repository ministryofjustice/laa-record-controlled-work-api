package uk.gov.justice.laa.rcw.utils;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.test.context.DynamicPropertyRegistry;

/** Shared setup for integration tests using isolated datastore WireMock servers. */
public final class DatastoreTestSupport {

  private static final String TOKEN_PATH = "/default/token";

  private DatastoreTestSupport() {}

  /** Registers datastore URLs for the supplied server. */
  public static void registerProperties(
      DynamicPropertyRegistry registry, WireMockServer datastore) {
    registry.add("laa.datastore.client.base-url", datastore::baseUrl);
    registry.add(
        "spring.security.oauth2.client.provider.datastore.token-uri",
        () -> datastore.baseUrl() + TOKEN_PATH);
  }

  /** Stubs the OAuth token endpoint for the supplied server. */
  public static void stubTokenEndpoint(WireMockServer datastore) {
    datastore.stubFor(
        post(urlPathEqualTo(TOKEN_PATH))
            .willReturn(
                okJson(
                    """
                    {
                      "access_token": "obo-access-token",
                      "token_type": "Bearer",
                      "expires_in": 3600,
                      "scope": "DataStore.Access"
                    }
                    """)));
  }

  /** Resets the supplied server and restores its OAuth token stub. */
  public static void resetMappingsAndStubTokenEndpoint(WireMockServer datastore) {
    datastore.resetAll();
    stubTokenEndpoint(datastore);
  }
}
