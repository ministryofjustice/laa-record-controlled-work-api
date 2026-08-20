package uk.gov.justice.laa.rcw.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
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
import java.util.Map;
import lombok.experimental.ExtensionMethod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import uk.gov.justice.laa.rcw.SpringBootMicroserviceApplication;
import uk.gov.justice.laa.rcw.generator.CreateApplicationRequestGenerator;
import uk.gov.justice.laa.rcw.model.CreateApplicationRequestBody;
import uk.gov.justice.laa.rcw.utils.BaseIntegrationTest;
import uk.gov.justice.laa.rcw.utils.TestJwtConfig;
import uk.gov.justice.laa.rcw.utils.extensions.MockHttpServletRequestBuilderExtensions;

@SpringBootTest(classes = SpringBootMicroserviceApplication.class)
@ExtensionMethod(MockHttpServletRequestBuilderExtensions.class)
class ApplicationsControllerIntegrationTest extends BaseIntegrationTest {

  private static final WireMockServer DATASTORE =
      new WireMockServer(WireMockConfiguration.options().dynamicPort());

  static {
    DATASTORE.start();
  }

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
  void resetDatastoreApplicationsStub() {
    DATASTORE.resetRequests();
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
                          "modifiedAt": "2024-01-01T10:00:00Z"
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
                .withBearerReadToken())
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$[0].id").value("a1b2c3d4-e5f6-7890-abcd-ef1234567890"))
        .andExpect(jsonPath("$[0].name").value("Jane Doe"))
        .andExpect(jsonPath("$[0].applicationRefNumber").value("REF123"));
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
                .withBearerReadToken())
        .andExpect(status().isOk());

    DATASTORE.verify(
        getRequestedFor(urlPathEqualTo("/api/v0/applications"))
            .withHeader("Authorization", equalTo("Bearer obo-access-token"))
            .withHeader("X-Authorization", equalTo("Bearer " + TestJwtConfig.ACCESS_TOKEN)));
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
                        "providerOfficeCode": "22439e72-68d3-4770-b435-c352d883d21e",
                        "referenceNumber": "CW-111111",
                        "scopingQuestions": {
                            "priorLegalAid": "same_matter"
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
                        .formatted(applicationId))));

    mockMvc
        .perform(get("/api/v1/applications/%s".formatted(applicationId)).withBearerReadToken())
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(
            jsonPath("$.individualLegalAidNumber").value("ebd50ba0-9ed9-4003-83a8-c11ac07d9e32"))
        .andExpect(jsonPath("$.applicationRefNumber").value("CW-111111"))
        .andExpect(jsonPath("$.scopingQuestions.priorLegalAid").value("same_matter"))
        .andExpect(jsonPath("$.declaration.id").value("d4e5f6a7-b8c9-0123-def1-234567890123"))
        .andExpect(jsonPath("$.declaration.clientDeclarationStatus").doesNotExist())
        .andExpect(jsonPath("$.evidence.evidenceStatus").doesNotExist())
        .andExpect(jsonPath("$.eligibility.data.level_of_help").value("controlled"))
        .andExpect(jsonPath("$.eligibility.result.indication").value(true));
  }

  @Test
  void shouldReturnNotFound_whenGettingApplicationThatDoesNotExist() throws Exception {
    String applicationId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    DATASTORE.stubFor(
        WireMock.get(urlPathEqualTo("/api/v0/applications/" + applicationId))
            .willReturn(WireMock.notFound()));

    mockMvc
        .perform(get("/api/v1/applications/%s".formatted(applicationId)).withBearerReadToken())
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldCreateApplication() throws Exception {

    CreateApplicationRequestBody request =
        CreateApplicationRequestGenerator.createWithName(
            builder ->
                builder
                    .providerOfficeCode(TestJwtConfig.AUTHORIZED_OFFICE_CODE)
                    .scopingQuestions(Map.of("priorLegalAid", "same_matter")));
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
                            "niNumber": "AB123456C",
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
                            applicationId))));

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
            .withRequestBody(
                equalToJson(
                    """
                    {
                        "client": {
                            "firstName": "Joe",
                            "lastName": "Bloggs",
                            "dateOfBirth": "1990-01-01",
                            "nationalInsuranceNumber": "AB123456C",
                            "noFixedAbode": false,
                            "createAddressCommand": {
                                "addressLine1": "10 Downing Street",
                                "addressLine2": "Prime ministers address",
                                "addressLine3": null,
                                "addressLine4": null,
                                "postCode": "SW1A 2AA",
                                "county": null,
                                "townOrCity": "London",
                                "country": "GB"
                            }
                        },
                        "applicationType": "RCW",
                        "providerOfficeCode": "%s"
                    }
                    """
                        .formatted(TestJwtConfig.AUTHORIZED_OFFICE_CODE))));

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
                            "priorLegalAid": "same_matter"
                        }
                    }
                    """)));
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
                        "data": {"level_of_help": "controlled"},
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
