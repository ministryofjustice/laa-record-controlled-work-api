package uk.gov.justice.laa.rcw.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import uk.gov.justice.laa.rcw.config.schemas.ApplicationRequestSchemaConfiguration;
import uk.gov.justice.laa.rcw.exception.ApplicationBadRequestException;
import uk.gov.justice.laa.rcw.exception.ApplicationConflictException;
import uk.gov.justice.laa.rcw.exception.ApplicationForbiddenException;
import uk.gov.justice.laa.rcw.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.rcw.exception.ApplicationUnavailableException;
import uk.gov.justice.laa.rcw.exception.ApplicationUpstreamErrorException;
import uk.gov.justice.laa.rcw.generator.ApplicationGenerator;
import uk.gov.justice.laa.rcw.generator.ApplicationOverviewGenerator;
import uk.gov.justice.laa.rcw.generator.CreateApplicationRequestGenerator;
import uk.gov.justice.laa.rcw.model.Application;
import uk.gov.justice.laa.rcw.model.ApplicationOverview;
import uk.gov.justice.laa.rcw.model.ApplicationState;
import uk.gov.justice.laa.rcw.model.CreateApplicationRequestBody;
import uk.gov.justice.laa.rcw.model.EligibilityData;
import uk.gov.justice.laa.rcw.model.EligibilityIndication;
import uk.gov.justice.laa.rcw.model.UpdateApplicationDetailsRequestBody;
import uk.gov.justice.laa.rcw.service.ApplicationCreationService;
import uk.gov.justice.laa.rcw.service.ApplicationDetailsService;
import uk.gov.justice.laa.rcw.service.ApplicationEvidenceService;
import uk.gov.justice.laa.rcw.service.ApplicationMeansService;
import uk.gov.justice.laa.rcw.service.ApplicationQueryService;
import uk.gov.justice.laa.rcw.service.ApplicationQueryService.VersionedApplication;
import uk.gov.justice.laa.rcw.service.ApplicationUpdateService;

@WebMvcTest(ApplicationController.class)
@Import(ApplicationRequestSchemaConfiguration.class)
@TestPropertySource(
    properties = {
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.security.oauth2.server.resource"
          + ".autoconfigure.OAuth2ResourceServerAutoConfiguration,"
          + "org.springframework.boot.security.oauth2.server.resource"
          + ".autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration,"
          + "org.springframework.boot.security.oauth2.client.autoconfigure.servlet"
          + ".OAuth2ClientWebSecurityAutoConfiguration"
    })
class ApplicationControllerTest {

  private static final String DETAILS_APPLICATION_ID = "b2c3d4e5-f6a7-8901-bcde-f12345678901";
  private static final String VALID_DETAILS_REQUEST =
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
      """;
  private static final String VALID_FIXED_ADDRESS_DETAILS_REQUEST =
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
              "hasFixedAddress": true,
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
      """;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ApplicationQueryService mockApplicationQueryService;
  @MockitoBean private ApplicationDetailsService mockApplicationDetailsService;
  @MockitoBean private ApplicationMeansService mockApplicationMeansService;
  @MockitoBean private ApplicationEvidenceService mockApplicationEvidenceService;
  @MockitoBean private ApplicationUpdateService mockApplicationUpdateService;
  @MockitoBean private ApplicationCreationService mockApplicationCreationService;

