package uk.gov.justice.laa.rcw.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateApplicationCommand;
import uk.gov.justice.laa.rcw.SpringBootMicroserviceApplication;
import uk.gov.justice.laa.rcw.constants.CorrelationConstants;
import uk.gov.justice.laa.rcw.mapper.ApplicationMapper;
import uk.gov.justice.laa.rcw.model.PriorLegalAid;
import uk.gov.justice.laa.rcw.model.UpdateAddressRequestBody;
import uk.gov.justice.laa.rcw.model.UpdateApplicationDetailsRequestBody;
import uk.gov.justice.laa.rcw.model.UpdateClientDetailsRequestBody;
import uk.gov.justice.laa.rcw.utils.BaseIntegrationTest;
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
  void clearRequestContext() {
    SecurityContextHolder.clearContext();
    MDC.remove(CorrelationConstants.CORRELATION_ID_LOG_KEY);
    DATASTORE.resetRequests();
  }

  @Test
  void shouldSendSparseEditJsonAndReturnDownstreamEtag() {
    authenticateRequest();
    MDC.put(CorrelationConstants.CORRELATION_ID_LOG_KEY, CORRELATION_ID);

    DATASTORE.stubFor(
        WireMock.patch(
                urlPathEqualTo("/api/v0/applications/" + APPLICATION_ID + ":edit-application"))
            .willReturn(WireMock.aResponse().withStatus(204).withHeader("ETag", "\"32\"")));

    ResponseEntity<Void> response =
        applicationGateway.editApplication(
            APPLICATION_ID, applicationMapper.toEditApplicationCommand(detailsRequest(), 31L));

    assertThat(response.getStatusCode().value()).isEqualTo(204);
    assertThat(response.getHeaders().getETag()).isEqualTo("\"32\"");
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
    Jwt jwt =
        Jwt.withTokenValue(TestJwtConfig.ACCESS_TOKEN)
            .header("alg", "none")
            .claim("sub", "test-user")
            .build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
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
