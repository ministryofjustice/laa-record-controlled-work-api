package uk.gov.justice.laa.rcw.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateApplicationCommand;
import uk.gov.justice.laa.rcw.SpringBootMicroserviceApplication;
import uk.gov.justice.laa.rcw.constants.CorrelationConstants;
import uk.gov.justice.laa.rcw.datastore.client.DatastoreClientConfiguration;
import uk.gov.justice.laa.rcw.mapper.ApplicationMapper;
import uk.gov.justice.laa.rcw.model.PriorLegalAid;
import uk.gov.justice.laa.rcw.model.UpdateAddressRequestBody;
import uk.gov.justice.laa.rcw.model.UpdateApplicationDetailsRequestBody;
import uk.gov.justice.laa.rcw.model.UpdateClientDetailsRequestBody;
import uk.gov.justice.laa.rcw.utils.BaseIntegrationTest;
import uk.gov.justice.laa.rcw.utils.DatastoreTestSupport;
import uk.gov.justice.laa.rcw.utils.TestJwtConfig;

@SpringBootTest(classes = SpringBootMicroserviceApplication.class)
class ApplicationGatewayIntegrationTest extends BaseIntegrationTest {

  private static final UUID APPLICATION_ID =
      UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
  private static final String CORRELATION_ID = "edit-details-correlation";
  private static final WireMockServer DATASTORE =
      new WireMockServer(WireMockConfiguration.options().dynamicPort());

  static {
    DATASTORE.start();
  }

  @Autowired private ApplicationGateway applicationGateway;
  @Autowired private ApplicationMapper applicationMapper;
  @Autowired private MeterRegistry meterRegistry;
  private Logger logger;
  private Level previousLogLevel;
  private ListAppender<ILoggingEvent> appender;

  @DynamicPropertySource
  static void datastoreProperties(DynamicPropertyRegistry registry) {
    DatastoreTestSupport.registerProperties(registry, DATASTORE);
  }

  @BeforeAll
  static void stubTokenEndpoint() {
    DatastoreTestSupport.stubTokenEndpoint(DATASTORE);
  }

