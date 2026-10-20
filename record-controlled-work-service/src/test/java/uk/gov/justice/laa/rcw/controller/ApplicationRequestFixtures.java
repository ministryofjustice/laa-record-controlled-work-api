package uk.gov.justice.laa.rcw.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import uk.gov.justice.laa.rcw.generator.CreateApplicationRequestGenerator;
import uk.gov.justice.laa.rcw.generator.ScopingQuestionsGenerator;
import uk.gov.justice.laa.rcw.generator.UpdateApplicationDetailsRequestGenerator;
import uk.gov.justice.laa.rcw.model.FamilyLawClassification;
import uk.gov.justice.laa.rcw.model.PriorLegalAid;

final class ApplicationRequestFixtures {

  private ApplicationRequestFixtures() {}

  static ObjectMapper createRequestObjectMapper() {
    return new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);
  }

  static ObjectNode createApplicationRequestBody() {
    return createRequestObjectMapper()
        .valueToTree(CreateApplicationRequestGenerator.createWithName(null));
  }

  static ObjectNode detailsRequest() {
    return detailsRequest(false);
  }

  static ObjectNode detailsRequest(boolean fixedAddress) {
    ObjectMapper mapper =
        createRequestObjectMapper().setSerializationInclusion(JsonInclude.Include.ALWAYS);
    ObjectNode request =
        mapper.valueToTree(UpdateApplicationDetailsRequestGenerator.validRequest().ecfFlag(false));
    if (fixedAddress) {
      ObjectNode client = (ObjectNode) request.get("clientDetails");
      client.put("hasFixedAddress", true);
      client
          .putObject("address")
          .put("addressLine1", "1 Example Street")
          .putNull("addressLine2")
          .putNull("addressLine3")
          .putNull("addressLine4")
          .putNull("townOrCity")
          .putNull("postCode")
          .putNull("county")
          .put("country", "GB");
    }
    return request;
  }

  static ObjectNode scopingQuestions(FamilyLawClassification classification) {
    var answers =
        ScopingQuestionsGenerator.valid(PriorLegalAid.NO).familyLawClassification(classification);
    if (classification == FamilyLawClassification.PUBLIC) {
      answers
          .needsAdviceOnEUOrInternationalMaintenance(null)
          .needsProtectiveInjunction(null)
          .laspoEvidenceScope(null)
          .careProceedingsStarted(false);
    }
    return createRequestObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.ALWAYS)
        .valueToTree(answers);
  }

  static ObjectNode requestFromContractCase(ObjectMapper mapper, JsonNode contractCase) {
    ObjectNode request = ((ObjectNode) contractCase.path("request")).deepCopy();
    JsonNode overrides = contractCase.get("scopingQuestionOverrides");
    if (overrides == null) {
      return request;
    }
    if (!overrides.isObject()) {
      throw new IllegalStateException("Scoping-question overrides must be an object");
    }
    if (request.has("scopingQuestions")) {
      throw new IllegalStateException(
          "A contract case cannot combine raw scoping questions and overrides");
    }

    ObjectNode scopingQuestions =
        mapper.valueToTree(ScopingQuestionsGenerator.valid(PriorLegalAid.NO));
    scopingQuestions.remove("careProceedingsStarted");
    scopingQuestions.remove("careProceedingsParentalResponsibility");
    overrides
        .fields()
        .forEachRemaining(
            override -> scopingQuestions.set(override.getKey(), override.getValue().deepCopy()));
    request.set("scopingQuestions", scopingQuestions);
    return request;
  }
}