  @Test
  void getApplications_returnsOkStatusAndAllApplications() throws Exception {
    List<ApplicationOverview> applications =
        List.of(
            ApplicationOverviewGenerator.create(null),
            ApplicationOverviewGenerator.create(
                    b ->
                        b.id(UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"))
                            .name("Other Random Name")
                            .modifiedAt(OffsetDateTime.now()))
                .applicationRefNumber("CW-222222"));

    when(mockApplicationQueryService.getApplications(any(), any(), any(), any(), any()))
        .thenReturn(applications);

    mockMvc
        .perform(get("/api/v1/applications"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.*", hasSize(2)))
        .andExpect(jsonPath("$[0].id").value("a1b2c3d4-e5f6-7890-abcd-ef1234567890"))
        .andExpect(jsonPath("$[0].name").value("Random Name"))
        .andExpect(jsonPath("$[0].modifiedAt").exists())
        .andExpect(jsonPath("$[0].applicationRefNumber").value("CW-111111"))
        .andExpect(jsonPath("$[1].id").value("b2c3d4e5-f6a7-8901-bcde-f12345678901"))
        .andExpect(jsonPath("$[1].name").value("Other Random Name"))
        .andExpect(jsonPath("$[1].modifiedAt").exists())
        .andExpect(jsonPath("$[1].applicationRefNumber").value("CW-222222"));
  }

  @Test
  void getApplications_returnsEmptyListWhenNoApplications() throws Exception {
    when(mockApplicationQueryService.getApplications(any(), any(), any(), any(), any()))
        .thenReturn(List.of());

    mockMvc
        .perform(get("/api/v1/applications"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.*", hasSize(0)));
  }

  @Test
  void getApplications_forwardsEligibilityIndicationFilter() throws Exception {
    when(mockApplicationQueryService.getApplications(any(), any(), any(), any(), any()))
        .thenReturn(List.of());

    mockMvc
        .perform(
            get("/api/v1/applications")
                .param("status", "COMPLETED")
                .param("eligibilityIndication", "INELIGIBLE"))
        .andExpect(status().isOk());

    verify(mockApplicationQueryService)
        .getApplications(0, 25, null, ApplicationState.COMPLETED, EligibilityIndication.INELIGIBLE);
  }

  @ParameterizedTest
  @ValueSource(longs = {0L, 7L, Long.MAX_VALUE})
  void getApplicationWithId_returnsOkStatusAndApplicationResponse(long version) throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    Application applicationResponse =
        ApplicationGenerator.create(b -> b.id(applicationId).ufn("123456/123"));

    when(mockApplicationQueryService.getApplication(applicationId))
        .thenReturn(Optional.of(new VersionedApplication(applicationResponse, version)));

    mockMvc
        .perform(get("/api/v1/applications/%s".formatted(applicationId)))
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"" + version + "\""))
        .andExpect(jsonPath("$.eTag").doesNotExist())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value("b2c3d4e5-f6a7-8901-bcde-f12345678901"))
        .andExpect(jsonPath("$.applicationRefNumber").value("CW-111111"))
        .andExpect(jsonPath("$.ufn").value("123456/123"))
        .andExpect(
            jsonPath("$.individualLegalAidNumber").value("b2c3d4e5-f6a7-8901-bcde-f12345678901"))
        .andExpect(jsonPath("$.modifiedAt").exists())
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.providerOfficeCode").value("b2c3d4e5-f6a7-8901-bcde-f12345678901"))
        .andExpect(jsonPath("$.providerFirmCode").value("123456"))
        .andExpect(jsonPath("$.modifiedBy").value("Random User"))
        .andExpect(jsonPath("$.createdBy").value("Random User"));
  }

  @Test
  void getApplicationWithId_returnsOkStatus_whenUfnIsAbsent() throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    Application applicationResponse = ApplicationGenerator.create(b -> b.id(applicationId));

    when(mockApplicationQueryService.getApplication(applicationId))
        .thenReturn(Optional.of(new VersionedApplication(applicationResponse, 0L)));

    mockMvc
        .perform(get("/api/v1/applications/%s".formatted(applicationId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.applicationRefNumber").value("CW-111111"))
        .andExpect(jsonPath("$.ufn").doesNotExist());
  }

  @Test
  void getApplicationWithId_returnsNotFoundWhenApplicationDoesNotExist() throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    when(mockApplicationQueryService.getApplication(applicationId)).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/v1/applications/%s".formatted(applicationId)))
        .andExpect(status().isNotFound())
        .andExpect(header().doesNotExist("ETag"));
  }

  @Test
  void getApplicationWithId_returnsBadGatewayWithoutEtagForInvalidVersion() throws Exception {
    UUID id = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationQueryService.getApplication(id))
        .thenThrow(
            new ApplicationUpstreamErrorException(
                "Datastore returned an invalid application version",
                "DATASTORE_INVALID_APPLICATION_VERSION"));

    mockMvc
        .perform(get("/api/v1/applications/{id}", id))
        .andExpect(status().isBadGateway())
        .andExpect(header().doesNotExist("ETag"))
        .andExpect(jsonPath("$.status").value(502))
        .andExpect(jsonPath("$.reason").value("DATASTORE_INVALID_APPLICATION_VERSION"));
  }

  @Test
  void updateApplicationDetails_returnsNoContentAndNewEtag() {
    UUID id = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationDetailsService.updateApplicationDetails(eq(id), any(), eq(0L)))
        .thenReturn("\"1\"");
    ApplicationController controller =
        new ApplicationController(
            mockApplicationQueryService,
            mockApplicationDetailsService,
            mockApplicationMeansService,
            mockApplicationUpdateService,
            mockApplicationEvidenceService,
            mockApplicationCreationService);

    var response =
        controller.updateApplicationDetails(id, new UpdateApplicationDetailsRequestBody(), "\"0\"");

    assertEquals(204, response.getStatusCode().value());
    assertEquals("\"1\"", response.getHeaders().getETag());
    verify(mockApplicationDetailsService).updateApplicationDetails(eq(id), any(), eq(0L));
    verifyNoInteractions(
        mockApplicationQueryService,
        mockApplicationMeansService,
        mockApplicationUpdateService,
        mockApplicationEvidenceService,
        mockApplicationCreationService);
  }

  @Test
  void updateApplicationDetails_returnsPreconditionRequired_whenIfMatchIsMissing()
      throws Exception {
    performDetailsPut(null, VALID_DETAILS_REQUEST)
        .andExpect(status().isPreconditionRequired())
        .andExpect(jsonPath("$.status").value(428))
        .andExpect(jsonPath("$.reason").value("IF_MATCH_REQUIRED"));
    verifyNoInteractions(
        mockApplicationQueryService,
        mockApplicationDetailsService,
        mockApplicationMeansService,
        mockApplicationUpdateService,
        mockApplicationEvidenceService,
        mockApplicationCreationService);
  }

