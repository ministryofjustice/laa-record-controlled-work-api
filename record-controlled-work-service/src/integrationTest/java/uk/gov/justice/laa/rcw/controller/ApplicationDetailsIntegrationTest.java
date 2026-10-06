package uk.gov.justice.laa.rcw.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.experimental.ExtensionMethod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import uk.gov.justice.laa.rcw.SpringBootMicroserviceApplication;
import uk.gov.justice.laa.rcw.generator.UpdateApplicationDetailsRequestGenerator;
import uk.gov.justice.laa.rcw.utils.BaseIntegrationTest;
import uk.gov.justice.laa.rcw.utils.DatastoreTestSupport;
import uk.gov.justice.laa.rcw.utils.TestJwtConfig;
import uk.gov.justice.laa.rcw.utils.extensions.MockHttpServletRequestBuilderExtensions;

@SpringBootTest(classes = SpringBootMicroserviceApplication.class)
@ExtensionMethod(MockHttpServletRequestBuilderExtensions.class)
class ApplicationDetailsIntegrationTest extends BaseIntegrationTest {

  private static final String SERVICE_NAME = "laa-record-controlled-work-api";
  private static final WireMockServer DATASTORE =
      new WireMockServer(WireMockConfiguration.options().dynamicPort());

  static {
    DATASTORE.start();
  }

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
  void resetDatastore() {
    DatastoreTestSupport.resetMappingsAndStubTokenEndpoint(DATASTORE);
  }

