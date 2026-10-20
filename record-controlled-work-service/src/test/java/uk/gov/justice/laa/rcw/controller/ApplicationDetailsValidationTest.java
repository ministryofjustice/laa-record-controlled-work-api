package uk.gov.justice.laa.rcw.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.rcw.controller.ApplicationRequestFixtures.detailsRequest;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
import uk.gov.justice.laa.rcw.model.UpdateApplicationDetailsRequestBody;
import uk.gov.justice.laa.rcw.service.ApplicationCreationService;
import uk.gov.justice.laa.rcw.service.ApplicationDetailsService;
import uk.gov.justice.laa.rcw.service.ApplicationEvidenceService;
import uk.gov.justice.laa.rcw.service.ApplicationMeansService;
import uk.gov.justice.laa.rcw.service.ApplicationQueryService;
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
class ApplicationDetailsValidationTest {

  private static final String DETAILS_APPLICATION_ID = "b2c3d4e5-f6a7-8901-bcde-f12345678901";
  @Autowired private MockMvc mockMvc;

  @MockitoBean private ApplicationQueryService mockApplicationQueryService;
  @MockitoBean private ApplicationDetailsService mockApplicationDetailsService;
  @MockitoBean private ApplicationMeansService mockApplicationMeansService;
  @MockitoBean private ApplicationEvidenceService mockApplicationEvidenceService;
  @MockitoBean private ApplicationUpdateService mockApplicationUpdateService;
  @MockitoBean private ApplicationCreationService mockApplicationCreationService;

  @ParameterizedTest
  @ValueSource(
      strings = {"reasonForReapplication", "clientDetails.niNumber", "clientDetails.address"})
  void updateApplicationDetails_rejectsMissingNullableProperties(String propertyPath)
      throws Exception {
    ObjectNode request = detailsRequest();
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
    performDetailsPut("\"0\"", detailsRequest().toString())
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
    ObjectNode request = detailsRequest(true);
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
    assertInvalidDetails(removeDetailsProperty(detailsRequest(), propertyPath));
  }

  @Test
  void updateApplicationDetails_rejectsMissingOrNullScopingQuestions() throws Exception {
    ObjectNode request = detailsRequest();
    request.remove("scopingQuestions");
    assertInvalidDetails(request);

    request = detailsRequest();
    request.putNull("scopingQuestions");
    assertInvalidDetails(request);
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
    ObjectNode request = detailsRequest(true);
    ObjectNode address = (ObjectNode) request.path("clientDetails").path("address");
    address.remove(property);

    assertInvalidDetails(request);
  }

  @ParameterizedTest
  @ValueSource(strings = {"AB12345A", "BG123456A", "a.b123456s", ""})
  void updateApplicationDetails_rejectsInvalidNationalInsuranceNumber(String niNumber)
      throws Exception {
    ObjectNode request = detailsRequest();
    ((ObjectNode) request.path("clientDetails")).put("niNumber", niNumber);

    assertInvalidDetails(request);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1990-02-30", "01/01/1990", "1990-1-1"})
  void updateApplicationDetails_rejectsInvalidDateOfBirth(String dateOfBirth) throws Exception {
    ObjectNode request = detailsRequest();
    ((ObjectNode) request.path("clientDetails")).put("dateOfBirth", dateOfBirth);

    assertInvalidDetails(request);
  }

  @ParameterizedTest
  @ValueSource(strings = {"G", "GBR"})
  void updateApplicationDetails_rejectsInvalidCountryLength(String country) throws Exception {
    ObjectNode request = detailsRequest(true);
    ((ObjectNode) request.path("clientDetails").path("address")).put("country", country);

    assertInvalidDetails(request);
  }

  @Test
  void updateApplicationDetails_rejectsCountryWithOneSupplementaryCharacter() throws Exception {
    ObjectNode request = detailsRequest(true);
    ((ObjectNode) request.path("clientDetails").path("address"))
        .put("country", Character.toString(0x1F310));

    assertInvalidDetails(request);
  }

  @Test
  void updateApplicationDetails_rejectsAddressThatContradictsFixedAddressAnswer() throws Exception {
    ObjectNode fixedAddressRequest = detailsRequest(true);
    ((ObjectNode) fixedAddressRequest.path("clientDetails")).put("hasFixedAddress", false);
    assertInvalidDetails(fixedAddressRequest);

    ObjectNode missingAddressRequest = detailsRequest();
    ((ObjectNode) missingAddressRequest.path("clientDetails")).put("hasFixedAddress", true);
    assertInvalidDetails(missingAddressRequest);
  }

  @Test
  void updateApplicationDetails_rejectsContradictoryLegalAidAnswers() throws Exception {
    ObjectNode request = detailsRequest();
    ((ObjectNode) request.get("scopingQuestions")).put("priorLegalAid", "yesSameMatter");
    request.put("legalAidLast6Months", true);
    assertInvalidDetails(request);

    request = detailsRequest();
    ((ObjectNode) request.get("scopingQuestions")).put("priorLegalAid", "yesDifferentMatter");
    request.put("legalAidLast6Months", true);
    assertInvalidDetails(request);

    request = detailsRequest();
    ((ObjectNode) request.get("scopingQuestions")).put("priorLegalAid", "yesSameMatter");
    request.put("reasonForReapplication", "reason");
    assertInvalidDetails(request);

    request.put("reasonForReapplication", "");
    assertInvalidDetails(request);
  }

  @Test
  void updateApplicationDetails_requiresNonblankReasonOnApplicableBranch() throws Exception {
    ObjectNode request = detailsRequest();
    ((ObjectNode) request.get("scopingQuestions")).put("priorLegalAid", "yesSameMatter");
    request.put("legalAidLast6Months", true);
    request.put("reasonForReapplication", "  ");

    assertInvalidDetails(request);
  }

  @Test
  void updateApplicationDetails_allowsValidReasonAndEmptyOptionalAddressValues() throws Exception {
    ObjectNode request = detailsRequest(true);
    ((ObjectNode) request.get("scopingQuestions")).put("priorLegalAid", "yesSameMatter");
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
    ObjectNode request = detailsRequest();
    request.put("unexpected", "value");
    assertInvalidDetails(request);

    request = detailsRequest();
    ((ObjectNode) request.get("clientDetails")).put("unexpected", "value");
    assertInvalidDetails(request);

    request = detailsRequest(true);
    ((ObjectNode) request.path("clientDetails").path("address")).put("unexpected", "value");
    assertInvalidDetails(request);
  }

  @Test
  void updateApplicationDetails_rejectsWrongTypesAtEachEditableLevel() throws Exception {
    ObjectNode request = detailsRequest();
    request.put("ecfFlag", "false");
    assertInvalidDetails(request);

    request = detailsRequest();
    ((ObjectNode) request.get("clientDetails")).put("firstName", 123);
    assertInvalidDetails(request);

    request = detailsRequest();
    ((ObjectNode) request.get("clientDetails")).put("address", "malformed");
    assertInvalidDetails(request);

    request = detailsRequest(true);
    ((ObjectNode) request.path("clientDetails").path("address")).put("addressLine1", false);
    assertInvalidDetails(request);
  }

  @Test
  void updateApplicationDetails_rejectsDuplicateJsonProperties() throws Exception {
    String request =
        detailsRequest()
            .toString()
            .replace("\"ecfFlag\":false", "\"ecfFlag\":false,\"ecfFlag\":true");

    performDetailsPut("\"0\"", request)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("MALFORMED_REQUEST_BODY"));
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
}
