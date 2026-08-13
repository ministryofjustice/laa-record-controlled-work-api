package uk.gov.justice.laa.rcw.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
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
import uk.gov.justice.laa.rcw.model.CreateApplicationRequestBody;
import uk.gov.justice.laa.rcw.service.ApplicationCreationService;
import uk.gov.justice.laa.rcw.service.ApplicationEvidenceService;
import uk.gov.justice.laa.rcw.service.ApplicationMeansService;
import uk.gov.justice.laa.rcw.service.ApplicationQueryService;
import uk.gov.justice.laa.rcw.service.ApplicationUpdateService;

@WebMvcTest(ApplicationController.class)
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

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ApplicationQueryService mockApplicationQueryService;
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

    when(mockApplicationQueryService.getApplications(any(), any(), any(), any()))
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
    when(mockApplicationQueryService.getApplications(any(), any(), any(), any()))
        .thenReturn(List.of());

    mockMvc
        .perform(get("/api/v1/applications"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.*", hasSize(0)));
  }

  @Test
  void getApplicationWithId_returnsOkStatusAndApplicationResponse() throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    Application applicationResponse = ApplicationGenerator.create(b -> b.id(applicationId));

    when(mockApplicationQueryService.getApplication(applicationId))
        .thenReturn(Optional.of(applicationResponse));

    mockMvc
        .perform(get("/api/v1/applications/%s".formatted(applicationId)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value("b2c3d4e5-f6a7-8901-bcde-f12345678901"))
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
  void getApplicationWithId_returnsNotFoundWhenApplicationDoesNotExist() throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    when(mockApplicationQueryService.getApplication(applicationId)).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/v1/applications/%s".formatted(applicationId)))
        .andExpect(status().isNotFound());
  }

  @Test
  void createApplication_returnsCreatedStatus_andApplication() throws Exception {
    CreateApplicationRequestBody request = CreateApplicationRequestGenerator.createWithName(null);
    Application response = ApplicationGenerator.create(null);
    when(mockApplicationCreationService.createApplication(any())).thenReturn(response);

    ObjectMapper mapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

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

    ObjectMapper mapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

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

    verify(mockApplicationMeansService)
        .updateMeans(
            applicationId, Map.of("level_of_help", "controlled"), Map.of("indication", true));
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
  void updateApplicationStatus_returnsNoContent_andForwardsStatusToService() throws Exception {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    String requestBody =
        """
                {"officeId": "AB12CD", "applicationState": "COMPLETED"}
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
                .content("{" + "\"officeId\":\"AB12CD\",\"applicationState\":\"COMPLETED\"}"))
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
                .content("{" + "\"officeId\":\"AB12CD\",\"applicationState\":\"COMPLETED\"}"))
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
                .content("{" + "\"officeId\":\"AB12CD\",\"applicationState\":\"COMPLETED\"}"))
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
                .content("{" + "\"officeId\":\"AB12CD\",\"applicationState\":\"COMPLETED\"}"))
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
                .content("{" + "\"officeId\":\"AB12CD\",\"applicationState\":\"COMPLETED\"}"))
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
                .content("{" + "\"officeId\":\"AB12CD\",\"applicationState\":\"COMPLETED\"}"))
        .andExpect(status().isServiceUnavailable());
  }
}
