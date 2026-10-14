package uk.gov.justice.laa.rcw.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.gov.justice.laa.rcw.model.ApplicationScopingQuestions;
import uk.gov.justice.laa.rcw.model.CreateScopingQuestions;
import uk.gov.justice.laa.rcw.model.FamilyLawClassification;
import uk.gov.justice.laa.rcw.model.PriorLegalAid;
import uk.gov.justice.laa.rcw.model.UpdateApplicationDetailsRequestBody;

class ScopingQuestionsMapperTest {

  private final ScopingQuestionsMapper scopingQuestionsMapper = new ScopingQuestionsMapperImpl();

  @Test
  void shouldMapScopingQuestionsToDatastoreMap() {
    assertThat(
            scopingQuestionsMapper.toDatastoreScopingQuestions(
                new CreateScopingQuestions()
                    .priorLegalAid(PriorLegalAid.YES_SAME_MATTER)
                    .familyLawClassification(FamilyLawClassification.PRIVATE)
                    .needsAdviceOnEUOrInternationalMaintenance(false)
                    .needsProtectiveInjunction(false)))
        .isEqualTo(
            Map.of(
                "priorLegalAid",
                "yesSameMatter",
                "familyLawClassification",
                "private",
                "needsAdviceOnEUOrInternationalMaintenance",
                false,
                "needsProtectiveInjunction",
                false));
    assertThat(scopingQuestionsMapper.toDatastoreScopingQuestions(new CreateScopingQuestions()))
        .isEmpty();
    assertThat(scopingQuestionsMapper.toDatastoreScopingQuestions(null)).isEmpty();
  }

  @Test
  void shouldMapEditRequestAnswersToDatastoreScopingMap() {
    UpdateApplicationDetailsRequestBody request =
        new UpdateApplicationDetailsRequestBody().priorLegalAid(PriorLegalAid.YES_SAME_MATTER);

    assertThat(scopingQuestionsMapper.toDatastoreScopingQuestionsFromDetails(request))
        .isEqualTo(Map.of("priorLegalAid", "yesSameMatter"));
  }

  @ParameterizedTest
  @CsvSource({"public, PUBLIC", "private, PRIVATE"})
  void shouldReadApplicationAnswersAndIgnoreOtherKeys(
      String value, FamilyLawClassification classification) {
    assertThat(
            scopingQuestionsMapper.toApplicationScopingQuestions(
                Map.of(
                    "priorLegalAid", "yesSameMatter",
                    "familyLawClassification", value,
                    "futureQuestion", "value")))
        .isEqualTo(
            new ApplicationScopingQuestions()
                .priorLegalAid(PriorLegalAid.YES_SAME_MATTER)
                .familyLawClassification(classification));
  }

  @Test
  void shouldDiscardLegacyApplicationAnswersOnRead() {
    assertThat(
            scopingQuestionsMapper.toApplicationScopingQuestions(
                Map.of("priorLegalAid", "same_matter", "familyLawClassification", "legacy")))
        .isEqualTo(new ApplicationScopingQuestions());
    assertThat(scopingQuestionsMapper.toApplicationScopingQuestions(Map.of("unknown", true)))
        .isEqualTo(new ApplicationScopingQuestions());
    assertThat(scopingQuestionsMapper.toApplicationScopingQuestions("legacy value"))
        .isEqualTo(new ApplicationScopingQuestions());
    assertThat(scopingQuestionsMapper.toApplicationScopingQuestions(null)).isNull();
  }

  @Test
  void shouldReadUnknownOptionalAnswerAsNull() {
    assertThat(
            scopingQuestionsMapper.toApplicationScopingQuestions(
                Map.of(
                    "priorLegalAid", "yesSameMatter",
                    "familyLawClassification", "legacy")))
        .isEqualTo(new ApplicationScopingQuestions().priorLegalAid(PriorLegalAid.YES_SAME_MATTER));
  }

  @Test
  void shouldPreserveUnansweredAndFalseScopingAnswers() {
    ApplicationScopingQuestions legacyAnswers =
        scopingQuestionsMapper.toApplicationScopingQuestions(
            Map.of("priorLegalAid", "no"));

    assertThat(legacyAnswers.getNeedsAdviceOnEUOrInternationalMaintenance()).isNull();
    assertThat(legacyAnswers.getNeedsProtectiveInjunction()).isNull();

    Map<String, Object> answers = new HashMap<>();
    answers.put("priorLegalAid", "no");
    answers.put("needsAdviceOnEUOrInternationalMaintenance", null);
    answers.put("needsProtectiveInjunction", false);

    assertThat(scopingQuestionsMapper.toApplicationScopingQuestions(answers))
        .isEqualTo(
            new ApplicationScopingQuestions()
                .priorLegalAid(PriorLegalAid.NO)
                .needsAdviceOnEUOrInternationalMaintenance(null)
                .needsProtectiveInjunction(false));
  }
}
