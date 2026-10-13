package uk.gov.justice.laa.rcw.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.gov.justice.laa.rcw.model.ApplicationScopingQuestions;
import uk.gov.justice.laa.rcw.model.FamilyLawClassification;
import uk.gov.justice.laa.rcw.model.PriorLegalAid;
import uk.gov.justice.laa.rcw.model.PrivateFamilyCaseScope;
import uk.gov.justice.laa.rcw.model.ScopingQuestions;
import uk.gov.justice.laa.rcw.model.UpdateApplicationDetailsRequestBody;

class ScopingQuestionsMapperTest {

  private final ScopingQuestionsMapper scopingQuestionsMapper = new ScopingQuestionsMapperImpl();

  @Test
  void shouldMapScopingQuestionsToDatastoreMap() {
    assertThat(
            scopingQuestionsMapper.toDatastoreScopingQuestions(
                new ScopingQuestions()
                    .priorLegalAid(PriorLegalAid.YES_SAME_MATTER)
                    .familyLawClassification(FamilyLawClassification.PRIVATE)
                    .needsAdviceOnEUOrInternationalMaintenance(false)
                    .needsProtectiveInjunction(false)
                    .privateFamilyCaseScope(PrivateFamilyCaseScope.DOMESTIC_ABUSE)))
        .isEqualTo(
            Map.of(
                "priorLegalAid",
                "yesSameMatter",
                "familyLawClassification",
                "private",
                "needsAdviceOnEUOrInternationalMaintenance",
                false,
                "needsProtectiveInjunction",
                false,
                "privateFamilyCaseScope",
                "domesticAbuse"));
    assertThat(scopingQuestionsMapper.toDatastoreScopingQuestions(new ScopingQuestions()))
        .isEmpty();
    assertThat(scopingQuestionsMapper.toDatastoreScopingQuestions(null)).isEmpty();
  }

  @Test
  void shouldMapEditRequestAnswersToDatastoreScopingMap() {
    Map<String, Object> expected =
        new HashMap<>(
            Map.of(
                "priorLegalAid", "no",
                "familyLawClassification", "public",
                "needsProtectiveInjunction", false));
    expected.put("needsAdviceOnEUOrInternationalMaintenance", null);
    expected.put("privateFamilyCaseScope", null);
    UpdateApplicationDetailsRequestBody request =
        new UpdateApplicationDetailsRequestBody()
            .scopingQuestions(
                new ScopingQuestions()
                    .priorLegalAid(PriorLegalAid.NO)
                    .familyLawClassification(FamilyLawClassification.PUBLIC)
                    .needsAdviceOnEUOrInternationalMaintenance(null)
                    .needsProtectiveInjunction(false));

    assertThat(
            scopingQuestionsMapper.toDatastoreScopingQuestionsFromDetails(
                request.getScopingQuestions()))
        .isEqualTo(expected);
  }

  @Test
  void shouldOmitNullAnswersWhenCreatingScopingData() {
    assertThat(
            scopingQuestionsMapper.toDatastoreScopingQuestions(
                new ScopingQuestions()
                    .priorLegalAid(PriorLegalAid.NO)
                    .familyLawClassification(FamilyLawClassification.PUBLIC)))
        .isEqualTo(Map.of("priorLegalAid", "no", "familyLawClassification", "public"));
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
        scopingQuestionsMapper.toApplicationScopingQuestions(Map.of("priorLegalAid", "no"));

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