  @ParameterizedTest
  @MethodSource("invalidIfMatchValues")
  void updateApplicationDetails_returnsConsistentBadRequest_whenIfMatchIsInvalid(String ifMatch)
      throws Exception {
    performDetailsPut(ifMatch, VALID_DETAILS_REQUEST)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value("Invalid request content."))
        .andExpect(jsonPath("$.reason").value("INVALID_IF_MATCH"));
    verifyNoInteractions(
        mockApplicationQueryService,
        mockApplicationDetailsService,
        mockApplicationMeansService,
        mockApplicationUpdateService,
        mockApplicationEvidenceService,
        mockApplicationCreationService);
  }

  private static Stream<String> invalidIfMatchValues() {
    return Stream.of(
        "",
        "0",
        "W/\"0\"",
        "*",
        "\"-1\"",
        "\"+1\"",
        "\" 1\"",
        "\"1 \"",
        "\"9223372036854775808\"",
        "\"1\", \"2\"");
  }

  @ParameterizedTest
  @ValueSource(strings = {"\"0\"", "\"000\"", "\"9223372036854775807\""})
  void updateApplicationDetails_acceptsValidIfMatchVersions(String ifMatch) throws Exception {
    stubSuccessfulDetailsEdit();
    performDetailsPut(ifMatch, VALID_DETAILS_REQUEST)
        .andExpect(status().isNoContent())
        .andExpect(header().string("ETag", "\"42\""));
    verify(mockApplicationDetailsService)
        .updateApplicationDetails(
            eq(UUID.fromString(DETAILS_APPLICATION_ID)),
            any(),
            eq(Long.parseLong(ifMatch.substring(1, ifMatch.length() - 1))));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"reasonForReapplication", "clientDetails.niNumber", "clientDetails.address"})
  void updateApplicationDetails_rejectsMissingNullableProperties(String propertyPath)
      throws Exception {
    ObjectNode request = (ObjectNode) new ObjectMapper().readTree(VALID_DETAILS_REQUEST);
    if (propertyPath.startsWith("clientDetails.")) {
      ((ObjectNode) request.get("clientDetails")).remove(propertyPath.substring(14));
    } else {
      request.remove(propertyPath);
    }

    performDetailsPut("\"0\"", request.toString())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.reason").value("INVALID_APPLICATION_DETAILS"));
    verifyNoInteractions(
        mockApplicationQueryService,
        mockApplicationDetailsService,
        mockApplicationMeansService,
        mockApplicationUpdateService,
        mockApplicationEvidenceService,
        mockApplicationCreationService);
  }

  @Test
  void updateApplicationDetails_allowsExplicitNullForNullableProperties() throws Exception {
    stubSuccessfulDetailsEdit();
    performDetailsPut("\"0\"", VALID_DETAILS_REQUEST)
        .andExpect(status().isNoContent())
        .andExpect(header().string("ETag", "\"42\""));
    verifyNoInteractions(
        mockApplicationQueryService,
        mockApplicationMeansService,
        mockApplicationUpdateService,
        mockApplicationEvidenceService,
        mockApplicationCreationService);
  }

  @Test
  void updateApplicationDetails_normalizesNiNumberAndPostcode() throws Exception {
    stubSuccessfulDetailsEdit();
    ObjectNode request = detailsRequest(VALID_FIXED_ADDRESS_DETAILS_REQUEST);
    ObjectNode clientDetails = (ObjectNode) request.path("clientDetails");
    clientDetails.put("niNumber", "a.b123456c");
    ((ObjectNode) clientDetails.path("address")).put("postCode", " s.w.1a - 2aa ");

    performDetailsPut("\"0\"", request.toString()).andExpect(status().isNoContent());

    ArgumentCaptor<UpdateApplicationDetailsRequestBody> requestCaptor =
        ArgumentCaptor.forClass(UpdateApplicationDetailsRequestBody.class);
    verify(mockApplicationDetailsService)
        .updateApplicationDetails(
            eq(UUID.fromString(DETAILS_APPLICATION_ID)), requestCaptor.capture(), eq(0L));
    assertEquals("AB123456C", requestCaptor.getValue().getClientDetails().getNiNumber());
    assertEquals("SW1A2AA", requestCaptor.getValue().getClientDetails().getAddress().getPostCode());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "priorLegalAid",
        "legalAidLast6Months",
        "ecfFlag",
        "clientDetails",
        "clientDetails.firstName",
        "clientDetails.lastName",
        "clientDetails.dateOfBirth",
        "clientDetails.hasFixedAddress"
      })
  void updateApplicationDetails_rejectsMissingRequiredProperties(String propertyPath)
      throws Exception {
    assertInvalidDetails(
        removeDetailsProperty(detailsRequest(VALID_DETAILS_REQUEST), propertyPath));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "addressLine1",
        "addressLine2",
        "addressLine3",
        "addressLine4",
        "townOrCity",
        "postCode",
        "county",
        "country"
      })
  void updateApplicationDetails_rejectsMissingAddressProperties(String property) throws Exception {
    ObjectNode request = detailsRequest(VALID_FIXED_ADDRESS_DETAILS_REQUEST);
    ObjectNode address = (ObjectNode) request.path("clientDetails").path("address");
    address.remove(property);

    assertInvalidDetails(request);
  }

  @ParameterizedTest
  @ValueSource(strings = {"AB12345A", "BG123456A", "a.b123456s", ""})
  void updateApplicationDetails_rejectsInvalidNationalInsuranceNumber(String niNumber)
      throws Exception {
    ObjectNode request = detailsRequest(VALID_DETAILS_REQUEST);
    ((ObjectNode) request.path("clientDetails")).put("niNumber", niNumber);

    assertInvalidDetails(request);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1990-02-30", "01/01/1990", "1990-1-1"})
  void updateApplicationDetails_rejectsInvalidDateOfBirth(String dateOfBirth) throws Exception {
    ObjectNode request = detailsRequest(VALID_DETAILS_REQUEST);
    ((ObjectNode) request.path("clientDetails")).put("dateOfBirth", dateOfBirth);

    assertInvalidDetails(request);
  }

  @ParameterizedTest
  @ValueSource(strings = {"G", "GBR"})
  void updateApplicationDetails_rejectsInvalidCountryLength(String country) throws Exception {
    ObjectNode request = detailsRequest(VALID_FIXED_ADDRESS_DETAILS_REQUEST);
    ((ObjectNode) request.path("clientDetails").path("address")).put("country", country);

    assertInvalidDetails(request);
  }

  @Test
  void updateApplicationDetails_rejectsCountryWithOneSupplementaryCharacter() throws Exception {
    ObjectNode request = detailsRequest(VALID_FIXED_ADDRESS_DETAILS_REQUEST);
    ((ObjectNode) request.path("clientDetails").path("address"))
        .put("country", Character.toString(0x1F310));

    assertInvalidDetails(request);
  }

  @Test
  void updateApplicationDetails_rejectsAddressThatContradictsFixedAddressAnswer() throws Exception {
    ObjectNode fixedAddressRequest = detailsRequest(VALID_FIXED_ADDRESS_DETAILS_REQUEST);
    ((ObjectNode) fixedAddressRequest.path("clientDetails")).put("hasFixedAddress", false);
    assertInvalidDetails(fixedAddressRequest);

    ObjectNode missingAddressRequest = detailsRequest(VALID_DETAILS_REQUEST);
    ((ObjectNode) missingAddressRequest.path("clientDetails")).put("hasFixedAddress", true);
    assertInvalidDetails(missingAddressRequest);
  }

  @Test
  void updateApplicationDetails_rejectsContradictoryLegalAidAnswers() throws Exception {
    ObjectNode request = detailsRequest(VALID_DETAILS_REQUEST);
    request.put("priorLegalAid", "yesSameMatter");
    request.put("legalAidLast6Months", true);
    assertInvalidDetails(request);

    request = detailsRequest(VALID_DETAILS_REQUEST);
    request.put("priorLegalAid", "yesDifferentMatter");
    request.put("legalAidLast6Months", true);
    assertInvalidDetails(request);

    request = detailsRequest(VALID_DETAILS_REQUEST);
    request.put("priorLegalAid", "yesSameMatter");
    request.put("reasonForReapplication", "reason");
    assertInvalidDetails(request);

    request.put("reasonForReapplication", "");
    assertInvalidDetails(request);
  }

  @Test
  void updateApplicationDetails_requiresNonblankReasonOnApplicableBranch() throws Exception {
    ObjectNode request = detailsRequest(VALID_DETAILS_REQUEST);
    request.put("priorLegalAid", "yesSameMatter");
    request.put("legalAidLast6Months", true);
    request.put("reasonForReapplication", "  ");

    assertInvalidDetails(request);
  }

  @Test
  void updateApplicationDetails_allowsValidReasonAndEmptyOptionalAddressValues() throws Exception {
    ObjectNode request = detailsRequest(VALID_FIXED_ADDRESS_DETAILS_REQUEST);
    request.put("priorLegalAid", "yesSameMatter");
    request.put("legalAidLast6Months", true);
    request.put("reasonForReapplication", "Same matter reason");
    ((ObjectNode) request.path("clientDetails").path("address")).put("addressLine2", "");

    stubSuccessfulDetailsEdit();
    performDetailsPut("\"0\"", request.toString())
        .andExpect(status().isNoContent())
        .andExpect(header().string("ETag", "\"42\""));
  }

  @Test
  void updateApplicationDetails_rejectsUnknownProperties() throws Exception {
    ObjectNode request = detailsRequest(VALID_DETAILS_REQUEST);
    request.put("unexpected", "value");
    assertInvalidDetails(request);

    request = detailsRequest(VALID_DETAILS_REQUEST);
    ((ObjectNode) request.get("clientDetails")).put("unexpected", "value");
    assertInvalidDetails(request);

    request = detailsRequest(VALID_FIXED_ADDRESS_DETAILS_REQUEST);
    ((ObjectNode) request.path("clientDetails").path("address")).put("unexpected", "value");
    assertInvalidDetails(request);
  }

  @Test
  void updateApplicationDetails_rejectsWrongTypesAtEachEditableLevel() throws Exception {
    ObjectNode request = detailsRequest(VALID_DETAILS_REQUEST);
    request.put("ecfFlag", "false");
    assertInvalidDetails(request);

    request = detailsRequest(VALID_DETAILS_REQUEST);
    ((ObjectNode) request.get("clientDetails")).put("firstName", 123);
    assertInvalidDetails(request);

    request = detailsRequest(VALID_DETAILS_REQUEST);
    ((ObjectNode) request.get("clientDetails")).put("address", "malformed");
    assertInvalidDetails(request);

    request = detailsRequest(VALID_FIXED_ADDRESS_DETAILS_REQUEST);
    ((ObjectNode) request.path("clientDetails").path("address")).put("addressLine1", false);
    assertInvalidDetails(request);
  }

  @Test
  void updateApplicationDetails_rejectsDuplicateJsonProperties() throws Exception {
    String request =
        VALID_DETAILS_REQUEST.replace(
            "\"ecfFlag\": false", "\"ecfFlag\": false, \"ecfFlag\": true");

    performDetailsPut("\"0\"", request)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("MALFORMED_REQUEST_BODY"));
  }

  private ObjectNode detailsRequest(String requestBody) throws Exception {
    return (ObjectNode) new ObjectMapper().readTree(requestBody);
  }

  private ObjectNode removeDetailsProperty(ObjectNode request, String propertyPath) {
    String[] properties = propertyPath.split("\\.");
    ObjectNode parent = request;
    for (int index = 0; index < properties.length - 1; index++) {
      if (!(parent.get(properties[index]) instanceof ObjectNode nested)) {
        throw new IllegalArgumentException("Test property path is not an object");
      }
      parent = nested;
    }
    parent.remove(properties[properties.length - 1]);
    return request;
  }

  private void assertInvalidDetails(ObjectNode request) throws Exception {
    performDetailsPut("\"0\"", request.toString())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value("Invalid request content."))
        .andExpect(jsonPath("$.reason").value("INVALID_APPLICATION_DETAILS"));
    verifyNoInteractions(
        mockApplicationQueryService,
        mockApplicationDetailsService,
        mockApplicationMeansService,
        mockApplicationUpdateService,
        mockApplicationEvidenceService,
        mockApplicationCreationService);
  }

  private ResultActions performDetailsPut(String ifMatch, String requestBody) throws Exception {
    var request =
        put("/api/v1/applications/{id}/details", DETAILS_APPLICATION_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody);
    if (ifMatch != null) {
      request.header("If-Match", ifMatch);
    }
    return mockMvc.perform(request);
  }

  private void stubSuccessfulDetailsEdit() {
    when(mockApplicationDetailsService.updateApplicationDetails(any(), any(), anyLong()))
        .thenReturn("\"42\"");
  }

  private ObjectMapper createRequestMapper() {
    return new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);
  }

  @Test
  void createApplication_returnsCreatedStatus_andApplication() throws Exception {
    CreateApplicationRequestBody request = CreateApplicationRequestGenerator.createWithName(null);
    Application response = ApplicationGenerator.create(null);
    when(mockApplicationCreationService.createApplication(any())).thenReturn(response);

    ObjectMapper mapper = createRequestMapper();

    var mappedRequest = mapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/api/v1/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mappedRequest)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string(
                    "Location",
                    org.hamcrest.Matchers.endsWith(
                        "/api/v1/applications/%s".formatted(response.getId()))))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(response.getId().toString()));
  }

  @Test
  void createApplication_returnsBadRequestStatus() throws Exception {
    CreateApplicationRequestBody request =
        CreateApplicationRequestGenerator.createWithoutName(null);

    ObjectMapper mapper = createRequestMapper();

    var mappedRequest = mapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/api/v1/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mappedRequest)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(
            content()
                .json(
                    "{"
                        + "\"type\":\"about:blank\","
                        + "\"title\":\"Bad Request\","
                        + "\"status\":400,"
                        + "\"detail\":\"Invalid request content.\","
                        + "\"instance\":\"/api/v1/applications\"}"));
  }

  @Test
  void createApplication_rejectsPostcodeThatRemainsInvalidAfterNormalization() throws Exception {
    ObjectMapper mapper = createRequestMapper();
    ObjectNode request = mapper.valueToTree(CreateApplicationRequestGenerator.createWithName(null));
    ((ObjectNode) request.path("clientDetails").path("address")).put("postCode", "INVALID");

    mockMvc
        .perform(
            post("/api/v1/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request.toString()))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(mockApplicationCreationService);
  }

  @Test
  void createApplication_rejectsMismatchedLegalAidAnswersBeforeCreation() throws Exception {
    String request =
        """
        {
            "providerOfficeCode": "office",
            "scopingQuestions": {"priorLegalAid": "yesSameMatter"},
            "clientDetails": {
                "firstName": "",
                "lastName": "",
                "dateOfBirth": "1990-01-01",
                "hasFixedAddress": false
            }
        }
        """;
    when(mockApplicationCreationService.createApplication(any()))
        .thenReturn(ApplicationGenerator.create(null));

    mockMvc
        .perform(
            post("/api/v1/applications").contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(mockApplicationCreationService);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("createApplicationContractCases")
  void createApplication_matchesContractCases(
      String caseId, String operation, boolean accepted, JsonNode request, JsonNode preserves)
      throws Exception {
    assertEquals("createApplication", operation);
    when(mockApplicationCreationService.createApplication(any()))
        .thenReturn(ApplicationGenerator.create(null));

    ResultActions result =
        mockMvc.perform(
            post("/api/v1/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request.toString()));

    if (!accepted) {
      result.andExpect(status().isBadRequest());
      verifyNoInteractions(mockApplicationCreationService);
      return;
    }

    result.andExpect(status().isCreated());
    ArgumentCaptor<CreateApplicationRequestBody> requestCaptor =
        ArgumentCaptor.forClass(CreateApplicationRequestBody.class);
    verify(mockApplicationCreationService).createApplication(requestCaptor.capture());
    JsonNode actual = createRequestMapper().valueToTree(requestCaptor.getValue());
    assertEquals(request, actual, caseId + " should preserve the complete request");
    preserves
        .fields()
        .forEachRemaining(
            expected ->
                assertEquals(
                    expected.getValue(),
                    actual.at(expected.getKey()),
                    caseId + " should preserve " + expected.getKey()));
  }

  private static Stream<Arguments> createApplicationContractCases() throws Exception {
    Resource[] resources =
        new PathMatchingResourcePatternResolver()
            .getResources("classpath*:/validation/create-application/*.json");
    if (resources.length == 0) {
      throw new IllegalStateException("Create application contract cases were not found");
    }

    ObjectMapper mapper = new ObjectMapper();
    return Arrays.stream(resources)
        .sorted(Comparator.comparing(Resource::getFilename))
        .map(
            resource -> {
              try (var input = resource.getInputStream()) {
                JsonNode testCase = mapper.readTree(input);
                return Arguments.of(
                    testCase.path("id").asText(),
                    testCase.path("operation").asText(),
                    testCase.path("accepted").asBoolean(),
                    testCase.path("request"),
                    testCase.path("preserves"));
              } catch (IOException exception) {
                throw new UncheckedIOException(exception);
              }
            });
  }

  @Test
  void createApplication_acceptsNamesWithoutFormatRestrictions_andPreservesThem() throws Exception {
    ObjectMapper mapper = createRequestMapper();
    ObjectNode request = mapper.valueToTree(CreateApplicationRequestGenerator.createWithName(null));
    String firstName = Character.toString(0x2003);
    String lastName = "O'Connor 42 " + Character.toString(0x674E);
    ((ObjectNode) request.get("clientDetails"))
        .put("firstName", firstName)
        .put("lastName", lastName);
    when(mockApplicationCreationService.createApplication(any()))
        .thenReturn(ApplicationGenerator.create(null));

    mockMvc
        .perform(
            post("/api/v1/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request.toString()))
        .andExpect(status().isCreated());

    verify(mockApplicationCreationService)
        .createApplication(
            argThat(
                actual ->
                    firstName.equals(actual.getClientDetails().getFirstName())
                        && lastName.equals(actual.getClientDetails().getLastName())));
  }

  @Test
  void createApplication_rejectsDuplicateJsonProperties_beforeCreation() throws Exception {
    ObjectMapper mapper = createRequestMapper();
    String request =
        mapper.writeValueAsString(CreateApplicationRequestGenerator.createWithName(null));
    String duplicateRequest =
        request.replace(
            "\"legalAidLast6Months\":false",
            "\"legalAidLast6Months\":false,\"legalAidLast6Months\":false");
    when(mockApplicationCreationService.createApplication(any()))
        .thenReturn(ApplicationGenerator.create(null));

    mockMvc
        .perform(
            post("/api/v1/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(duplicateRequest))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(mockApplicationCreationService);
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "{\"priorLegalAid\":\"same_matter\"}"})
  void createApplication_rejectsInvalidScopingQuestions(String scopingQuestions) throws Exception {
    ObjectMapper mapper = createRequestMapper();
    ObjectNode request = mapper.valueToTree(CreateApplicationRequestGenerator.createWithName(null));
    request.set("scopingQuestions", mapper.readTree(scopingQuestions));

    mockMvc
        .perform(
            post("/api/v1/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request.toString()))
        .andExpect(status().isBadRequest());
    org.mockito.Mockito.verifyNoInteractions(mockApplicationCreationService);
  }

  @Test
  void createApplication_rejectsScopingQuestionsWithoutPriorLegalAid() throws Exception {
    ObjectMapper mapper = createRequestMapper();
    ObjectNode request = mapper.valueToTree(CreateApplicationRequestGenerator.createWithName(null));
    request.set("scopingQuestions", mapper.createObjectNode());
    mockMvc
        .perform(
            post("/api/v1/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request.toString()))
        .andExpect(status().isBadRequest());
    org.mockito.Mockito.verifyNoInteractions(mockApplicationCreationService);
  }

  @Test
  void createApplication_rejectsMissingScopingQuestions() throws Exception {
    ObjectMapper mapper = createRequestMapper();
    ObjectNode request = mapper.valueToTree(CreateApplicationRequestGenerator.createWithName(null));
    request.remove("scopingQuestions");

    mockMvc
        .perform(
            post("/api/v1/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request.toString()))
        .andExpect(status().isBadRequest());
    org.mockito.Mockito.verifyNoInteractions(mockApplicationCreationService);
  }

  @Test
  void updateApplicationMeans_returnsNoContent_andForwardsDataAndResultToService()
      throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    String requestBody =
        """
        {"data": {"level_of_help": "controlled"}, "result": {"indication": true}}
        """;

    mockMvc
        .perform(
            put("/api/v1/applications/%s/means".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isNoContent());

    ArgumentCaptor<EligibilityData> eligibiltyDataCaptor =
        ArgumentCaptor.forClass(EligibilityData.class);

    verify(mockApplicationMeansService)
        .updateMeans(
            eq(applicationId), eligibiltyDataCaptor.capture(), eq(Map.of("indication", true)));
    assertEquals("controlled", eligibiltyDataCaptor.getValue().getLevelOfHelp());
  }

  @Test
  void updateApplicationMeans_returnsBadRequest_whenDataIsMissing() throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    String requestBody =
        """
        {"result": {"indication": true}}
        """;

    mockMvc
        .perform(
            put("/api/v1/applications/%s/means".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateApplicationMeans_returnsBadRequest_whenResultIsMissing() throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    String requestBody =
        """
        {"data": {"level_of_help": "controlled"}}
        """;

    mockMvc
        .perform(
            put("/api/v1/applications/%s/means".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateApplicationMeans_returnsBadRequest_whenPayloadExceedsMaxDocumentLength()
      throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    // each value stays under max-string-length so only max-document-length is exercised
    String longValue = "a".repeat(60_000);
    String requestBody =
        """
        {
            "data": {
                "note1": "%s",
                "note2": "%s",
                "note3": "%s",
                "note4": "%s",
                "note5": "%s"
            },
            "result": {}
        }
        """
            .formatted(longValue, longValue, longValue, longValue, longValue);

    mockMvc
        .perform(
            put("/api/v1/applications/%s/means".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateApplicationMeans_returnsNotFound_whenApplicationDoesNotExist() throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(new ApplicationNotFoundException("No application found with id: " + applicationId))
        .when(mockApplicationMeansService)
        .updateMeans(any(), any(), any());
    String requestBody =
        """
        {"data": {}, "result": {}}
        """;

    mockMvc
        .perform(
            put("/api/v1/applications/%s/means".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateApplicationMeans_returnsForbidden_whenUserNotAuthorizedForOffice() throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(
            new ApplicationForbiddenException(
                "Not authorized to update application %s".formatted(applicationId)))
        .when(mockApplicationMeansService)
        .updateMeans(any(), any(), any());
    String requestBody =
        """
        {"data": {}, "result": {}}
        """;

    mockMvc
        .perform(
            put("/api/v1/applications/%s/means".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isForbidden())
        .andExpect(
            jsonPath("$.detail")
                .value("Not authorized to update application %s".formatted(applicationId)));
  }

  @Test
  void updateApplicationMeans_returnsConflict_whenDatastoreEtagMismatchPersists() throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(
            new ApplicationConflictException(
                "Application %s was modified concurrently".formatted(applicationId)))
        .when(mockApplicationMeansService)
        .updateMeans(any(), any(), any());
    String requestBody =
        """
        {"data": {}, "result": {}}
        """;

    mockMvc
        .perform(
            put("/api/v1/applications/%s/means".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isConflict());
  }

  @Test
  void updateApplicationMeans_returnsConflict_whenApplicationAlreadyRecorded() throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(
            new ApplicationConflictException(
                "Application %s has already been recorded and cannot be updated"
                    .formatted(applicationId)))
        .when(mockApplicationMeansService)
        .updateMeans(any(), any(), any());
    String requestBody =
        """
        {"data": {}, "result": {}}
        """;

    mockMvc
        .perform(
            put("/api/v1/applications/%s/means".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isConflict());
  }

  @Test
  void updateApplicationMeans_returnsBadRequest_whenDatastoreRejectsTheRequest() throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(
            new ApplicationBadRequestException(
                "Datastore rejected the request for application %s".formatted(applicationId)))
        .when(mockApplicationMeansService)
        .updateMeans(any(), any(), any());
    String requestBody =
        """
        {"data": {}, "result": {}}
        """;

    mockMvc
        .perform(
            put("/api/v1/applications/%s/means".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateApplicationMeans_returnsBadGateway_whenDatastoreReturnsAServerError()
      throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(
            new ApplicationUpstreamErrorException(
                "Datastore returned an error for application %s".formatted(applicationId)))
        .when(mockApplicationMeansService)
        .updateMeans(any(), any(), any());
    String requestBody =
        """
        {"data": {}, "result": {}}
        """;

    mockMvc
        .perform(
            put("/api/v1/applications/%s/means".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadGateway());
  }

  @Test
  void updateApplicationMeans_returnsServiceUnavailable_whenDatastoreCannotBeReached()
      throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(
            new ApplicationUnavailableException(
                "Datastore is unavailable for application %s".formatted(applicationId)))
        .when(mockApplicationMeansService)
        .updateMeans(any(), any(), any());
    String requestBody =
        """
        {"data": {}, "result": {}}
        """;

    mockMvc
        .perform(
            put("/api/v1/applications/%s/means".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void updateApplicationDeclaration_returnsNoContent_andForwardsDeclarationToService()
      throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    String requestBody =
        """
        {"declarationConfirmation": true, "dateSigned": "2026-08-14"}
        """;

    mockMvc
        .perform(
            put("/api/v1/applications/%s/declaration".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isNoContent());

    verify(mockApplicationUpdateService)
        .updateDeclaration(applicationId, true, LocalDate.of(2026, 8, 14));
  }

  @Test
  void updateApplicationDeclaration_returnsBadRequest_whenDeclarationConfirmationIsMissing()
      throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    mockMvc
        .perform(
            put("/api/v1/applications/%s/declaration".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"dateSigned": "2026-08-14"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateApplicationDeclaration_returnsBadRequest_whenDateSignedIsMissing() throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    mockMvc
        .perform(
            put("/api/v1/applications/%s/declaration".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"declarationConfirmation": true}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateApplicationDeclaration_returnsNotFound_whenApplicationDoesNotExist() throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(new ApplicationNotFoundException("No application found with id: " + applicationId))
        .when(mockApplicationUpdateService)
        .updateDeclaration(any(), any(), any());

    mockMvc
        .perform(
            put("/api/v1/applications/%s/declaration".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"declarationConfirmation": true, "dateSigned": "2026-08-14"}
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateApplicationDeclaration_returnsConflict_whenDatastoreEtagMismatchPersists()
      throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(
            new ApplicationConflictException(
                "Application %s was modified concurrently".formatted(applicationId)))
        .when(mockApplicationUpdateService)
        .updateDeclaration(any(), any(), any());

    mockMvc
        .perform(
            put("/api/v1/applications/%s/declaration".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"declarationConfirmation": true, "dateSigned": "2026-08-14"}
                    """))
        .andExpect(status().isConflict());
  }

  @Test
  void updateApplicationEvidence_returnsNoContent_andForwardsRequestBodyToService()
      throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    String requestBody =
        """
        {
          "evidenceExemptionCode": "EXEMPT",
          "evidenceExemptionReason": "reason",
          "incomeEvidenceChecklist": {"payslips": true},
          "expenditureCapitalEvidenceChecklist": {"bankStatements": true}
        }
        """;

    mockMvc
        .perform(
            put("/api/v1/applications/%s/evidence".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isNoContent());

    verify(mockApplicationEvidenceService)
        .updateEvidence(
            eq(applicationId),
            argThat(
                b ->
                    "EXEMPT".equals(b.getEvidenceExemptionCode())
                        && "reason".equals(b.getEvidenceExemptionReason())));
  }

  @Test
  void updateApplicationEvidence_returnsNotFound_whenApplicationDoesNotExist() throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(new ApplicationNotFoundException("No application found with id: " + applicationId))
        .when(mockApplicationEvidenceService)
        .updateEvidence(any(), any());

    mockMvc
        .perform(
            put("/api/v1/applications/%s/evidence".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateApplicationEvidence_returnsForbidden_whenUserNotAuthorizedForOffice()
      throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(
            new ApplicationForbiddenException(
                "Not authorized to update application %s".formatted(applicationId)))
        .when(mockApplicationEvidenceService)
        .updateEvidence(any(), any());

    mockMvc
        .perform(
            put("/api/v1/applications/%s/evidence".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateApplicationEvidence_returnsConflict_whenApplicationAlreadyRecorded() throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(
            new ApplicationConflictException(
                "Application %s has already been recorded and cannot be updated"
                    .formatted(applicationId)))
        .when(mockApplicationEvidenceService)
        .updateEvidence(any(), any());

    mockMvc
        .perform(
            put("/api/v1/applications/%s/evidence".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isConflict());
  }

  @Test
  void updateApplicationEvidence_returnsBadRequest_whenDatastoreRejectsTheRequest()
      throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(
            new ApplicationBadRequestException(
                "Datastore rejected the request for application %s".formatted(applicationId)))
        .when(mockApplicationEvidenceService)
        .updateEvidence(any(), any());

    mockMvc
        .perform(
            put("/api/v1/applications/%s/evidence".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateApplicationEvidence_returnsBadGateway_whenDatastoreReturnsAServerError()
      throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(
            new ApplicationUpstreamErrorException(
                "Datastore returned an error for application %s".formatted(applicationId)))
        .when(mockApplicationEvidenceService)
        .updateEvidence(any(), any());

    mockMvc
        .perform(
            put("/api/v1/applications/%s/evidence".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadGateway());
  }

  @Test
  void updateApplicationEvidence_returnsServiceUnavailable_whenDatastoreCannotBeReached()
      throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(
            new ApplicationUnavailableException(
                "Datastore is unavailable for application %s".formatted(applicationId)))
        .when(mockApplicationEvidenceService)
        .updateEvidence(any(), any());

    mockMvc
        .perform(
            put("/api/v1/applications/%s/evidence".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void updateApplicationStatus_returnsNoContent_andForwardsStatusToService() throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    String requestBody =
        """
        {"applicationState": "COMPLETED", "eTag": 1}
        """;

    mockMvc
        .perform(
            patch("/api/v1/applications/%s/status".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isNoContent());

    verify(mockApplicationUpdateService)
        .updateStatus(applicationId, uk.gov.justice.laa.rcw.model.ApplicationState.COMPLETED);
  }

  @Test
  void updateApplicationStatus_returnsBadRequest_whenApplicationStateIsMissing() throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    mockMvc
        .perform(
            patch("/api/v1/applications/%s/status".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateApplicationStatus_returnsNotFound_whenApplicationDoesNotExist() throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(new ApplicationNotFoundException("No application found with id: " + applicationId))
        .when(mockApplicationUpdateService)
        .updateStatus(any(), any());

    mockMvc
        .perform(
            patch("/api/v1/applications/%s/status".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + "\"applicationState\":\"COMPLETED\",\"eTag\":1}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateApplicationStatus_returnsForbidden_whenUserNotAuthorizedForOffice() throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(
            new ApplicationForbiddenException(
                "Not authorized to update application %s".formatted(applicationId)))
        .when(mockApplicationUpdateService)
        .updateStatus(any(), any());

    mockMvc
        .perform(
            patch("/api/v1/applications/%s/status".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + "\"applicationState\":\"COMPLETED\",\"eTag\":1}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateApplicationStatus_returnsConflict_whenDatastoreEtagMismatchPersists()
      throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(
            new ApplicationConflictException(
                "Application %s was modified concurrently".formatted(applicationId)))
        .when(mockApplicationUpdateService)
        .updateStatus(any(), any());

    mockMvc
        .perform(
            patch("/api/v1/applications/%s/status".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + "\"applicationState\":\"COMPLETED\",\"eTag\":1}"))
        .andExpect(status().isConflict());
  }

  @Test
  void updateApplicationStatus_returnsBadRequest_whenDatastoreRejectsTheRequest() throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(
            new ApplicationBadRequestException(
                "Datastore rejected the request for application %s".formatted(applicationId)))
        .when(mockApplicationUpdateService)
        .updateStatus(any(), any());

    mockMvc
        .perform(
            patch("/api/v1/applications/%s/status".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + "\"applicationState\":\"COMPLETED\",\"eTag\":1}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateApplicationStatus_returnsBadGateway_whenDatastoreReturnsAServerError()
      throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(
            new ApplicationUpstreamErrorException(
                "Datastore returned an error for application %s".formatted(applicationId)))
        .when(mockApplicationUpdateService)
        .updateStatus(any(), any());

    mockMvc
        .perform(
            patch("/api/v1/applications/%s/status".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + "\"applicationState\":\"COMPLETED\",\"eTag\":1}"))
        .andExpect(status().isBadGateway());
  }

  @Test
  void updateApplicationStatus_returnsServiceUnavailable_whenDatastoreCannotBeReached()
      throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    doThrow(
            new ApplicationUnavailableException(
                "Datastore is unavailable for application %s".formatted(applicationId)))
        .when(mockApplicationUpdateService)
        .updateStatus(any(), any());

    mockMvc
        .perform(
            patch("/api/v1/applications/%s/status".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + "\"applicationState\":\"COMPLETED\",\"eTag\":1}"))
        .andExpect(status().isServiceUnavailable());
  }
}
