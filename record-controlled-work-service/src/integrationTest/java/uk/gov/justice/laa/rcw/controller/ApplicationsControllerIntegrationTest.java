package uk.gov.justice.laa.rcw.controller;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import lombok.experimental.ExtensionMethod;
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
    mockMvc
        .perform(
            get("/api/v1/applications/a1b2c3d4-e5f6-7890-abcd-ef1234567890").withBearerReadToken())
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(
            jsonPath("$.individualLegalAidNumber").value("ebd50ba0-9ed9-4003-83a8-c11ac07d9e32"));
  }

  @Test
  void shouldCreateApplication() throws Exception {

    CreateApplicationRequestBody request = CreateApplicationRequestGenerator.createWithName(null);

    mockMvc
        .perform(
            post("/api/v1/applications")
                .withBearerReadToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated());
  }
}
