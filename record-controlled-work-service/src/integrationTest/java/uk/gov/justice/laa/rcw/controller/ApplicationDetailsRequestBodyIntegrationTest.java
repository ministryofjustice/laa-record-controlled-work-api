package uk.gov.justice.laa.rcw.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import uk.gov.justice.laa.rcw.SpringBootMicroserviceApplication;
import uk.gov.justice.laa.rcw.exception.ApplicationRequestTooLargeException;
import uk.gov.justice.laa.rcw.model.UpdateApplicationDetailsRequestBody;
import uk.gov.justice.laa.rcw.utils.BaseIntegrationTest;
import uk.gov.justice.laa.rcw.utils.TestJwtConfig;

@SpringBootTest(
    classes = SpringBootMicroserviceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationDetailsRequestBodyIntegrationTest extends BaseIntegrationTest {

  private static final int MAX_BODY_BYTES = 65_536;
  private static final UUID APPLICATION_ID =
      UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
  private static final String VALID_BODY =
      """
      {
        "priorLegalAid": "no",
        "legalAidLast6Months": false,
        "reasonForReapplication": null,
        "ecfFlag": false,
        "clientDetails": {
          "firstName": "PrivateValueForOversizedBody",
          "lastName": "Client",
          "dateOfBirth": "1990-01-01",
          "niNumber": null,
          "hasFixedAddress": false,
          "address": null
        }
      }
      """;
  private static final String DETAILS_PATH = "/api/v1/applications/" + APPLICATION_ID + "/details";
  private static final WireMockServer DATASTORE =
      new WireMockServer(WireMockConfiguration.options().dynamicPort());
  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

  static {
    DATASTORE.start();
  }

  @LocalServerPort private int port;
  @Autowired private ApplicationDetailsRequestBodyValidator requestBodyValidator;

  @DynamicPropertySource
  static void datastoreProperties(DynamicPropertyRegistry registry) {
    registry.add("laa.datastore.client.base-url", DATASTORE::baseUrl);
    registry.add(
        "spring.security.oauth2.client.provider.datastore.token-uri",
        () -> DATASTORE.baseUrl() + "/default/token");
  }

  @BeforeAll
  static void stubTokenEndpoint() {
    DATASTORE.stubFor(
        WireMock.post(urlPathEqualTo("/default/token"))
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

  @AfterAll
  static void stopWireMock() {
    DATASTORE.stop();
  }

  @AfterEach
  void resetDatastoreRequests() {
    DATASTORE.resetRequests();
  }

  @Test
  void shouldAcceptExactLimitOverChunkedHttp() throws Exception {
    stubSuccessfulEdit();

    HttpResponse<String> response = sendChunked(padToLimit(VALID_BODY));

    assertThat(response.statusCode()).isEqualTo(204);
    DATASTORE.verify(1, getRequestedFor(urlPathEqualTo("/api/v0/applications/" + APPLICATION_ID)));
    DATASTORE.verify(
        1,
        patchRequestedFor(
            urlPathEqualTo("/api/v0/applications/" + APPLICATION_ID + ":edit-application")));
  }

  @Test
  void shouldRejectLimitPlusOneOverChunkedHttpWithoutDatastoreRequests() throws Exception {
    String requestBody = padToLimit(VALID_BODY) + " ";

    HttpResponse<String> response = sendChunked(requestBody);

    assertThat(response.statusCode()).isEqualTo(413);
    assertThat(response.body())
        .contains("REQUEST_BODY_TOO_LARGE")
        .contains("Request body exceeds the configured size limit.")
        .doesNotContain("PrivateValueForOversizedBody");
    DATASTORE.verify(0, anyRequestedFor(urlMatching("/api/v0/.*")));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(longs = {1L, 131_072L})
  @SuppressWarnings("removal")
  void shouldBoundReadsWhenContentLengthIsAbsentOrMisleading(Long contentLength) throws Exception {
    GuardedInputStream body = new GuardedInputStream(MAX_BODY_BYTES + 1);
    HttpInputMessage inputMessage = inputMessage(body, contentLength);
    MethodParameter parameter = detailsBodyParameter();

    assertThatThrownBy(
            () ->
                requestBodyValidator.beforeBodyRead(
                    inputMessage,
                    parameter,
                    parameter.getGenericParameterType(),
                    MappingJackson2HttpMessageConverter.class))
        .isInstanceOf(ApplicationRequestTooLargeException.class);

    assertThat(body.bytesRead()).isEqualTo(MAX_BODY_BYTES + 1);
  }

  private HttpResponse<String> sendChunked(String body) throws Exception {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    HttpRequest.BodyPublisher publisher =
        HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes));
    assertThat(publisher.contentLength()).isEqualTo(-1);
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + DETAILS_PATH))
            .header("Authorization", "Bearer " + TestJwtConfig.ACCESS_TOKEN)
            .header("If-Match", "\"31\"")
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .PUT(publisher)
            .build();
    return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static String padToLimit(String body) {
    return body + " ".repeat(MAX_BODY_BYTES - body.getBytes(StandardCharsets.UTF_8).length);
  }

  private static HttpInputMessage inputMessage(InputStream body, Long contentLength) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (contentLength != null) {
      headers.setContentLength(contentLength);
    }
    return new HttpInputMessage() {
      @Override
      public InputStream getBody() {
        return body;
      }

      @Override
      public HttpHeaders getHeaders() {
        return headers;
      }
    };
  }

  private static MethodParameter detailsBodyParameter() throws NoSuchMethodException {
    Method method =
        ApplicationController.class.getMethod(
            "updateApplicationDetails",
            UUID.class,
            UpdateApplicationDetailsRequestBody.class,
            String.class);
    return new MethodParameter(method, 1);
  }

  private static void stubSuccessfulEdit() {
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + APPLICATION_ID))
            .willReturn(
                okJson(
                    """
                    {
                      "id": "%s",
                      "providerOfficeCode": "%s",
                      "applicationState": "DRAFT",
                      "eTag": 31
                    }
                    """
                        .formatted(APPLICATION_ID, TestJwtConfig.AUTHORIZED_OFFICE_CODE))));
    DATASTORE.stubFor(
        WireMock.patch(
                urlPathEqualTo("/api/v0/applications/" + APPLICATION_ID + ":edit-application"))
            .willReturn(WireMock.aResponse().withStatus(204).withHeader("ETag", "\"32\"")));
  }

  private static final class GuardedInputStream extends InputStream {

    private final int maxBytes;
    private int bytesRead;

    private GuardedInputStream(int maxBytes) {
      this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
      if (bytesRead == maxBytes) {
        throw new IOException("guarded stream read limit exceeded");
      }
      bytesRead++;
      return ' ';
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
      if (length == 0) {
        return 0;
      }
      if (bytesRead == maxBytes) {
        throw new IOException("guarded stream read limit exceeded");
      }
      int count = Math.min(length, maxBytes - bytesRead);
      Arrays.fill(bytes, offset, offset + count, (byte) ' ');
      bytesRead += count;
      return count;
    }

    private int bytesRead() {
      return bytesRead;
    }
  }
}