  @BeforeEach
  void captureOAuthEvents() {
    logger = (Logger) LoggerFactory.getLogger(DatastoreClientConfiguration.class);
    previousLogLevel = logger.getLevel();
    logger.setLevel(Level.INFO);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterAll
  static void stopWireMock() {
    DATASTORE.stop();
  }

  @AfterEach
  void clearRequestContext() {
    SecurityContextHolder.clearContext();
    MDC.remove(CorrelationConstants.CORRELATION_ID_LOG_KEY);
    logger.detachAppender(appender);
    logger.setLevel(previousLogLevel);
    DatastoreTestSupport.resetMappingsAndStubTokenEndpoint(DATASTORE);
  }

  @Test
  void shouldExchangeTokenOnceAcrossColdAndWarmAuthorization() {
    authenticateRequest("cold-warm-cache-user");
    stubApplicationRead();

    applicationGateway.fetchApplication(APPLICATION_ID);
    applicationGateway.fetchApplication(APPLICATION_ID);

    DATASTORE.verify(1, postRequestedFor(urlPathEqualTo("/default/token")));
    DATASTORE.verify(2, getRequestedFor(urlPathEqualTo("/api/v0/applications/" + APPLICATION_ID)));
    assertThat(events("datastore.authorization"))
        .extracting(event -> keyValue(event, "event.phase"))
        .containsExactly("start", "finish", "start", "finish");
    assertThat(events("datastore.token-exchange"))
        .extracting(event -> keyValue(event, "event.phase"))
        .containsExactly("start", "finish");
    assertThat(events("datastore.operation"))
        .extracting(event -> keyValue(event, "event.phase"))
        .containsExactly("start", "finish", "start", "finish");
    assertNoSensitiveTokenInEvents();
  }

  @Test
  void shouldExchangeAgainWhenCachedTokenIsExpired() {
    authenticateRequest("expired-cache-user");
    stubApplicationRead();
    DATASTORE.stubFor(
        WireMock.post(urlPathEqualTo("/default/token"))
            .willReturn(
                okJson(
                    """
                    {
                      "access_token": "obo-access-token",
                      "token_type": "Bearer",
                      "expires_in": 0,
                      "scope": "DataStore.Access"
                    }
                    """)));

    applicationGateway.fetchApplication(APPLICATION_ID);
    applicationGateway.fetchApplication(APPLICATION_ID);

    DATASTORE.verify(2, postRequestedFor(urlPathEqualTo("/default/token")));
    assertThat(events("datastore.token-exchange"))
        .extracting(event -> keyValue(event, "event.phase"))
        .containsExactly("start", "finish", "start", "finish");
  }

  @Test
  void shouldMeasureDelayedTokenExchange() {
    authenticateRequest("delayed-exchange-user");
    stubApplicationRead();
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
                        """)
                    .withFixedDelay(200)));

    applicationGateway.fetchApplication(APPLICATION_ID);

    ILoggingEvent exchangeFinished = events("datastore.token-exchange").getLast();
    assertThat(keyValue(exchangeFinished, "event.phase")).isEqualTo("finish");
    assertThat(((Number) keyValue(exchangeFinished, "duration_ms")).doubleValue())
        .isGreaterThanOrEqualTo(150);
    assertNoSensitiveTokenInEvents();
  }

  @Test
  void shouldReportOperationCompletionAfterDelayedResponseBody() {
    authenticateRequest("delayed-body-user");
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + APPLICATION_ID))
            .willReturn(
                okJson(
                        """
                        {
                          "id": "%s",
                          "providerOfficeCode": "123456"
                        }
                        """
                            .formatted(APPLICATION_ID))
                    .withChunkedDribbleDelay(2, 400)));

    applicationGateway.fetchApplication(APPLICATION_ID);

    List<ILoggingEvent> operationEvents = events("datastore.operation");
    List<ILoggingEvent> transportEvents = events("datastore.transport");
    ILoggingEvent operationFinished = operationEvents.getLast();
    ILoggingEvent headersReceived = transportEvents.getLast();
    double operationDuration = ((Number) keyValue(operationFinished, "duration_ms")).doubleValue();
    double transportDuration = ((Number) keyValue(headersReceived, "duration_ms")).doubleValue();

    assertThat(operationEvents)
        .extracting(event -> keyValue(event, "event.phase"))
        .containsExactly("start", "finish");
    assertThat(transportEvents)
        .extracting(event -> keyValue(event, "event.phase"))
        .containsExactly("start", "finish");
    assertThat(keyValue(operationFinished, "event.outcome")).isEqualTo("success");
    assertThat(keyValue(headersReceived, "event.outcome")).isEqualTo("headers_received");
    assertThat(keyValue(operationFinished, "datastore.operation")).isEqualTo("fetch_application");
    assertThat(operationDuration).isGreaterThan(transportDuration + 150);
    assertThat(((Number) keyValue(operationFinished, "pool.max_total")).intValue()).isPositive();
    assertThat(((Number) keyValue(operationFinished, "pool.default_max_per_route")).intValue())
        .isPositive();
    assertThat(((Number) keyValue(operationFinished, "pool.leased")).intValue())
        .isGreaterThanOrEqualTo(0);

    var requestTimer =
        meterRegistry
            .find("http.client.requests")
            .tag("operation", "fetch_application")
            .tag("method", "GET")
            .tag("status", "200")
            .timer();
    assertThat(requestTimer).isNotNull();
    assertThat(requestTimer.getId().getTags().toString()).doesNotContain(APPLICATION_ID.toString());
  }

  @Test
  void shouldReportDelayedHeadersAtTransportAndOperationBoundaries() {
    authenticateRequest("delayed-headers-user");
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + APPLICATION_ID))
            .willReturn(
                okJson(
                        """
                        {
                          "id": "%s",
                          "providerOfficeCode": "123456"
                        }
                        """
                            .formatted(APPLICATION_ID))
                    .withFixedDelay(250)));

    applicationGateway.fetchApplication(APPLICATION_ID);

    ILoggingEvent transportFinished = events("datastore.transport").getLast();
    ILoggingEvent operationFinished = events("datastore.operation").getLast();
    double transportDuration = ((Number) keyValue(transportFinished, "duration_ms")).doubleValue();
    double operationDuration = ((Number) keyValue(operationFinished, "duration_ms")).doubleValue();
    assertThat(transportDuration).isGreaterThanOrEqualTo(200);
    assertThat(operationDuration).isGreaterThanOrEqualTo(transportDuration);
    assertThat(keyValue(transportFinished, "event.outcome")).isEqualTo("headers_received");
    assertThat(keyValue(operationFinished, "event.outcome")).isEqualTo("success");
  }

  @Test
  void shouldReportDecodeFailureAfterHeadersAreReceived() {
    authenticateRequest("decode-failure-user");
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + APPLICATION_ID))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("decode-secret-sentinel")));

    assertThatThrownBy(() -> applicationGateway.fetchApplication(APPLICATION_ID))
        .isInstanceOf(RuntimeException.class);

    assertThat(keyValue(events("datastore.transport").getLast(), "event.outcome"))
        .isEqualTo("headers_received");
    assertOperationFailure("decoding_failure", 200);
    assertNoSensitiveValueInOperationEvents("decode-secret-sentinel");
  }

  @Test
  void shouldReportTruncatedResponseBodyAsOperationFailure() {
    authenticateRequest("truncated-body-user");
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + APPLICATION_ID))
            .willReturn(
                okJson(
                        """
                        {
                          "id": "%s",
                          "providerOfficeCode": "123456"
                        }
                        """
                            .formatted(APPLICATION_ID))
                    .withFault(Fault.MALFORMED_RESPONSE_CHUNK)));

    assertThatThrownBy(() -> applicationGateway.fetchApplication(APPLICATION_ID))
        .isInstanceOf(RuntimeException.class);

    assertOperationFailure("unknown", null);
    assertThat(events("datastore.transport"))
        .extracting(event -> keyValue(event, "event.outcome"))
        .containsExactly("in_progress", "headers_received");
  }

  @Test
  void shouldReportHttpRejectionStatusAsBoundedOperationFailure() {
    authenticateRequest("http-rejection-user");
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + APPLICATION_ID))
            .willReturn(
                WireMock.aResponse().withStatus(404).withBody("http-rejection-secret-sentinel")));

    assertThatThrownBy(() -> applicationGateway.fetchApplication(APPLICATION_ID))
        .isInstanceOf(RuntimeException.class);

    assertOperationFailure("http_rejection", 404);
    assertNoSensitiveValueInOperationEvents("http-rejection-secret-sentinel");

    var rejectionTimer =
        meterRegistry
            .find("http.client.requests")
            .tag("operation", "fetch_application")
            .tag("method", "GET")
            .tag("status", "404")
            .timer();
    assertThat(rejectionTimer).isNotNull();
    assertThat(rejectionTimer.getId().getTags())
        .extracting(tag -> tag.getKey())
        .containsExactlyInAnyOrder("failure", "method", "operation", "phase", "status");
    assertThat(rejectionTimer.getId().getTags().toString()).contains("failure=http_rejection");
  }

  @Test
  void shouldLogOAuthRejectionAndAvoidDatastoreRequest() {
    authenticateRequest("oauth-rejection-user");
    DATASTORE.stubFor(
        WireMock.post(urlPathEqualTo("/default/token"))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(400)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"error\":\"invalid_grant\","
                            + "\"error_description\":\"oauth-secret-sentinel\"}")));

    assertThatThrownBy(() -> applicationGateway.fetchApplication(APPLICATION_ID))
        .isInstanceOf(RuntimeException.class);

    assertTokenExchangeFailedWithoutLeak("oauth-secret-sentinel");
    DATASTORE.verify(0, getRequestedFor(urlPathEqualTo("/api/v0/applications/" + APPLICATION_ID)));
  }

  @Test
  void shouldLogTokenTransportFailureAndAvoidDatastoreRequest() {
    authenticateRequest("transport-failure-user");
    DATASTORE.stubFor(
        WireMock.post(urlPathEqualTo("/default/token"))
            .willReturn(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    assertThatThrownBy(() -> applicationGateway.fetchApplication(APPLICATION_ID))
        .isInstanceOf(RuntimeException.class);

    assertTokenExchangeFailedWithoutLeak("obo-access-token");
    DATASTORE.verify(0, getRequestedFor(urlPathEqualTo("/api/v0/applications/" + APPLICATION_ID)));
  }

  @Test
  void shouldLogMalformedTokenResponseAndAvoidDatastoreRequest() {
    authenticateRequest("malformed-token-user");
    DATASTORE.stubFor(
        WireMock.post(urlPathEqualTo("/default/token"))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("malformed-secret-sentinel")));

    assertThatThrownBy(() -> applicationGateway.fetchApplication(APPLICATION_ID))
        .isInstanceOf(RuntimeException.class);

    assertTokenExchangeFailedWithoutLeak("malformed-secret-sentinel");
    DATASTORE.verify(0, getRequestedFor(urlPathEqualTo("/api/v0/applications/" + APPLICATION_ID)));
  }

  @Test
  void shouldSendSparseEditJsonAndReturnValidatedEtag() {
    authenticateRequest();
    MDC.put(CorrelationConstants.CORRELATION_ID_LOG_KEY, CORRELATION_ID);

    DATASTORE.stubFor(
        WireMock.patch(
                urlPathEqualTo("/api/v0/applications/" + APPLICATION_ID + ":edit-application"))
            .willReturn(WireMock.aResponse().withStatus(204).withHeader("ETag", "\"32\"")));

    String etag =
        applicationGateway.editApplication(
            APPLICATION_ID, applicationMapper.toEditApplicationCommand(detailsRequest(), 31L));

    assertThat(etag).isEqualTo("\"32\"");
    assertThat(events("datastore.retry")).isEmpty();
    DATASTORE.verify(
        1,
        patchRequestedFor(
                urlPathEqualTo("/api/v0/applications/" + APPLICATION_ID + ":edit-application"))
            .withHeader("Authorization", equalTo("Bearer obo-access-token"))
            .withHeader("X-Authorization", equalTo("Bearer " + TestJwtConfig.ACCESS_TOKEN))
            .withHeader("X-Correlation-ID", equalTo(CORRELATION_ID))
            .withHeader("X-Service-Name", equalTo("laa-record-controlled-work-api"))
            .withRequestBody(
                equalToJson(
                    """
                    {
                      "eTag": 31,
                      "reasonForReapplication": null,
                      "ecfFlag": false,
                      "scopingQuestions": {"priorLegalAid": "no"},
                      "clientDetails": {
                        "firstName": "Ada",
                        "lastName": "Lovelace",
                        "dateOfBirth": "1990-01-01",
                        "niNumber": null,
                        "noFixedAbode": false,
                        "address": {
                          "addressLine1": "1 Example Street",
                          "addressLine2": null,
                          "addressLine3": null,
                          "addressLine4": null,
                          "townOrCity": null,
                          "postCode": null,
                          "county": null,
                          "country": "GB"
                        }
                      }
                    }
                    """)));
  }

  @Test
  void shouldUseDefaultJacksonBehaviourForUnrelatedApplicationReads() {
    authenticateRequest();
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + APPLICATION_ID))
            .willReturn(
                okJson(
                    """
                    {
                      "id": "%s",
                      "providerOfficeCode": "123456",
                      "futureField": "ignored"
                    }
                    """
                        .formatted(APPLICATION_ID))));

    ApplicationResponse response = applicationGateway.fetchApplication(APPLICATION_ID);

    assertThat(response.getId()).isEqualTo(APPLICATION_ID);
  }

  @Test
  void shouldUseDefaultJacksonBehaviourForUnrelatedApplicationCommands() {
    authenticateRequest();
    DATASTORE.stubFor(
        WireMock.patch(
                urlPathEqualTo("/api/v0/applications/" + APPLICATION_ID + ":update-application"))
            .willReturn(WireMock.aResponse().withStatus(204)));

    applicationGateway.updateApplication(APPLICATION_ID, new UpdateApplicationCommand().eTag(31L));

    DATASTORE.verify(
        1,
        patchRequestedFor(
                urlPathEqualTo("/api/v0/applications/" + APPLICATION_ID + ":update-application"))
            .withRequestBody(equalToJson("{\"eTag\":31,\"applicationState\":null}")));
  }

  private static void authenticateRequest() {
    authenticateRequest("test-user");
  }

  private static void authenticateRequest(String subject) {
    Jwt jwt =
        Jwt.withTokenValue(TestJwtConfig.ACCESS_TOKEN)
            .header("alg", "none")
            .claim("sub", subject)
            .build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }

  private static void stubApplicationRead() {
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + APPLICATION_ID))
            .willReturn(
                okJson(
                    """
                    {
                      "id": "%s",
                      "providerOfficeCode": "123456"
                    }
                    """
                        .formatted(APPLICATION_ID))));
  }

  private List<ILoggingEvent> events(String action) {
    return appender.list.stream()
        .filter(event -> action.equals(keyValue(event, "event.action")))
        .toList();
  }

  private Object keyValue(ILoggingEvent event, String key) {
    return event.getKeyValuePairs().stream()
        .filter(pair -> pair.key.equals(key))
        .map(pair -> pair.value)
        .findFirst()
        .orElse(null);
  }

  private void assertOperationFailure(String category, Integer statusCode) {
    List<ILoggingEvent> operationEvents = events("datastore.operation");
    assertThat(operationEvents)
        .extracting(event -> keyValue(event, "event.phase"))
        .containsExactly("start", "finish");
    ILoggingEvent finished = operationEvents.getLast();
    assertThat(keyValue(finished, "event.outcome")).isEqualTo("failure");
    assertThat(keyValue(finished, "failure.category")).isEqualTo(category);
    if (statusCode != null) {
      assertThat(keyValue(finished, "http.response.status_code")).isEqualTo(statusCode);
    }
    assertThat(operationEvents).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
  }

  private void assertNoSensitiveValueInOperationEvents(String sentinel) {
    assertThat(events("datastore.operation"))
        .extracting(ILoggingEvent::getFormattedMessage)
        .noneMatch(message -> message.contains(sentinel));
    assertThat(events("datastore.operation"))
        .flatExtracting(ILoggingEvent::getKeyValuePairs)
        .extracting(pair -> String.valueOf(pair.value))
        .noneMatch(value -> value.contains(sentinel));
  }

  private void assertTokenExchangeFailedWithoutLeak(String sentinel) {
    List<ILoggingEvent> events = events("datastore.token-exchange");
    assertThat(events)
        .extracting(event -> keyValue(event, "event.phase"))
        .containsExactly("start", "finish");
    assertThat(keyValue(events.getLast(), "event.outcome")).isEqualTo("failure");
    assertThat(events).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
    assertThat(events)
        .extracting(ILoggingEvent::getFormattedMessage)
        .noneMatch(message -> message.contains(sentinel));
    assertThat(events)
        .flatExtracting(ILoggingEvent::getKeyValuePairs)
        .extracting(pair -> String.valueOf(pair.value))
        .noneMatch(value -> value.contains(sentinel));
  }

  private void assertNoSensitiveTokenInEvents() {
    assertThat(appender.list).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .noneMatch(message -> message.contains("obo-access-token"));
    assertThat(appender.list)
        .flatExtracting(ILoggingEvent::getKeyValuePairs)
        .extracting(pair -> String.valueOf(pair.value))
        .noneMatch(value -> value.contains("obo-access-token"));
  }

  private static UpdateApplicationDetailsRequestBody detailsRequest() {
    UpdateAddressRequestBody address =
        new UpdateAddressRequestBody()
            .addressLine1("1 Example Street")
            .addressLine2(null)
            .addressLine3(null)
            .addressLine4(null)
            .townOrCity(null)
            .postCode(null)
            .county(null)
            .country("GB");
    UpdateClientDetailsRequestBody clientDetails =
        new UpdateClientDetailsRequestBody()
            .firstName("Ada")
            .lastName("Lovelace")
            .dateOfBirth(LocalDate.of(1990, 1, 1))
            .niNumber(null)
            .hasFixedAddress(true)
            .address(address);
    return new UpdateApplicationDetailsRequestBody()
        .priorLegalAid(PriorLegalAid.NO)
        .legalAidLast6Months(false)
        .reasonForReapplication(null)
        .ecfFlag(false)
        .clientDetails(clientDetails);
  }
}