  @Test
  void shouldPreserveUpdatedApplicationAfterStaleDetailsEdit() throws Exception {
    String id = UUID.randomUUID().toString();
    String path = "/api/v0/applications/" + id;
    String scenario = "conditional-details-" + id;
    String originalApplication = applicationDetailsResponse(id, 31, "Before");
    String updatedApplication = applicationDetailsResponse(id, 32, "Test");
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo(path))
            .inScenario(scenario)
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(okJson(originalApplication))
            .willSetStateTo("current"));
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo(path))
            .inScenario(scenario)
            .whenScenarioStateIs("current")
            .willReturn(okJson(originalApplication)));
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo(path))
            .inScenario(scenario)
            .whenScenarioStateIs("edited")
            .willReturn(okJson(updatedApplication)));
    DATASTORE.stubFor(
        WireMock.patch(urlPathEqualTo(path + ":edit-application"))
            .inScenario(scenario)
            .whenScenarioStateIs("current")
            .willReturn(WireMock.aResponse().withStatus(204).withHeader("ETag", "\"32\""))
            .willSetStateTo("edited"));
    DATASTORE.stubFor(
        WireMock.patch(urlPathEqualTo(path + ":edit-application"))
            .inScenario(scenario)
            .whenScenarioStateIs("edited")
            .willReturn(
                WireMock.aResponse()
                    .withStatus(409)
                    .withHeader("Content-Type", "application/problem+json")
                    .withBody("{\"status\":409,\"reason\":\"APPLICATION_VERSION_CONFLICT\"}")));

    mockMvc
        .perform(get("/api/v1/applications/{id}", id).withBearerReadToken())
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"31\""))
        .andExpect(jsonPath("$.clientDetails.firstName").value("Before"));

    performValidDetailsPut(id)
        .andExpect(status().isNoContent())
        .andExpect(header().string("ETag", "\"32\""));

    mockMvc
        .perform(get("/api/v1/applications/{id}", id).withBearerReadToken())
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"32\""))
        .andExpect(jsonPath("$.clientDetails.firstName").value("Test"));

    performValidDetailsPut(id)
        .andExpect(status().isPreconditionFailed())
        .andExpect(jsonPath("$.status").value(412))
        .andExpect(jsonPath("$.reason").value("APPLICATION_VERSION_CONFLICT"));

    mockMvc
        .perform(get("/api/v1/applications/{id}", id).withBearerReadToken())
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"32\""))
        .andExpect(jsonPath("$.clientDetails.firstName").value("Test"));

    DATASTORE.verify(5, getRequestedFor(urlPathEqualTo(path)));
    DATASTORE.verify(
        2,
        patchRequestedFor(urlPathEqualTo(path + ":edit-application"))
            .withRequestBody(
                equalToJson(
                    """
                    {
                        "eTag": 31,
                        "reasonForReapplication": null,
                        "ecfFlag": true,
                        "scopingQuestions": {"priorLegalAid": "no"},
                        "clientDetails": {
                            "firstName": "Test",
                            "lastName": "Client",
                            "dateOfBirth": "1990-01-01",
                            "niNumber": null,
                            "noFixedAbode": true,
                            "address": null
                        }
                    }
                    """)));
  }

  @Test
  void shouldEditApplicationDetailsOnceWithCallerVersionAndReturnNewEtag() throws Exception {
    String id = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + id))
            .willReturn(
                okJson(
                    """
                    {
                      "id": "%s",
                      "providerOfficeCode": "%s",
                      "applicationState": "DRAFT",
                      "eTag": 100
                    }
                    """
                        .formatted(id, TestJwtConfig.AUTHORIZED_OFFICE_CODE))));
    DATASTORE.stubFor(
        WireMock.patch(urlPathEqualTo("/api/v0/applications/" + id + ":edit-application"))
            .willReturn(WireMock.aResponse().withStatus(204).withHeader("ETag", "\"32\"")));

    mockMvc
        .perform(
            put("/api/v1/applications/{id}/details", id)
                .withBearerWriteToken()
                .header("If-Match", "\"31\"")
                .header("X-Correlation-ID", "details-edit-correlation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(UpdateApplicationDetailsRequestGenerator.validRequest())))
        .andExpect(status().isNoContent())
        .andExpect(header().string("ETag", "\"32\""));

    DATASTORE.verify(
        1,
        getRequestedFor(urlPathEqualTo("/api/v0/applications/" + id))
            .withHeader("Authorization", equalTo("Bearer obo-access-token"))
            .withHeader("X-Authorization", equalTo("Bearer " + TestJwtConfig.ACCESS_TOKEN))
            .withHeader("X-Correlation-ID", equalTo("details-edit-correlation"))
            .withHeader("X-Service-Name", equalTo(SERVICE_NAME)));
    DATASTORE.verify(
        1,
        patchRequestedFor(urlPathEqualTo("/api/v0/applications/" + id + ":edit-application"))
            .withHeader("Authorization", equalTo("Bearer obo-access-token"))
            .withHeader("X-Authorization", equalTo("Bearer " + TestJwtConfig.ACCESS_TOKEN))
            .withHeader("X-Correlation-ID", equalTo("details-edit-correlation"))
            .withHeader("X-Service-Name", equalTo(SERVICE_NAME))
            .withRequestBody(
                equalToJson(
                    """
                    {
                        "eTag": 31,
                        "reasonForReapplication": null,
                        "ecfFlag": true,
                        "scopingQuestions": {"priorLegalAid": "no"},
                        "clientDetails": {
                            "firstName": "Test",
                            "lastName": "Client",
                            "dateOfBirth": "1990-01-01",
                            "niNumber": null,
                            "noFixedAbode": true,
                            "address": null
                        }
                    }
                    """)));
  }

  @Test
  void shouldRejectUnauthenticatedDetailsPutWithoutDatastoreRequests() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/applications/{id}/details", "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(UpdateApplicationDetailsRequestGenerator.validRequest())))
        .andExpect(status().isUnauthorized());

    DATASTORE.verify(0, WireMock.anyRequestedFor(WireMock.urlMatching("/api/v0/.*")));
  }

  @Test
  void shouldRejectInvalidDetailsWithoutDatastoreRequests() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/applications/{id}/details", "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
                .withBearerWriteToken()
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "priorLegalAid": "no",
                      "legalAidLast6Months": false,
                      "reasonForReapplication": null,
                      "ecfFlag": false,
                      "clientDetails": {
                        "firstName": "Test",
                        "lastName": "Client",
                        "dateOfBirth": "1990-01-01",
                        "hasFixedAddress": false,
                        "address": null
                      }
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("INVALID_APPLICATION_DETAILS"));

    DATASTORE.verify(0, WireMock.anyRequestedFor(WireMock.urlMatching("/api/v0/.*")));
  }

  @Test
  void shouldReturnSameNotFoundForMissingAndForbiddenDetailsPreflight() throws Exception {
    String id = UUID.randomUUID().toString();
    String path = "/api/v0/applications/" + id;
    String scenario = "details-preflight-visibility-" + id;
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo(path))
            .inScenario(scenario)
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(WireMock.notFound())
            .willSetStateTo("forbidden"));
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo(path))
            .inScenario(scenario)
            .whenScenarioStateIs("forbidden")
            .willReturn(WireMock.aResponse().withStatus(403)));

    String missingResponse =
        performValidDetailsPut(id)
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String forbiddenResponse =
        performValidDetailsPut(id)
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertEquals(missingResponse, forbiddenResponse);
    DATASTORE.verify(2, getRequestedFor(urlPathEqualTo(path)));
    DATASTORE.verify(0, patchRequestedFor(urlPathEqualTo(path + ":edit-application")));
  }

  @Test
  void shouldReturnSameNotFoundForMissingAndUnauthorizedOffice() throws Exception {
    String id = "c2c3d4e5-f6a7-8901-bcde-f12345678902";
    String path = "/api/v0/applications/" + id;
    String scenario = "details-put-office-visibility";
    DATASTORE.resetScenarios();
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo(path))
            .inScenario(scenario)
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(WireMock.aResponse().withStatus(404))
            .willSetStateTo("application at unauthorized office"));
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo(path))
            .inScenario(scenario)
            .whenScenarioStateIs("application at unauthorized office")
            .willReturn(
                okJson(
                    """
                    {
                        "id": "%s",
                        "providerOfficeCode": "UNAUTHORIZED",
                        "applicationState": "DRAFT",
                        "eTag": 100
                    }
                    """
                        .formatted(id))));

    String missingResponse =
        performValidDetailsPut(id)
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String hiddenResponse =
        performValidDetailsPut(id)
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertEquals(missingResponse, hiddenResponse);
    DATASTORE.verify(2, getRequestedFor(urlPathEqualTo(path)));
    DATASTORE.verify(0, patchRequestedFor(urlPathEqualTo(path + ":edit-application")));
  }

  @Test
  void shouldHideCompletedApplicationBeforeCheckingCompletionWhenOfficeIsUnauthorized()
      throws Exception {
    String id = UUID.randomUUID().toString();
    String path = "/api/v0/applications/" + id;
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo(path))
            .willReturn(
                okJson(
                    """
                    {
                      "id": "%s",
                      "providerOfficeCode": "OTHER-OFFICE",
                      "applicationState": "COMPLETED",
                      "eTag": 100
                    }
                    """
                        .formatted(id))));

    performValidDetailsPut(id)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.reason").value("APPLICATION_NOT_FOUND"));

    DATASTORE.verify(1, getRequestedFor(urlPathEqualTo(path)));
    DATASTORE.verify(0, patchRequestedFor(urlPathEqualTo(path + ":edit-application")));
  }

  @Test
  void shouldReturnSameNotFoundForMissingAndForbiddenDetailsEdit() throws Exception {
    String id = UUID.randomUUID().toString();
    String path = "/api/v0/applications/" + id;
    String scenario = "details-edit-visibility-" + id;
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo(path))
            .willReturn(
                okJson(
                    """
                    {
                      "id": "%s",
                      "providerOfficeCode": "%s",
                      "applicationState": "DRAFT",
                      "eTag": 100
                    }
                    """
                        .formatted(id, TestJwtConfig.AUTHORIZED_OFFICE_CODE))));
    DATASTORE.stubFor(
        WireMock.patch(urlPathEqualTo(path + ":edit-application"))
            .inScenario(scenario)
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(WireMock.notFound())
            .willSetStateTo("forbidden"));
    DATASTORE.stubFor(
        WireMock.patch(urlPathEqualTo(path + ":edit-application"))
            .inScenario(scenario)
            .whenScenarioStateIs("forbidden")
            .willReturn(WireMock.aResponse().withStatus(403)));

    String missingResponse =
        performValidDetailsPut(id)
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String forbiddenResponse =
        performValidDetailsPut(id)
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertEquals(missingResponse, forbiddenResponse);
    DATASTORE.verify(2, patchRequestedFor(urlPathEqualTo(path + ":edit-application")));
  }

  @Test
  void shouldRejectCompletedApplicationWithoutEditing() throws Exception {
    String id = "d2c3d4e5-f6a7-8901-bcde-f12345678903";
    String path = "/api/v0/applications/" + id;
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo(path))
            .willReturn(
                okJson(
                    """
                    {
                      "id": "%s",
                      "providerOfficeCode": "%s",
                      "applicationState": "COMPLETED",
                      "eTag": 100
                    }
                    """
                        .formatted(id, TestJwtConfig.AUTHORIZED_OFFICE_CODE))));

    performValidDetailsPut(id)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.reason").value("APPLICATION_COMPLETED"));

    DATASTORE.verify(1, getRequestedFor(urlPathEqualTo(path)));
    DATASTORE.verify(0, patchRequestedFor(urlPathEqualTo(path + ":edit-application")));
  }

  @ParameterizedTest
  @MethodSource("invalidEditResponseEtags")
  void shouldReturnBadGatewayWhenEditResponseEtagIsInvalid(String editEtag) throws Exception {
    String id = "e2c3d4e5-f6a7-8901-bcde-f12345678904";
    String path = "/api/v0/applications/" + id;
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo(path))
            .willReturn(
                okJson(
                    """
                    {
                      "id": "%s",
                      "providerOfficeCode": "%s",
                      "applicationState": "DRAFT",
                      "eTag": 100
                    }
                    """
                        .formatted(id, TestJwtConfig.AUTHORIZED_OFFICE_CODE))));
    var editResponse = WireMock.aResponse().withStatus(204);
    if (editEtag != null) {
      editResponse.withHeader("ETag", editEtag);
    }
    DATASTORE.stubFor(
        WireMock.patch(urlPathEqualTo(path + ":edit-application")).willReturn(editResponse));

    performValidDetailsPut(id)
        .andExpect(status().isBadGateway())
        .andExpect(header().doesNotExist("ETag"))
        .andExpect(jsonPath("$.status").value(502))
        .andExpect(jsonPath("$.reason").value("DATASTORE_INVALID_APPLICATION_VERSION"));

    DATASTORE.verify(1, getRequestedFor(urlPathEqualTo(path)));
    DATASTORE.verify(1, patchRequestedFor(urlPathEqualTo(path + ":edit-application")));
  }

  private static Stream<Arguments> invalidEditResponseEtags() {
    return Stream.of(
        Arguments.of((Object) null),
        Arguments.of("W/\"32\""),
        Arguments.of("\"invalid\""),
        Arguments.of("\"9223372036854775808\""));
  }

  @ParameterizedTest
  @MethodSource("detailsEditConflictResponses")
  void shouldClassifyDetailsEditConflictByReason(
      String downstreamBody, int expectedStatus, String expectedReason) throws Exception {
    String id = UUID.randomUUID().toString();
    String path = "/api/v0/applications/" + id;
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo(path))
            .willReturn(
                okJson(
                    """
                    {
                      "id": "%s",
                      "providerOfficeCode": "%s",
                      "applicationState": "DRAFT",
                      "eTag": 100
                    }
                    """
                        .formatted(id, TestJwtConfig.AUTHORIZED_OFFICE_CODE))));
    DATASTORE.stubFor(
        WireMock.patch(urlPathEqualTo(path + ":edit-application"))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(409)
                    .withHeader("Content-Type", "application/problem+json")
                    .withBody(downstreamBody)));

    var response =
        performValidDetailsPut(id)
            .andExpect(status().is(expectedStatus))
            .andExpect(jsonPath("$.status").value(expectedStatus))
            .andExpect(jsonPath("$.reason").value(expectedReason))
            .andReturn()
            .getResponse();

    assertFalse(response.getContentAsString().contains("untrusted downstream detail"));
    DATASTORE.verify(1, getRequestedFor(urlPathEqualTo(path)));
    DATASTORE.verify(
        1,
        patchRequestedFor(urlPathEqualTo(path + ":edit-application"))
            .withRequestBody(
                equalToJson(
                    """
                    {
                      "eTag": 31,
                      "reasonForReapplication": null,
                      "ecfFlag": true,
                      "scopingQuestions": {"priorLegalAid": "no"},
                      "clientDetails": {
                        "firstName": "Test",
                        "lastName": "Client",
                        "dateOfBirth": "1990-01-01",
                        "niNumber": null,
                        "noFixedAbode": true,
                        "address": null
                      }
                    }
                    """)));
  }

  private static Stream<Arguments> detailsEditConflictResponses() {
    return Stream.of(
        Arguments.of(
            "{\"status\":409,\"reason\":\"APPLICATION_VERSION_CONFLICT\","
                + "\"detail\":\"untrusted downstream detail\"}",
            412,
            "APPLICATION_VERSION_CONFLICT"),
        Arguments.of(
            "{\"status\":409,\"reason\":\"APPLICATION_VERSION_CONFLICT\","
                + "\"detail\":\"different conflict source\"}",
            412,
            "APPLICATION_VERSION_CONFLICT"),
        Arguments.of(
            "{\"status\":409,\"reason\":\"APPLICATION_COMPLETED\"}", 409, "APPLICATION_COMPLETED"),
        Arguments.of(
            "{\"status\":409,\"reason\":\"OTHER_CONFLICT\"}", 409, "CONCURRENT_MODIFICATION"),
        Arguments.of("{\"status\":409}", 409, "CONCURRENT_MODIFICATION"),
        Arguments.of("not-json", 409, "CONCURRENT_MODIFICATION"));
  }

  @ParameterizedTest
  @MethodSource("detailsEditFailureResponses")
  void shouldMapDatastoreEditFailuresSafely(
      String downstreamFailure, int expectedStatus, String expectedReason) throws Exception {
    String id = UUID.randomUUID().toString();
    String path = "/api/v0/applications/" + id;
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo(path))
            .willReturn(
                okJson(
                    """
                    {
                        "id": "%s",
                        "providerOfficeCode": "%s",
                        "applicationState": "DRAFT",
                        "eTag": 100
                    }
                    """
                        .formatted(id, TestJwtConfig.AUTHORIZED_OFFICE_CODE))));
    if ("connection".equals(downstreamFailure)) {
      DATASTORE.stubFor(
          WireMock.patch(urlPathEqualTo(path + ":edit-application"))
              .willReturn(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
    } else {
      DATASTORE.stubFor(
          WireMock.patch(urlPathEqualTo(path + ":edit-application"))
              .willReturn(
                  WireMock.aResponse()
                      .withStatus(Integer.parseInt(downstreamFailure))
                      .withBody("untrusted downstream detail")));
    }

    var response =
        performValidDetailsPut(id)
            .andExpect(status().is(expectedStatus))
            .andExpect(jsonPath("$.status").value(expectedStatus))
            .andExpect(jsonPath("$.reason").value(expectedReason))
            .andReturn()
            .getResponse();

    assertFalse(response.getContentAsString().contains("untrusted downstream detail"));
    DATASTORE.verify(1, getRequestedFor(urlPathEqualTo(path)));
    DATASTORE.verify(1, patchRequestedFor(urlPathEqualTo(path + ":edit-application")));
  }

  private static Stream<Arguments> detailsEditFailureResponses() {
    return Stream.of(
        Arguments.of("400", 400, "DATASTORE_REJECTED_REQUEST"),
        Arguments.of("500", 502, "DATASTORE_SERVER_ERROR"),
        Arguments.of("503", 502, "DATASTORE_SERVER_ERROR"),
        Arguments.of("connection", 503, "DATASTORE_UNAVAILABLE"));
  }

  private ResultActions performValidDetailsPut(String id) throws Exception {
    return mockMvc.perform(
        put("/api/v1/applications/{id}/details", id)
            .withBearerWriteToken()
            .header("If-Match", "\"31\"")
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(UpdateApplicationDetailsRequestGenerator.validRequest())));
  }

  private static String applicationDetailsResponse(String id, long version, String firstName) {
    return """
    {
        "id": "%s",
        "providerOfficeCode": "%s",
        "applicationState": "DRAFT",
        "eTag": %d,
        "client": {
            "firstName": "%s",
            "lastName": "Client",
            "dateOfBirth": "1990-01-01",
            "niNumber": null,
            "noFixedAbode": true,
            "address": null
        }
    }
    """
        .formatted(id, TestJwtConfig.AUTHORIZED_OFFICE_CODE, version, firstName);
  }
}
