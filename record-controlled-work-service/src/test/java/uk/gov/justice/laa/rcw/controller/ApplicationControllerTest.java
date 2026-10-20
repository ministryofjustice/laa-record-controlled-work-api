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
import static uk.gov.justice.laa.rcw.controller.ApplicationRequestFixtures.createRequestObjectMapper;
import static uk.gov.justice.laa.rcw.controller.ApplicationRequestFixtures.detailsRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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
import uk.gov.justice.laa.rcw.model.FamilyLawClassification;
import uk.gov.justice.laa.rcw.model.ScopingQuestions;
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
    performDetailsPut(null, detailsRequest().toString())
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
    performDetailsPut(ifMatch, detailsRequest().toString())
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
    performDetailsPut(ifMatch, detailsRequest().toString())
        .andExpect(status().isNoContent())
        .andExpect(header().string("ETag", "\"42\""));
    verify(mockApplicationDetailsService)
        .updateApplicationDetails(
            eq(UUID.fromString(DETAILS_APPLICATION_ID)),
            any(),
            eq(Long.parseLong(ifMatch.substring(1, ifMatch.length() - 1))));
  }

  @ParameterizedTest
  @MethodSource("representativeScopingQuestions")
  void updateApplicationDetails_acceptsPublicAndPrivateScopingQuestions(ObjectNode scopingQuestions)
      throws Exception {
    ObjectNode request = detailsRequest();
    request.set("scopingQuestions", scopingQuestions);
    stubSuccessfulDetailsEdit();

    performDetailsPut("\"0\"", request.toString()).andExpect(status().isNoContent());

    ArgumentCaptor<UpdateApplicationDetailsRequestBody> requestCaptor =
        ArgumentCaptor.forClass(UpdateApplicationDetailsRequestBody.class);
    verify(mockApplicationDetailsService)
        .updateApplicationDetails(
            eq(UUID.fromString(DETAILS_APPLICATION_ID)), requestCaptor.capture(), eq(0L));
    assertEquals(
        createRequestObjectMapper().treeToValue(scopingQuestions, ScopingQuestions.class),
        requestCaptor.getValue().getScopingQuestions());
  }

  @Test
  void updateApplicationDetails_rejectsInvalidScopingBeforeDelegating() throws Exception {
    ObjectNode request = detailsRequest();
    ((ObjectNode) request.get("scopingQuestions")).put("priorLegalAid", "invalid");

    assertInvalidDetails(request);
  }

  @ParameterizedTest
  @MethodSource("representativeScopingQuestions")
  void createApplication_acceptsPublicAndPrivateScopingQuestions(ObjectNode scopingQuestions)
      throws Exception {
    ObjectMapper mapper = createRequestObjectMapper();
    ObjectNode request = mapper.valueToTree(CreateApplicationRequestGenerator.createWithName(null));
    request.set("scopingQuestions", scopingQuestions);
    when(mockApplicationCreationService.createApplication(any()))
        .thenReturn(ApplicationGenerator.create(null));

    mockMvc
        .perform(
            post("/api/v1/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request.toString()))
        .andExpect(status().isCreated());

    ArgumentCaptor<CreateApplicationRequestBody> requestCaptor =
        ArgumentCaptor.forClass(CreateApplicationRequestBody.class);
    verify(mockApplicationCreationService).createApplication(requestCaptor.capture());
    assertEquals(
        mapper.treeToValue(scopingQuestions, ScopingQuestions.class),
        requestCaptor.getValue().getScopingQuestions());
  }

  @Test
  void createApplication_rejectsInvalidScopingBeforeDelegating() throws Exception {
    ObjectMapper mapper = createRequestObjectMapper();
    ObjectNode request = mapper.valueToTree(CreateApplicationRequestGenerator.createWithName(null));
    ((ObjectNode) request.get("scopingQuestions")).put("priorLegalAid", "invalid");

    mockMvc
        .perform(
            post("/api/v1/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request.toString()))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(mockApplicationCreationService);
  }

  private static Stream<ObjectNode> representativeScopingQuestions() {
    return Stream.of(
        ApplicationRequestFixtures.scopingQuestions(FamilyLawClassification.PRIVATE),
        ApplicationRequestFixtures.scopingQuestions(FamilyLawClassification.PUBLIC));
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

  @Test
  void createApplication_returnsCreatedStatus_andApplication() throws Exception {
    CreateApplicationRequestBody request = CreateApplicationRequestGenerator.createWithName(null);
    Application response = ApplicationGenerator.create(null);
    when(mockApplicationCreationService.createApplication(any())).thenReturn(response);

    ObjectMapper mapper = createRequestObjectMapper();

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

    ObjectMapper mapper = createRequestObjectMapper();

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
