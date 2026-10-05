package uk.gov.justice.laa.rcw.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.util.Locale;
import java.util.stream.Stream;
import lombok.experimental.ExtensionMethod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import uk.gov.justice.laa.rcw.SpringBootMicroserviceApplication;
import uk.gov.justice.laa.rcw.generator.CreateApplicationRequestGenerator;
import uk.gov.justice.laa.rcw.model.CreateApplicationRequestBody;
import uk.gov.justice.laa.rcw.model.CreateScopingQuestions;
import uk.gov.justice.laa.rcw.model.FamilyLawClassification;
import uk.gov.justice.laa.rcw.model.PriorLegalAid;
import uk.gov.justice.laa.rcw.utils.BaseIntegrationTest;
import uk.gov.justice.laa.rcw.utils.DatastoreTestSupport;
import uk.gov.justice.laa.rcw.utils.TestJwtConfig;
import uk.gov.justice.laa.rcw.utils.extensions.MockHttpServletRequestBuilderExtensions;

@SpringBootTest(classes = SpringBootMicroserviceApplication.class)
@ExtensionMethod(MockHttpServletRequestBuilderExtensions.class)
class ApplicationsControllerIntegrationTest extends BaseIntegrationTest {

  private static final String SERVICE_NAME = "laa-record-controlled-work-api";
  private static final String VALID_DETAILS_PUT_BODY =
      """
      {
          "priorLegalAid": "no",
          "legalAidLast6Months": false,
          "reasonForReapplication": null,
          "ecfFlag": true,
          "clientDetails": {
              "firstName": "Test",
              "lastName": "Client",
              "dateOfBirth": "1990-01-01",
              "niNumber": null,
              "hasFixedAddress": false,
              "address": null
          }
      }
      """;
  private static final String UUID_REGEX =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
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
  void resetDatastoreApplicationsStub() {
    DatastoreTestSupport.resetMappingsAndStubTokenEndpoint(DATASTORE);
  }

  @Test
  void shouldGetAllApplications() throws Exception {
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications"))
            .willReturn(
                okJson(
                    """
                    {
                      "content": [
                        {
                          "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                          "clientFirstName": "Jane",
                          "clientLastName": "Doe",
                          "referenceNumber": "REF123",
                                                    "modifiedAt": "2024-01-01T10:00:00Z",
                                                    "eligibilityIndication": "eligible"
                        }
                      ],
                      "page": 1,
                      "size": 1,
                      "totalElements": 1,
                      "totalPages": 1
                    }
                    """)));

    mockMvc
        .perform(
            get("/api/v1/applications")
                .param("page", "1")
                .param("size", "1")
                .param("officeId", "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
                .param("status", "DRAFT")
                .param("eligibilityIndication", "ELIGIBLE")
                .withBearerReadToken())
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$[0].id").value("a1b2c3d4-e5f6-7890-abcd-ef1234567890"))
        .andExpect(jsonPath("$[0].name").value("Jane Doe"))
        .andExpect(jsonPath("$[0].applicationRefNumber").value("REF123"))
        .andExpect(jsonPath("$[0].eligibilityIndication").value("eligible"));

    DATASTORE.verify(
        getRequestedFor(urlPathEqualTo("/api/v0/applications"))
            .withQueryParam("status", equalTo("DRAFT"))
            .withQueryParam("eligibilityIndication", equalTo("eligible")));
  }

  @Test
  void shouldGetAllApplications_whenEligibilityIndicationIsIneligible() throws Exception {
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications"))
            .willReturn(
                okJson(
                    """
                    {
                        "content": [
                            {
                                "id": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
                                "clientFirstName": "John",
                                "clientLastName": "Smith",
                                "referenceNumber": "REF456",
                                "modifiedAt": "2024-02-01T10:00:00Z",
                                "eligibilityIndication": "ineligible"
                            }
                        ],
                        "page": 1,
                        "size": 1,
                        "totalElements": 1,
                        "totalPages": 1
                    }
                    """)));

    mockMvc
        .perform(
            get("/api/v1/applications")
                .param("page", "1")
                .param("size", "1")
                .param("officeId", "b2c3d4e5-f6a7-8901-bcde-f12345678901")
                .param("status", "COMPLETED")
                .param("eligibilityIndication", "INELIGIBLE")
                .withBearerReadToken())
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$[0].id").value("b2c3d4e5-f6a7-8901-bcde-f12345678901"))
        .andExpect(jsonPath("$[0].name").value("John Smith"))
        .andExpect(jsonPath("$[0].applicationRefNumber").value("REF456"))
        .andExpect(jsonPath("$[0].eligibilityIndication").value("ineligible"));

