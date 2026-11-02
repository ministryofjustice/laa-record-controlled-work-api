package uk.gov.justice.laa.rcw.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import uk.gov.justice.laa.rcw.SpringBootMicroserviceApplication;
import uk.gov.justice.laa.rcw.utils.BaseIntegrationTest;
import uk.gov.justice.laa.rcw.utils.DatastoreTestSupport;
import uk.gov.justice.laa.rcw.utils.TestJwtConfig;

@SpringBootTest(
    classes = SpringBootMicroserviceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CreateApplicationRequestBodyIntegrationTest extends BaseIntegrationTest {

  private static final int MAX_BODY_BYTES = 65_536;
  private static final WireMockServer DATASTORE =
      new WireMockServer(WireMockConfiguration.options().dynamicPort());
  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

  static {
    DATASTORE.start();
  }

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void datastoreProperties(DynamicPropertyRegistry registry) {
    DatastoreTestSupport.registerProperties(registry, DATASTORE);
  }

  @BeforeAll
  static void stubTokenEndpoint() {
    DatastoreTestSupport.stubTokenEndpoint(DATASTORE);
  }

  @AfterAll
  static void stopWireMock() {
    DATASTORE.stop();
  }

  @AfterEach
  void resetDatastoreRequests() {
    DatastoreTestSupport.resetMappingsAndStubTokenEndpoint(DATASTORE);
  }

  @Test
  void shouldRejectOversizedChunkedCreateRequestWithoutDatastoreAccess() throws Exception {
    String requestBody =
        "{\"private\":\"PrivateValueForOversizedBody\",\"data\":\""
            + "x".repeat(MAX_BODY_BYTES)
            + "\"";

    HttpResponse<String> response = sendChunked(requestBody);

    assertThat(response.statusCode()).isEqualTo(413);
    assertThat(response.body())
        .contains("REQUEST_BODY_TOO_LARGE")
        .contains("Request body exceeds the configured size limit.")
        .doesNotContain("PrivateValueForOversizedBody");
    DATASTORE.verify(0, anyRequestedFor(urlMatching("/api/v0/.*")));
  }

  private HttpResponse<String> sendChunked(String body) throws Exception {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    HttpRequest.BodyPublisher publisher =
        HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes));
    assertThat(publisher.contentLength()).isEqualTo(-1);
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/applications"))
            .header("Authorization", "Bearer " + TestJwtConfig.ACCESS_TOKEN)
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .POST(publisher)
            .build();
    return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
