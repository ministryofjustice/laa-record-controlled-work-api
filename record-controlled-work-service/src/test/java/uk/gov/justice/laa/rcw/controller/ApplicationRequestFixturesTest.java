package uk.gov.justice.laa.rcw.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.justice.laa.rcw.controller.ApplicationRequestFixtures.createApplicationRequestBody;
import static uk.gov.justice.laa.rcw.controller.ApplicationRequestFixtures.createRequestObjectMapper;
import static uk.gov.justice.laa.rcw.controller.ApplicationRequestFixtures.detailsRequest;
import static uk.gov.justice.laa.rcw.controller.ApplicationRequestFixtures.requestFromContractCase;
import static uk.gov.justice.laa.rcw.controller.ApplicationRequestFixtures.scopingQuestions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.rcw.model.FamilyLawClassification;

class ApplicationRequestFixturesTest {

  @Test
  void shouldPreserveExplicitNullAndFalseOverridesWithoutChangingTheContractCase() {
    var mapper = createRequestObjectMapper();
    ObjectNode contractCase = mapper.createObjectNode();
    ObjectNode raw = createApplicationRequestBody();
    raw.remove("scopingQuestions");
    contractCase.set("request", raw);
    var overrides = contractCase.putObject("scopingQuestionOverrides");
    overrides.putNull("needsAdviceOnEUOrInternationalMaintenance");
    overrides.put("careProceedingsStarted", false);

    var request = requestFromContractCase(mapper, contractCase);

    assertTrue(
        request
            .path("scopingQuestions")
            .path("needsAdviceOnEUOrInternationalMaintenance")
            .isNull());
    assertEquals(
        mapper.getNodeFactory().booleanNode(false),
        request.at("/scopingQuestions/careProceedingsStarted"));
    assertFalse(raw.has("scopingQuestions"));
    ((ObjectNode) request.get("scopingQuestions")).put("careProceedingsStarted", true);
    assertFalse(overrides.get("careProceedingsStarted").booleanValue());
  }

  @Test
  void shouldNotSupplyScopingQuestionsToRawContractRequests() {
    var mapper = createRequestObjectMapper();
    ObjectNode contractCase = mapper.createObjectNode();
    ObjectNode raw = createApplicationRequestBody();
    raw.remove("scopingQuestions");
    contractCase.set("request", raw);

    var request = requestFromContractCase(mapper, contractCase);

    assertEquals(raw, request);
    request.put("unexpected", true);
    assertFalse(raw.has("unexpected"));
  }

  @Test
  void shouldRejectRawScopingCombinedWithOverrides() {
    var mapper = createRequestObjectMapper();
    ObjectNode contractCase = mapper.createObjectNode();
    contractCase.set("request", createApplicationRequestBody());
    contractCase.putObject("scopingQuestionOverrides");

    assertThrows(IllegalStateException.class, () -> requestFromContractCase(mapper, contractCase));
  }

  @Test
  void shouldReturnIndependentMutableDefaults() {
    var first = scopingQuestions(FamilyLawClassification.PRIVATE);
    var second = scopingQuestions(FamilyLawClassification.PRIVATE);
    first.put("priorLegalAid", "yesSameMatter");

    assertEquals("no", second.path("priorLegalAid").textValue());
  }

  @Test
  void shouldPreserveRequiredNullableDetailsFieldsAndFalseAnswers() {
    var request = detailsRequest();

    assertTrue(request.get("reasonForReapplication").isNull());
    assertTrue(request.at("/clientDetails/niNumber").isNull());
    assertTrue(request.at("/clientDetails/address").isNull());
    assertEquals(
        createRequestObjectMapper().getNodeFactory().booleanNode(false), request.get("ecfFlag"));
    assertFalse(request.at("/scopingQuestions/needsProtectiveInjunction").booleanValue());
    assertNull(detailsRequest().at("/clientDetails").get("unexpected"));
  }
}