    DATASTORE.verify(
        getRequestedFor(urlPathEqualTo("/api/v0/applications"))
            .withQueryParam("status", equalTo("COMPLETED"))
            .withQueryParam("eligibilityIndication", equalTo("ineligible")));
  }

  @Test
  void shouldForwardOboAndOriginalTokenHeadersToDatastore() throws Exception {
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications"))
            .willReturn(
                okJson(
                    """
                    {
                      "content": [],
                      "page": 1,
                      "size": 1,
                      "totalElements": 0,
                      "totalPages": 0
                    }
                    """)));

    mockMvc
        .perform(
            get("/api/v1/applications")
                .param("page", "1")
                .param("size", "1")
                .param("officeId", "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
                .header("X-Correlation-Id", "incoming-correlation-id")
                .withBearerReadToken())
        .andExpect(status().isOk());

    DATASTORE.verify(
        getRequestedFor(urlPathEqualTo("/api/v0/applications"))
            .withHeader("Authorization", equalTo("Bearer obo-access-token"))
            .withHeader("X-Authorization", equalTo("Bearer " + TestJwtConfig.ACCESS_TOKEN))
            .withHeader("X-Correlation-ID", equalTo("incoming-correlation-id"))
            .withHeader("X-Service-Name", equalTo(SERVICE_NAME)));
  }

  @Test
  void shouldGenerateCorrelationIdWhenIncomingHeaderIsBlank() throws Exception {
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications"))
            .willReturn(
                okJson(
                    """
                    {
                      "content": [],
                      "page": 1,
                      "size": 1,
                      "totalElements": 0,
                      "totalPages": 0
                    }
                    """)));

    mockMvc
        .perform(get("/api/v1/applications").header("X-Correlation-Id", "  ").withBearerReadToken())
        .andExpect(status().isOk());

    DATASTORE.verify(
        getRequestedFor(urlPathEqualTo("/api/v0/applications"))
            .withHeader("X-Correlation-ID", matching(UUID_REGEX))
            .withHeader("X-Service-Name", equalTo(SERVICE_NAME)));
  }

  @Test
  void shouldReturnBadRequest_whenListDatastoreRejectsRequest() throws Exception {
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications"))
            .willReturn(WireMock.aResponse().withStatus(400)));

    mockMvc
        .perform(get("/api/v1/applications").withBearerReadToken())
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturnBadGateway_whenListDatastoreReturnsServerError() throws Exception {
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications"))
            .willReturn(WireMock.aResponse().withStatus(500)));

    mockMvc
        .perform(get("/api/v1/applications").withBearerReadToken())
        .andExpect(status().isBadGateway());
  }

  @Test
  void shouldReturnServiceUnavailable_whenListDatastoreCannotBeReached() throws Exception {
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications"))
            .willReturn(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    mockMvc
        .perform(get("/api/v1/applications").withBearerReadToken())
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void shouldPreserveUpdatedApplicationAfterStaleDetailsEdit() throws Exception {
    String id = java.util.UUID.randomUUID().toString();
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
    String expectedEditCommand =
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
        """;
    DATASTORE.verify(
        2,
        patchRequestedFor(urlPathEqualTo(path + ":edit-application"))
            .withRequestBody(equalToJson(expectedEditCommand)));
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
                .content(VALID_DETAILS_PUT_BODY))
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
                            "niNumber": null,
                            "hasFixedAddress": false,
                            "address": null
                        }
                    }
                    """))
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

  @ParameterizedTest
  @ValueSource(longs = {0L, 17L, Long.MAX_VALUE})
  void shouldReturnDatastoreBodyVersionAndUnknownLegacyDetails(long version) throws Exception {
    String id = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    String body =
        """
        {
          "id":"%s",
          "providerOfficeCode":"%s",
          "eTag":%d,
          "ecfFlag":null,
          "scopingQuestions":{"otherAnswer":true}
        }
        """
            .formatted(id, TestJwtConfig.AUTHORIZED_OFFICE_CODE, version);
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + id))
            .willReturn(okJson(body).withHeader("ETag", "\"999\"")));

    mockMvc
        .perform(
            get("/api/v1/applications/{id}", id)
                .header("X-Correlation-ID", "get-details-correlation")
                .withBearerReadToken())
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"" + version + "\""))
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.ecfFlag").value(nullValue()))
        .andExpect(jsonPath("$.scopingQuestions.priorLegalAid").value(nullValue()))
        .andExpect(jsonPath("$.eTag").doesNotExist());

    DATASTORE.verify(
        1,
        getRequestedFor(urlPathEqualTo("/api/v0/applications/" + id))
            .withHeader("Authorization", equalTo("Bearer obo-access-token"))
            .withHeader("X-Authorization", equalTo("Bearer " + TestJwtConfig.ACCESS_TOKEN))
            .withHeader("X-Correlation-ID", equalTo("get-details-correlation"))
            .withHeader("X-Service-Name", equalTo(SERVICE_NAME)));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        ",\"eTag\":null",
        ",\"eTag\":-1",
        ",\"eTag\":\"invalid\"",
        ",\"eTag\":\"17\"",
        ",\"eTag\":9223372036854775808",
        ",\"eTag\":1.5",
        ",\"eTag\":1.0",
        ",\"eTag\":1e2",
        ",\"eTag\":-0.5",
        ",\"eTag\":\"0\"",
        ",\"eTag\":true",
        ",\"eTag\":{}",
        ",\"eTag\":[]"
      })
  void shouldReturnBadGatewayForInvalidBodyVersion(String version) throws Exception {
    String id = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + id))
            .willReturn(
                okJson(
                    "{\"id\":\"%s\",\"providerOfficeCode\":\"%s\"%s}"
                        .formatted(id, TestJwtConfig.AUTHORIZED_OFFICE_CODE, version))));

    mockMvc
        .perform(get("/api/v1/applications/{id}", id).withBearerReadToken())
        .andExpect(status().isBadGateway())
        .andExpect(header().doesNotExist("ETag"))
        .andExpect(jsonPath("$.status").value(502))
        .andExpect(jsonPath("$.reason").value("DATASTORE_INVALID_APPLICATION_VERSION"));

    DATASTORE.verify(1, getRequestedFor(urlPathEqualTo("/api/v0/applications/" + id)));
  }

  @Test
  void shouldGetApplication() throws Exception {
    String applicationId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + applicationId))
            .willReturn(
                okJson(
                    """
                    {
                        "id": "%s",
                        "individualLegalAidNumber": "ebd50ba0-9ed9-4003-83a8-c11ac07d9e32",
                        "providerFirmCode": "123456",
                        "providerOfficeCode": "%s",
                        "referenceNumber": "CW-111111",
                        "ufn": "123456/123",
                        "eTag": 5,
                        "scopingQuestions": {
                            "priorLegalAid": "yesSameMatter",
                            "familyLawClassification": "private",
                            "needsAdviceOnEUOrInternationalMaintenance": true
                        },
                        "applicationType": "CONTROLLED_WORK",
                        "declaration": {
                            "id": "d4e5f6a7-b8c9-0123-def1-234567890123",
                            "clientDeclarationStatus": "DRAFT",
                            "declarationConfirmation": true
                        },
                        "eligibilityResult": {
                            "data": {"level_of_help": "controlled"},
                            "result": {"indication": true}
                        }
                    }
                    """
                        .formatted(applicationId, TestJwtConfig.AUTHORIZED_OFFICE_CODE))));

    mockMvc
        .perform(get("/api/v1/applications/%s".formatted(applicationId)).withBearerReadToken())
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(
            jsonPath("$.individualLegalAidNumber").value("ebd50ba0-9ed9-4003-83a8-c11ac07d9e32"))
        .andExpect(jsonPath("$.applicationRefNumber").value("CW-111111"))
        .andExpect(jsonPath("$.ufn").value("123456/123"))
        .andExpect(jsonPath("$.scopingQuestions.priorLegalAid").value("yesSameMatter"))
        .andExpect(jsonPath("$.scopingQuestions.familyLawClassification").value("private"))
        .andExpect(
            jsonPath("$.scopingQuestions.needsAdviceOnEUOrInternationalMaintenance").value(true))
        .andExpect(jsonPath("$.declaration.id").value("d4e5f6a7-b8c9-0123-def1-234567890123"))
        .andExpect(jsonPath("$.declaration.clientDeclarationStatus").doesNotExist())
        .andExpect(jsonPath("$.evidence.evidenceStatus").doesNotExist())
        .andExpect(jsonPath("$.eligibility.data.level_of_help").value("controlled"))
        .andExpect(jsonPath("$.eligibility.result.indication").value(true));
  }

  @Test
  void shouldOmitNullEligibilityDataPropertiesAndPreservePopulatedValues() throws Exception {
    String applicationId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + applicationId))
            .willReturn(
                okJson(
                    """
                    {
                        "id": "%s",
                        "providerOfficeCode": "%s",
                        "referenceNumber": null,
                        "eTag": 0,
                        "eligibilityResult": {
                            "data": {
                                "additional_property_owned": null,
                                "adult_dependants": false,
                                "adult_dependants_count": 0,
                                "bank_accounts": [
                                    {
                                        "amount": 0,
                                        "account_in_dispute": false
                                    }
                                ],
                                "benefits": [],
                                "early_result": {
                                    "result": null,
                                    "gross_income_excess": 0,
                                    "type": "income"
                                },
                                "incomes": [
                                    {
                                        "gross_income": 0,
                                        "income_frequency": "monthly",
                                        "income_tax": null
                                    }
                                ],
                                "api_response": {
                                    "legacy": true
                                },
                                "feature_flags": {
                                    "active": false,
                                    "discard": null
                                },
                                "pending": {
                                    "saved": 0,
                                    "discard": null
                                }
                            },
                            "result": {
                                "keep": false,
                                "discard": null,
                                "empty": []
                            }
                        }
                    }
                    """
                        .formatted(applicationId, TestJwtConfig.AUTHORIZED_OFFICE_CODE))));

    mockMvc
        .perform(get("/api/v1/applications/%s".formatted(applicationId)).withBearerReadToken())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.eligibility.data.additional_property_owned").doesNotHaveJsonPath())
        .andExpect(jsonPath("$.eligibility.data.level_of_help").doesNotHaveJsonPath())
        .andExpect(jsonPath("$.eligibility.data.adult_dependants").value(false))
        .andExpect(jsonPath("$.eligibility.data.adult_dependants_count").value(0))
        .andExpect(jsonPath("$.eligibility.data.bank_accounts[0].amount").value(0))
        .andExpect(jsonPath("$.eligibility.data.bank_accounts[0].account_in_dispute").value(false))
        .andExpect(jsonPath("$.eligibility.data.benefits").isEmpty())
        .andExpect(jsonPath("$.eligibility.data.early_result.result").doesNotHaveJsonPath())
        .andExpect(jsonPath("$.eligibility.data.early_result.gross_income_excess").value(0))
        .andExpect(jsonPath("$.eligibility.data.early_result.type").value("income"))
        .andExpect(jsonPath("$.eligibility.data.incomes[0].gross_income").value(0))
        .andExpect(jsonPath("$.eligibility.data.incomes[0].income_frequency").value("monthly"))
        .andExpect(jsonPath("$.eligibility.data.incomes[0].income_tax").doesNotHaveJsonPath())
        .andExpect(
            jsonPath("$.eligibility.data.incomes[0].national_insurance").doesNotHaveJsonPath())
        .andExpect(jsonPath("$.eligibility.data.api_response").doesNotHaveJsonPath())
        .andExpect(jsonPath("$.eligibility.data.feature_flags.active").value(false))
        .andExpect(jsonPath("$.eligibility.data.feature_flags.discard").doesNotHaveJsonPath())
        .andExpect(jsonPath("$.eligibility.data.pending.saved").value(0))
        .andExpect(jsonPath("$.eligibility.data.pending.discard").doesNotHaveJsonPath())
        .andExpect(jsonPath("$.eligibility.result.keep").value(false))
        .andExpect(jsonPath("$.eligibility.result.discard").value(nullValue()))
        .andExpect(jsonPath("$.eligibility.result.empty").isEmpty())
        .andExpect(jsonPath("$.applicationRefNumber").value(nullValue()));
  }

  @Test
  void shouldKeepNullEligibilityDataAndResultAndNullableApplicationFields() throws Exception {
    String applicationId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + applicationId))
            .willReturn(
                okJson(
                    """
                    {
                        "id": "%s",
                        "providerOfficeCode": "%s",
                        "referenceNumber": null,
                        "eTag": 0,
                        "eligibilityResult": {
                            "data": null,
                            "result": {"indication": false}
                        }
                    }
                    """
                        .formatted(applicationId, TestJwtConfig.AUTHORIZED_OFFICE_CODE))));

    mockMvc
        .perform(get("/api/v1/applications/%s".formatted(applicationId)).withBearerReadToken())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.eligibility.data").value(nullValue()))
        .andExpect(jsonPath("$.eligibility.result.indication").value(false))
        .andExpect(jsonPath("$.applicationRefNumber").value(nullValue()));
  }

  @Test
  void shouldReturnNotFound_whenGettingApplicationInAnotherOffice() throws Exception {
    String applicationId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + applicationId))
            .willReturn(
                okJson(
                    """
                    {
                      "id": "%s",
                                            "providerOfficeCode": "%s",
                                            "eTag": -1
                    }
                    """
                        .formatted(applicationId, TestJwtConfig.AUTHORIZED_OFFICE_CODE))));

    mockMvc
        .perform(
            get("/api/v1/applications/%s".formatted(applicationId)).withBearerUnauthorizedToken())
        .andExpect(status().isNotFound())
        .andExpect(header().doesNotExist("ETag"))
        .andExpect(content().string(""));
  }

  @Test
  void shouldReturnNotFound_whenGettingApplicationWithNoAuthorizedOffice() throws Exception {
    String applicationId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + applicationId))
            .willReturn(
                okJson(
                    """
                    {
                      "id": "%s",
                      "providerOfficeCode": "%s"
                    }
                    """
                        .formatted(applicationId, TestJwtConfig.AUTHORIZED_OFFICE_CODE))));

    mockMvc
        .perform(get("/api/v1/applications/%s".formatted(applicationId)).withBearerNoOfficeToken())
        .andExpect(status().isNotFound())
        .andExpect(header().doesNotExist("ETag"))
        .andExpect(content().string(""));
  }

  @Test
  void shouldReturnNotFound_whenGettingApplicationThatDoesNotExist() throws Exception {
    String applicationId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + applicationId))
            .willReturn(WireMock.notFound()));

    mockMvc
        .perform(get("/api/v1/applications/%s".formatted(applicationId)).withBearerReadToken())
        .andExpect(status().isNotFound())
        .andExpect(header().doesNotExist("ETag"))
        .andExpect(content().string(""));
  }

  @Test
  void shouldReturnSameNotFoundForMissingAndForbiddenDatastoreGet() throws Exception {
    String id = java.util.UUID.randomUUID().toString();
    String path = "/api/v0/applications/" + id;
    String scenario = "get-visibility-" + id;
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
        mockMvc
            .perform(get("/api/v1/applications/{id}", id).withBearerReadToken())
            .andExpect(status().isNotFound())
            .andExpect(header().doesNotExist("ETag"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String forbiddenResponse =
        mockMvc
            .perform(get("/api/v1/applications/{id}", id).withBearerReadToken())
            .andExpect(status().isNotFound())
            .andExpect(header().doesNotExist("ETag"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertEquals(missingResponse, forbiddenResponse);
    DATASTORE.verify(2, getRequestedFor(urlPathEqualTo(path)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"BG123456C", "AO123456C", "AB123456s"})
  void shouldReturnBadRequest_whenNiNumberDoesNotMatchUkFormat(String niNumber) throws Exception {
    CreateApplicationRequestBody request = CreateApplicationRequestGenerator.createWithName(null);
    request.setProviderOfficeCode(TestJwtConfig.AUTHORIZED_OFFICE_CODE);
    request.setReasonForReapplication("");
    request.getClientDetails().getAddress().setAddressLine3("");
    request.getClientDetails().getAddress().setAddressLine4("");
    request.getClientDetails().getAddress().setCounty("");
    request.getClientDetails().getAddress().setPostCode("SW1A 2AA");
    request.getClientDetails().setNiNumber(niNumber);

    mockMvc
        .perform(
            post("/api/v1/applications")
                .withBearerReadToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "AB123456A",
        "CE123456B",
        "EG123456C",
        "HJ123456D",
        "JP123456A",
        "PR123456B",
        "TW123456C",
        "WZ123456D",
        "a.b123456c"
      })
  void shouldCreateApplication(String niNumber) throws Exception {

    CreateApplicationRequestBody request =
        CreateApplicationRequestGenerator.createWithName(
            builder ->
                builder
                    .providerOfficeCode(TestJwtConfig.AUTHORIZED_OFFICE_CODE)
                    .scopingQuestions(
                        new CreateScopingQuestions()
                            .priorLegalAid(PriorLegalAid.YES_SAME_MATTER)
                            .familyLawClassification(FamilyLawClassification.PRIVATE)
                            .needsAdviceOnEUOrInternationalMaintenance(true)));
    request.getClientDetails().setNiNumber(niNumber);
    request.getClientDetails().getAddress().setAddressLine3("");
    request.getClientDetails().getAddress().setAddressLine4("");
    request.getClientDetails().getAddress().setCounty("");
    request.getClientDetails().getAddress().setPostCode(" s.w.1a - 2aa ");
    request.setReasonForReapplication("");
    String normalizedNiNumber = niNumber.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    String applicationId = "b2c3d4e5-f6a7-8901-bcde-f12345678901";
    DATASTORE.stubFor(
        WireMock.patch(
                urlPathEqualTo("/api/v0/applications/" + applicationId + ":update-scoping-data"))
            .willReturn(WireMock.noContent()));

    DATASTORE.stubFor(
        WireMock.post(urlPathEqualTo("/api/v0/applications:start-application"))
            .willReturn(
                okJson(
                    """
                    {
                        "id": "%s",
                        "individualLegalAidNumber": "%s",
                        "providerFirmCode": "123456",
                        "providerOfficeCode": "%s",
                        "client": {
                            "individualLegalAidNumber": "%s",
                            "firstName": "Joe",
                            "lastName": "Bloggs",
                            "dateOfBirth": "1990-01-01",
                            "niNumber": "%s",
                            "noFixedAbode": false,
                            "address": {
                                "addressLine1": "10 Downing Street",
                                "addressLine2": "Prime ministers address",
                                "postCode": "SW1A 2AA",
                                "townOrCity": "London",
                                "country": "GB"
                            },
                            "createdAt": "2026-08-09T00:00:00Z",
                            "modifiedAt": "2026-08-09T00:00:00Z"
                        },
                        "applicationState": "DRAFT",
                        "applicationType": "RCW",
                        "eTag": 0,
                        "createdAt": "2026-08-09T00:00:00Z",
                        "createdBy": "Random User",
                        "modifiedAt": "2026-08-09T00:00:00Z",
                        "modifiedBy": "Random User"
                    }
                    """
                        .formatted(
                            applicationId,
                            applicationId,
                            TestJwtConfig.AUTHORIZED_OFFICE_CODE,
                            applicationId,
                            normalizedNiNumber))));

    mockMvc
        .perform(
            post("/api/v1/applications")
                .withBearerReadToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andExpect(
            header()
                .string(
                    "Location",
                    org.hamcrest.Matchers.endsWith("/api/v1/applications/" + applicationId)))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(applicationId));

    DATASTORE.verify(
        postRequestedFor(urlPathEqualTo("/api/v0/applications:start-application"))
            .withHeader("Authorization", equalTo("Bearer obo-access-token"))
            .withHeader("X-Authorization", equalTo("Bearer " + TestJwtConfig.ACCESS_TOKEN))
            .withHeader("X-Correlation-ID", matching(UUID_REGEX))
            .withHeader("X-Service-Name", equalTo(SERVICE_NAME))
            .withRequestBody(
                equalToJson(
                    """
                    {
                        "client": {
                            "firstName": "Joe",
                            "lastName": "Bloggs",
                            "dateOfBirth": "1990-01-01",
                            "nationalInsuranceNumber": "%s",
                            "noFixedAbode": false,
                            "createAddressCommand": {
                                "addressLine1": "10 Downing Street",
                                "addressLine2": "Prime ministers address",
                                "addressLine3": "",
                                "addressLine4": "",
                                "postCode": "SW1A2AA",
                                "county": "",
                                "townOrCity": "London",
                                "country": "GB"
                            }
                        },
                        "ufn": null,
                        "applicationType": "RCW",
                        "providerOfficeCode": "%s"
                    }
                    """
                        .formatted(normalizedNiNumber, TestJwtConfig.AUTHORIZED_OFFICE_CODE))));

    DATASTORE.verify(
        patchRequestedFor(
                urlPathEqualTo("/api/v0/applications/" + applicationId + ":update-scoping-data"))
            .withHeader("Authorization", equalTo("Bearer obo-access-token"))
            .withHeader("X-Authorization", equalTo("Bearer " + TestJwtConfig.ACCESS_TOKEN))
            .withRequestBody(
                equalToJson(
                    """
                    {
                        "eTag": 0,
                        "scopingQuestions": {
                            "priorLegalAid": "yesSameMatter",
                            "familyLawClassification": "private",
                            "needsAdviceOnEUOrInternationalMaintenance": true
                        }
                    }
                    """)));
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
  void shouldReturnSameNotFoundForMissingAndForbiddenDetailsPreflight() throws Exception {
    String id = java.util.UUID.randomUUID().toString();
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
  void shouldHideCompletedApplicationBeforeCheckingCompletionWhenOfficeIsUnauthorized()
      throws Exception {
    String id = java.util.UUID.randomUUID().toString();
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
    String id = java.util.UUID.randomUUID().toString();
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
    String id = java.util.UUID.randomUUID().toString();
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
    String id = java.util.UUID.randomUUID().toString();
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

  @Test
  void shouldPreserveDatastore503RetryForMeansUpdates() throws Exception {
    String id = java.util.UUID.randomUUID().toString();
    String applicationPath = "/api/v0/applications/" + id;
    String meansPath = applicationPath + ":update-means-data";
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo(applicationPath))
            .willReturn(
                okJson(
                    """
                    {
                      "id": "%s",
                      "providerOfficeCode": "%s",
                      "applicationState": "DRAFT",
                      "eTag": 4
                    }
                    """
                        .formatted(id, TestJwtConfig.AUTHORIZED_OFFICE_CODE))));
    DATASTORE.stubFor(
        WireMock.put(urlPathEqualTo(meansPath))
            .inScenario("means update retries on 503")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(WireMock.aResponse().withStatus(503))
            .willSetStateTo("retry succeeds"));
    DATASTORE.stubFor(
        WireMock.put(urlPathEqualTo(meansPath))
            .inScenario("means update retries on 503")
            .whenScenarioStateIs("retry succeeds")
            .willReturn(WireMock.noContent()));

    mockMvc
        .perform(
            put("/api/v1/applications/{id}/means", id)
                .withBearerWriteToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"data\":{},\"result\":{}}"))
        .andExpect(status().isNoContent());

    DATASTORE.verify(1, getRequestedFor(urlPathEqualTo(applicationPath)));
    DATASTORE.verify(2, putRequestedFor(urlPathEqualTo(meansPath)));
  }

  private org.springframework.test.web.servlet.ResultActions performValidDetailsPut(String id)
      throws Exception {
    return mockMvc.perform(
        put("/api/v1/applications/{id}/details", id)
            .withBearerWriteToken()
            .header("If-Match", "\"31\"")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_DETAILS_PUT_BODY));
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

  @Test
  void shouldUpdateApplicationMeans_fetchesETagAndPersistsDataAndResult() throws Exception {
    String applicationId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + applicationId))
            .willReturn(
                okJson(
                    "{\"id\": \"%s\", \"eTag\": 5, \"providerOfficeCode\": \"%s\"}"
                        .formatted(applicationId, TestJwtConfig.AUTHORIZED_OFFICE_CODE))));
    DATASTORE.stubFor(
        WireMock.put(urlPathEqualTo("/api/v0/applications/" + applicationId + ":update-means-data"))
            .willReturn(WireMock.noContent()));

    mockMvc
        .perform(
            put("/api/v1/applications/%s/means".formatted(applicationId))
                .withBearerWriteToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"data": {"level_of_help": "controlled"}, "result": {"indication": true}}
                    """))
        .andExpect(status().isNoContent());

    DATASTORE.verify(
        WireMock.putRequestedFor(
                urlPathEqualTo("/api/v0/applications/" + applicationId + ":update-means-data"))
            .withHeader("Authorization", equalTo("Bearer obo-access-token"))
            .withHeader("X-Authorization", equalTo("Bearer " + TestJwtConfig.ACCESS_TOKEN))
            .withRequestBody(
                equalToJson(
                    """
                    {
                        "eTag": 5,
                        "data": {
                          "additional_property_owned" : null,
                          "adult_dependants" : null,
                          "adult_dependants_count" : null,
                          "aggregated_means" : null,
                          "asylum_support" : null,
                          "child_dependants" : null,
                          "child_dependants_count" : null,
                          "childcare_payments_conditional_value" : null,
                          "childcare_payments_frequency" : null,
                          "childcare_payments_relevant" : null,
                          "childcare_payments_value" : null,
                          "client_age" : null,
                          "combined_frequency" : null,
                          "controlled_legal_representation" : null,
                          "dependants_get_income" : null,
                          "domestic_abuse_applicant" : null,
                          "employment_status" : null,
                          "feature_flags" : null,
                          "friends_or_family_conditional_value" : null,
                          "friends_or_family_frequency" : null,
                          "friends_or_family_relevant" : null,
                          "house_in_dispute" : null,
                          "house_value" : null,
                          "housing_benefit_frequency" : null,
                          "housing_benefit_relevant" : null,
                          "housing_benefit_value" : null,
                          "housing_loan_payments" : null,
                          "housing_payments" : null,
                          "housing_payments_frequency" : null,
                          "housing_payments_loan_frequency" : null,
                          "immigration_or_asylum" : null,
                          "immigration_or_asylum_type" : null,
                          "immigration_or_asylum_type_upper_tribunal" : null,
                          "investments" : null,
                          "investments_in_dispute" : null,
                          "investments_relevant" : null,
                          "legal_aid_payments_conditional_value" : null,
                          "legal_aid_payments_frequency" : null,
                          "legal_aid_payments_relevant" : null,
                          "legal_aid_payments_value" : null,
                          "level_of_help" : "controlled",
                          "maintenance_conditional_value" : null,
                          "maintenance_frequency" : null,
                          "maintenance_payments_conditional_value" : null,
                          "maintenance_payments_frequency" : null,
                          "maintenance_payments_relevant" : null,
                          "maintenance_payments_value" : null,
                          "maintenance_relevant" : null,
                          "mortgage" : null,
                          "other_conditional_value" : null,
                          "other_relevant" : null,
                          "partner" : null,
                          "partner_additional_property_owned" : null,
                          "partner_childcare_payments_conditional_value" : null,
                          "partner_childcare_payments_frequency" : null,
                          "partner_childcare_payments_relevant" : null,
                          "partner_childcare_payments_value" : null,
                          "partner_employment_status" : null,
                          "partner_friends_or_family_conditional_value" : null,
                          "partner_friends_or_family_frequency" : null,
                          "partner_friends_or_family_relevant" : null,
                          "partner_investments" : null,
                          "partner_investments_relevant" : null,
                          "partner_legal_aid_payments_conditional_value" : null,
                          "partner_legal_aid_payments_frequency" : null,
                          "partner_legal_aid_payments_relevant" : null,
                          "partner_legal_aid_payments_value" : null,
                          "partner_maintenance_conditional_value" : null,
                          "partner_maintenance_frequency" : null,
                          "partner_maintenance_payments_conditional_value" : null,
                          "partner_maintenance_payments_frequency" : null,
                          "partner_maintenance_payments_relevant" : null,
                          "partner_maintenance_payments_value" : null,
                          "partner_maintenance_relevant" : null,
                          "partner_other_conditional_value" : null,
                          "partner_other_relevant" : null,
                          "partner_over_60" : null,
                          "partner_pension_conditional_value" : null,
                          "partner_pension_frequency" : null,
                          "partner_pension_relevant" : null,
                          "partner_property_or_lodger_conditional_value" : null,
                          "partner_property_or_lodger_frequency" : null,
                          "partner_property_or_lodger_relevant" : null,
                          "partner_receives_benefits" : null,
                          "partner_student_finance_conditional_value" : null,
                          "partner_student_finance_relevant" : null,
                          "partner_valuables" : null,
                          "partner_valuables_relevant" : null,
                          "passporting" : null,
                          "pending" : null,
                          "pension_conditional_value" : null,
                          "pension_frequency" : null,
                          "pension_relevant" : null,
                          "percentage_owned" : null,
                          "property_landlord" : null,
                          "property_or_lodger_conditional_value" : null,
                          "property_or_lodger_frequency" : null,
                          "property_or_lodger_relevant" : null,
                          "property_owned" : null,
                          "receives_benefits" : null,
                          "regular_income" : null,
                          "rent" : null,
                          "shared_ownership_mortgage" : null,
                          "student_finance_conditional_value" : null,
                          "student_finance_relevant" : null,
                          "under_eighteen_assets" : null,
                          "valuables" : null,
                          "valuables_in_dispute" : null,
                          "valuables_relevant" : null,
                          "vehicle_owned" : null
                        },
                        "result": {"indication": true}
                    }
                    """)));
  }

  @Test
  void shouldReturnNotFound_whenUpdatingMeansForAnApplicationThatDoesNotExist() throws Exception {
    String applicationId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + applicationId))
            .willReturn(WireMock.notFound()));

    mockMvc
        .perform(
            put("/api/v1/applications/%s/means".formatted(applicationId))
                .withBearerWriteToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"data": {}, "result": {}}
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturnForbidden_whenUpdatingMeansForApplicationInAnotherOffice() throws Exception {
    String applicationId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + applicationId))
            .willReturn(
                okJson(
                    "{\"id\": \"%s\", \"eTag\": 5, \"providerOfficeCode\": \"OTHER-OFFICE\"}"
                        .formatted(applicationId))));

    mockMvc
        .perform(
            put("/api/v1/applications/%s/means".formatted(applicationId))
                .withBearerWriteToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"data": {}, "result": {}}
                    """))
        .andExpect(status().isForbidden());

    DATASTORE.verify(
        0,
        WireMock.putRequestedFor(
            urlPathEqualTo("/api/v0/applications/" + applicationId + ":update-means-data")));
  }

  @Test
  void shouldRetryOnceThenReturnConflict_whenDatastoreEtagMismatchPersists() throws Exception {
    String applicationId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + applicationId))
            .willReturn(
                okJson(
                    "{\"id\": \"%s\", \"eTag\": 5, \"providerOfficeCode\": \"%s\"}"
                        .formatted(applicationId, TestJwtConfig.AUTHORIZED_OFFICE_CODE))));
    DATASTORE.stubFor(
        WireMock.put(urlPathEqualTo("/api/v0/applications/" + applicationId + ":update-means-data"))
            .willReturn(WireMock.aResponse().withStatus(409)));

    mockMvc
        .perform(
            put("/api/v1/applications/%s/means".formatted(applicationId))
                .withBearerWriteToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"data": {}, "result": {}}
                    """))
        .andExpect(status().isConflict());

    DATASTORE.verify(2, getRequestedFor(urlPathEqualTo("/api/v0/applications/" + applicationId)));
    DATASTORE.verify(
        2,
        putRequestedFor(
            urlPathEqualTo("/api/v0/applications/" + applicationId + ":update-means-data")));
  }

  @Test
  void shouldReturnConflict_whenApplicationAlreadyRecorded() throws Exception {
    String applicationId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + applicationId))
            .willReturn(
                okJson(
                    """
                    {
                      "id": "%s",
                      "eTag": 5,
                      "providerOfficeCode": "%s",
                      "applicationState": "COMPLETED"
                    }
                    """
                        .formatted(applicationId, TestJwtConfig.AUTHORIZED_OFFICE_CODE))));

    mockMvc
        .perform(
            put("/api/v1/applications/%s/means".formatted(applicationId))
                .withBearerWriteToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"data": {}, "result": {}}
                    """))
        .andExpect(status().isConflict());

    DATASTORE.verify(
        0,
        putRequestedFor(
            urlPathEqualTo("/api/v0/applications/" + applicationId + ":update-means-data")));
  }

  @Test
  void shouldReturnBadRequest_whenDatastoreRejectsTheUpdateAsInvalid() throws Exception {
    String applicationId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + applicationId))
            .willReturn(
                okJson(
                    "{\"id\": \"%s\", \"eTag\": 5, \"providerOfficeCode\": \"%s\"}"
                        .formatted(applicationId, TestJwtConfig.AUTHORIZED_OFFICE_CODE))));
    DATASTORE.stubFor(
        WireMock.put(urlPathEqualTo("/api/v0/applications/" + applicationId + ":update-means-data"))
            .willReturn(WireMock.aResponse().withStatus(400)));

    mockMvc
        .perform(
            put("/api/v1/applications/%s/means".formatted(applicationId))
                .withBearerWriteToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"data": {}, "result": {}}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturnBadGateway_whenDatastoreReturnsAServerError() throws Exception {
    String applicationId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + applicationId))
            .willReturn(
                okJson(
                    "{\"id\": \"%s\", \"eTag\": 5, \"providerOfficeCode\": \"%s\"}"
                        .formatted(applicationId, TestJwtConfig.AUTHORIZED_OFFICE_CODE))));
    DATASTORE.stubFor(
        WireMock.put(urlPathEqualTo("/api/v0/applications/" + applicationId + ":update-means-data"))
            .willReturn(WireMock.aResponse().withStatus(500)));

    mockMvc
        .perform(
            put("/api/v1/applications/%s/means".formatted(applicationId))
                .withBearerWriteToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"data": {}, "result": {}}
                    """))
        .andExpect(status().isBadGateway());
  }

  @Test
  void shouldReturnServiceUnavailable_whenDatastoreCannotBeReached() throws Exception {
    String applicationId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + applicationId))
            .willReturn(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    mockMvc
        .perform(
            put("/api/v1/applications/%s/means".formatted(applicationId))
                .withBearerWriteToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"data": {}, "result": {}}
                    """))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void shouldUpdateApplicationEvidence_fetchesETagAndPersistsEvidenceFields() throws Exception {
    String applicationId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + applicationId))
            .willReturn(
                okJson(
                    "{\"id\": \"%s\", \"eTag\": 3, \"providerOfficeCode\": \"%s\"}"
                        .formatted(applicationId, TestJwtConfig.AUTHORIZED_OFFICE_CODE))));
    DATASTORE.stubFor(
        WireMock.put(urlPathEqualTo("/api/v0/applications/" + applicationId + ":update-evidence"))
            .willReturn(WireMock.noContent()));

    mockMvc
        .perform(
            put("/api/v1/applications/%s/evidence".formatted(applicationId))
                .withBearerWriteToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "evidenceExemptionCode": "EXEMPT",
                      "evidenceExemptionReason": "reason",
                      "incomeEvidenceChecklist": {"payslips": true},
                      "expenditureCapitalEvidenceChecklist": {"bankStatements": true}
                    }
                    """))
        .andExpect(status().isNoContent());

    DATASTORE.verify(
        WireMock.putRequestedFor(
                urlPathEqualTo("/api/v0/applications/" + applicationId + ":update-evidence"))
            .withHeader("X-Authorization", equalTo("Bearer " + TestJwtConfig.ACCESS_TOKEN))
            .withRequestBody(
                equalToJson(
                    """
                    {
                      "eTag": 3,
                      "evidenceExemptionCode": "EXEMPT",
                      "evidenceExemptionReason": "reason",
                      "incomeEvidenceChecklist": {"payslips": true},
                      "expenditureCapitalEvidenceChecklist": {"bankStatements": true}
                    }
                    """)));
  }

  @Test
  void shouldReturnNotFound_whenUpdatingEvidenceForAnApplicationThatDoesNotExist()
      throws Exception {
    String applicationId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + applicationId))
            .willReturn(WireMock.notFound()));

    mockMvc
        .perform(
            put("/api/v1/applications/%s/evidence".formatted(applicationId))
                .withBearerWriteToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturnForbidden_whenUpdatingEvidenceForApplicationInAnotherOffice() throws Exception {
    String applicationId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + applicationId))
            .willReturn(
                okJson(
                    "{\"id\": \"%s\", \"eTag\": 3, \"providerOfficeCode\": \"OTHER-OFFICE\"}"
                        .formatted(applicationId))));

    mockMvc
        .perform(
            put("/api/v1/applications/%s/evidence".formatted(applicationId))
                .withBearerWriteToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());

    DATASTORE.verify(
        0,
        WireMock.putRequestedFor(
            urlPathEqualTo("/api/v0/applications/" + applicationId + ":update-evidence")));
  }

  @Test
  void shouldReturnConflict_whenEvidenceEtagMismatch() throws Exception {
    String applicationId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + applicationId))
            .willReturn(
                okJson(
                    "{\"id\": \"%s\", \"eTag\": 3, \"providerOfficeCode\": \"%s\"}"
                        .formatted(applicationId, TestJwtConfig.AUTHORIZED_OFFICE_CODE))));
    DATASTORE.stubFor(
        WireMock.put(urlPathEqualTo("/api/v0/applications/" + applicationId + ":update-evidence"))
            .willReturn(WireMock.aResponse().withStatus(409)));

    mockMvc
        .perform(
            put("/api/v1/applications/%s/evidence".formatted(applicationId))
                .withBearerWriteToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isConflict());

    DATASTORE.verify(
        1,
        putRequestedFor(
            urlPathEqualTo("/api/v0/applications/" + applicationId + ":update-evidence")));
  }

  @Test
  void shouldUpdateApplicationDeclaration_fetchesETagAndPersistsDeclarationData() throws Exception {
    String applicationId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    String updateDeclarationPath =
        "/api/v0/applications/" + applicationId + ":update-declaration-data";
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + applicationId))
            .willReturn(
                okJson(
                    """
                    {
                        "id": "%s",
                        "eTag": 5,
                        "providerOfficeCode": "%s",
                        "applicationState": "DRAFT"
                    }
                    """
                        .formatted(applicationId, TestJwtConfig.AUTHORIZED_OFFICE_CODE))));
    DATASTORE.stubFor(
        WireMock.patch(urlPathEqualTo(updateDeclarationPath)).willReturn(WireMock.noContent()));

    mockMvc
        .perform(
            put("/api/v1/applications/%s/declaration".formatted(applicationId))
                .withBearerWriteToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"declarationConfirmation": true, "dateSigned": "2026-08-14"}
                    """))
        .andExpect(status().isNoContent());

    DATASTORE.verify(
        patchRequestedFor(urlPathEqualTo(updateDeclarationPath))
            .withHeader("Authorization", equalTo("Bearer obo-access-token"))
            .withHeader("X-Authorization", equalTo("Bearer " + TestJwtConfig.ACCESS_TOKEN))
            .withRequestBody(
                equalToJson(
                    """
                    {
                        "eTag": 5,
                        "declarationConfirmation": true,
                        "dateSigned": "2026-08-14"
                    }
                    """)));
  }

  @Test
  void shouldReturnForbidden_whenUpdatingDeclarationForApplicationInAnotherOffice()
      throws Exception {
    String applicationId = java.util.UUID.randomUUID().toString();
    String applicationPath = "/api/v0/applications/" + applicationId;
    final String declarationPath = applicationPath + ":update-declaration-data";
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo(applicationPath))
            .willReturn(
                okJson(
                    """
                    {
                        "id": "%s",
                        "eTag": 5,
                        "providerOfficeCode": "OTHER-OFFICE",
                        "applicationState": "DRAFT"
                    }
                    """
                        .formatted(applicationId))));

    mockMvc
        .perform(
            put("/api/v1/applications/{id}/declaration", applicationId)
                .withBearerWriteToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"declarationConfirmation\":true,\"dateSigned\":\"2026-08-14\"}"))
        .andExpect(status().isForbidden());

    DATASTORE.verify(1, getRequestedFor(urlPathEqualTo(applicationPath)));
    DATASTORE.verify(0, patchRequestedFor(urlPathEqualTo(declarationPath)));
  }

  @Test
  void shouldRetryOnceThenReturnConflict_whenDeclarationUpdateEtagMismatchPersists()
      throws Exception {
    String applicationId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    String updateDeclarationPath =
        "/api/v0/applications/" + applicationId + ":update-declaration-data";
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + applicationId))
            .willReturn(
                okJson(
                    """
                    {
                        "id": "%s",
                        "eTag": 5,
                        "providerOfficeCode": "%s",
                        "applicationState": "DRAFT"
                    }
                    """
                        .formatted(applicationId, TestJwtConfig.AUTHORIZED_OFFICE_CODE))));
    DATASTORE.stubFor(
        WireMock.patch(urlPathEqualTo(updateDeclarationPath))
            .willReturn(WireMock.aResponse().withStatus(409)));

    mockMvc
        .perform(
            put("/api/v1/applications/%s/declaration".formatted(applicationId))
                .withBearerWriteToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"declarationConfirmation": true, "dateSigned": "2026-08-14"}
                    """))
        .andExpect(status().isConflict());

    DATASTORE.verify(2, getRequestedFor(urlPathEqualTo("/api/v0/applications/" + applicationId)));
    DATASTORE.verify(2, patchRequestedFor(urlPathEqualTo(updateDeclarationPath)));
  }

  @Test
  void shouldUpdateApplicationStatus_fetchesETagAndPersistsStatus() throws Exception {
    String applicationId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + applicationId))
            .willReturn(
                okJson(
                    """
                    {
                        "id": "%s",
                        "eTag": 5,
                        "providerOfficeCode": "%s",
                        "applicationState": "DRAFT"
                    }
                    """
                        .formatted(applicationId, TestJwtConfig.AUTHORIZED_OFFICE_CODE))));
    DATASTORE.stubFor(
        WireMock.patch(
                urlPathEqualTo("/api/v0/applications/" + applicationId + ":update-application"))
            .willReturn(WireMock.noContent()));

    mockMvc
        .perform(
            patch("/api/v1/applications/%s/status".formatted(applicationId))
                .withBearerWriteToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"applicationState": "COMPLETED", "eTag": 5}
                    """))
        .andExpect(status().isNoContent());

    DATASTORE.verify(
        patchRequestedFor(
                urlPathEqualTo("/api/v0/applications/" + applicationId + ":update-application"))
            .withHeader("Authorization", equalTo("Bearer obo-access-token"))
            .withHeader("X-Authorization", equalTo("Bearer " + TestJwtConfig.ACCESS_TOKEN))
            .withRequestBody(
                equalToJson(
                    """
                    {
                        "eTag": 5,
                        "applicationState": "COMPLETED"
                    }
                    """)));
  }

  @Test
  void shouldReturnForbidden_whenUpdatingStatusForApplicationInAnotherOffice() throws Exception {
    String applicationId = java.util.UUID.randomUUID().toString();
    String applicationPath = "/api/v0/applications/" + applicationId;
    final String statusPath = applicationPath + ":update-application";
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo(applicationPath))
            .willReturn(
                okJson(
                    """
                    {
                        "id": "%s",
                        "eTag": 5,
                        "providerOfficeCode": "OTHER-OFFICE",
                        "applicationState": "DRAFT"
                    }
                    """
                        .formatted(applicationId))));

    mockMvc
        .perform(
            patch("/api/v1/applications/{id}/status", applicationId)
                .withBearerWriteToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"applicationState\":\"COMPLETED\",\"eTag\":5}"))
        .andExpect(status().isForbidden());

    DATASTORE.verify(1, getRequestedFor(urlPathEqualTo(applicationPath)));
    DATASTORE.verify(0, patchRequestedFor(urlPathEqualTo(statusPath)));
  }

  @Test
  void shouldRetryOnceThenReturnConflict_whenStatusUpdateEtagMismatchPersists() throws Exception {
    String applicationId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + applicationId))
            .willReturn(
                okJson(
                    """
                    {
                        "id": "%s",
                        "eTag": 5,
                        "providerOfficeCode": "%s",
                        "applicationState": "DRAFT"
                    }
                    """
                        .formatted(applicationId, TestJwtConfig.AUTHORIZED_OFFICE_CODE))));
    DATASTORE.stubFor(
        WireMock.patch(
                urlPathEqualTo("/api/v0/applications/" + applicationId + ":update-application"))
            .willReturn(WireMock.aResponse().withStatus(409)));

    mockMvc
        .perform(
            patch("/api/v1/applications/%s/status".formatted(applicationId))
                .withBearerWriteToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"applicationState": "COMPLETED", "eTag": 5}
                    """))
        .andExpect(status().isConflict());

    DATASTORE.verify(2, getRequestedFor(urlPathEqualTo("/api/v0/applications/" + applicationId)));
    DATASTORE.verify(
        2,
        patchRequestedFor(
            urlPathEqualTo("/api/v0/applications/" + applicationId + ":update-application")));
  }

  @Test
  void shouldReturnConflict_whenUpdatingStatusForAnAlreadyRecordedApplication() throws Exception {
    String applicationId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + applicationId))
            .willReturn(
                okJson(
                    """
                    {
                      "id": "%s",
                      "eTag": 5,
                      "providerOfficeCode": "%s",
                      "applicationState": "COMPLETED"
                    }
                    """
                        .formatted(applicationId, TestJwtConfig.AUTHORIZED_OFFICE_CODE))));

    mockMvc
        .perform(
            patch("/api/v1/applications/%s/status".formatted(applicationId))
                .withBearerWriteToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"applicationState": "COMPLETED", "eTag": 5}
                    """))
        .andExpect(status().isConflict());

    DATASTORE.verify(
        0,
        patchRequestedFor(
            urlPathEqualTo("/api/v0/applications/" + applicationId + ":update-application")));
  }
}
