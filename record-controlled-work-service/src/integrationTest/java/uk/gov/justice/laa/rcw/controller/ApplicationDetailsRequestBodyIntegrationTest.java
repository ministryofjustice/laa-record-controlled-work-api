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
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import uk.gov.justice.laa.rcw.SpringBootMicroserviceApplication;
import uk.gov.justice.laa.rcw.exception.ApplicationRequestTooLargeException;
import uk.gov.justice.laa.rcw.generator.UpdateApplicationDetailsRequestGenerator;
import uk.gov.justice.laa.rcw.model.UpdateApplicationDetailsRequestBody;
import uk.gov.justice.laa.rcw.utils.BaseIntegrationTest;
import uk.gov.justice.laa.rcw.utils.DatastoreTestSupport;
import uk.gov.justice.laa.rcw.utils.GuardedInputStream;
import uk.gov.justice.laa.rcw.utils.TestJwtConfig;

@SpringBootTest(
    classes = SpringBootMicroserviceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationDetailsRequestBodyIntegrationTest extends BaseIntegrationTest {

  private static final int MAX_BODY_BYTES = 65_536;
  private static final UUID APPLICATION_ID =
      UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
  private static final String DETAILS_PATH = "/api/v1/applications/" + APPLICATION_ID + "/details";
  private static final WireMockServer DATASTORE =
      new WireMockServer(WireMockConfiguration.options().dynamicPort());
  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

  static {
    DATASTORE.start();
  }

  @LocalServerPort private int port;
  @Autowired private UpdateApplicationDetailsRequestBodyValidator requestBodyValidator;

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
  void shouldAcceptExactLimitOverChunkedHttp() throws Exception {
    stubSuccessfulEdit();

    HttpResponse<String> response = sendChunked(padToLimit(validBody()));

    assertThat(response.statusCode()).isEqualTo(204);
    DATASTORE.verify(1, getRequestedFor(urlPathEqualTo("/api/v0/applications/" + APPLICATION_ID)));
    DATASTORE.verify(
        1,
        patchRequestedFor(
            urlPathEqualTo("/api/v0/applications/" + APPLICATION_ID + ":edit-application")));
  }

  @Test
  void shouldRejectLimitPlusOneOverChunkedHttpWithoutDatastoreRequests() throws Exception {
    String requestBody = padToLimit(validBody()) + " ";

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
  void shouldBoundReadsWhenContentLengthIsAbsentOrMisleading(Long contentLength) throws Exception {
    GuardedInputStream body = new GuardedInputStream(MAX_BODY_BYTES + 1);
    MockHttpInputMessage inputMessage = new MockHttpInputMessage(body);
    inputMessage.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    if (contentLength != null) {
      inputMessage.getHeaders().setContentLength(contentLength);
    }
    MethodParameter parameter = detailsBodyParameter();

    assertThatThrownBy(
            () ->
                requestBodyValidator.beforeBodyRead(
                    inputMessage,
                    parameter,
                    parameter.getGenericParameterType(),
                    JacksonJsonHttpMessageConverter.class))
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

  private String validBody() throws Exception {
    var request = UpdateApplicationDetailsRequestGenerator.validRequest().ecfFlag(false);
    request.getClientDetails().setFirstName("PrivateValueForOversizedBody");
    return toJson(request);
  }

  private static String padToLimit(String body) {
    return body + " ".repeat(MAX_BODY_BYTES - body.getBytes(StandardCharsets.UTF_8).length);
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
}
