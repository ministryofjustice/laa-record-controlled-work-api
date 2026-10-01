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
import lombok.experimental.ExtensionMethod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import uk.gov.justice.laa.rcw.SpringBootMicroserviceApplication;
import uk.gov.justice.laa.rcw.generator.CreateApplicationRequestGenerator;
import uk.gov.justice.laa.rcw.model.CreateApplicationRequestBody;
import uk.gov.justice.laa.rcw.model.PriorLegalAid;
import uk.gov.justice.laa.rcw.model.ScopingQuestions;
import uk.gov.justice.laa.rcw.utils.BaseIntegrationTest;
import uk.gov.justice.laa.rcw.utils.TestJwtConfig;
import uk.gov.justice.laa.rcw.utils.extensions.MockHttpServletRequestBuilderExtensions;

@SpringBootTest(classes = SpringBootMicroserviceApplication.class)
@ExtensionMethod(MockHttpServletRequestBuilderExtensions.class)
class ApplicationsControllerIntegrationTest extends BaseIntegrationTest {

  private static final String SERVICE_NAME = "laa-record-controlled-work-api";
  private static final String UUID_REGEX =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
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
  void shouldFailDetailsPutClosedWithoutDatastoreRequests() throws Exception {
    String id = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    mockMvc
        .perform(
            put("/api/v1/applications/{id}/details", id)
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
                                                "niNumber": null,
                                                "hasFixedAddress": false,
                                                "address": null
                                            }
                                        }
                                        """))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().doesNotExist("ETag"))
        .andExpect(jsonPath("$.status").value(503))
        .andExpect(jsonPath("$.reason").value("APPLICATION_DETAILS_UNAVAILABLE"));

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
        ",\"eTag\":9223372036854775808",
        ",\"eTag\":1.5",
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
                            "priorLegalAid": "yesSameMatter"
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
                      "providerOfficeCode": "%s"
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

  @ParameterizedTest
  @ValueSource(strings = {"BG123456C", "AO123456C", "js101010D", "AB123456s"})
  void shouldReturnBadRequest_whenNiNumberDoesNotMatchUkFormat(String niNumber) throws Exception {
    CreateApplicationRequestBody request = CreateApplicationRequestGenerator.createWithName(null);
    request.setProviderOfficeCode(TestJwtConfig.AUTHORIZED_OFFICE_CODE);
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
        "WZ123456D"
      })
  void shouldCreateApplication(String niNumber) throws Exception {

    CreateApplicationRequestBody request =
        CreateApplicationRequestGenerator.createWithName(
            builder ->
                builder
                    .providerOfficeCode(TestJwtConfig.AUTHORIZED_OFFICE_CODE)
                    .scopingQuestions(
                        new ScopingQuestions().priorLegalAid(PriorLegalAid.YES_SAME_MATTER)));
    request.getClientDetails().setNiNumber(niNumber);
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
                            niNumber))));

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
                                "addressLine3": null,
                                "addressLine4": null,
                                "postCode": "SW1A 2AA",
                                "county": null,
                                "townOrCity": "London",
                                "country": "GB"
                            }
                        },
                        "applicationType": "RCW",
                        "providerOfficeCode": "%s",
                        "ufn": null
                    }
                    """
                        .formatted(niNumber, TestJwtConfig.AUTHORIZED_OFFICE_CODE))));

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
                            "priorLegalAid": "yesSameMatter"
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
