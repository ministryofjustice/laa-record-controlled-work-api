package uk.gov.justice.laa.rcw.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.rcw.model.FamilyLawClassification;
import uk.gov.justice.laa.rcw.model.PriorLegalAid;
import uk.gov.justice.laa.rcw.model.ScopingQuestions;

class ScopingQuestionsMapperTest {

  private final ScopingQuestionsMapper scopingQuestionsMapper = new ScopingQuestionsMapperImpl();

  @Test
  void shouldMapScopingQuestionsToDatastoreMap() {
    assertThat(
            scopingQuestionsMapper.toDatastoreScopingQuestions(
                new ScopingQuestions()
                    .priorLegalAid(PriorLegalAid.YES_SAME_MATTER)
                    .familyLawClassification(FamilyLawClassification.PUBLIC)))
        .isEqualTo(Map.of("priorLegalAid", "yesSameMatter", "familyLawClassification", "public"));
    assertThat(scopingQuestionsMapper.toDatastoreScopingQuestions(new ScopingQuestions()))
        .isEmpty();
    assertThat(scopingQuestionsMapper.toDatastoreScopingQuestions(null)).isEmpty();
  }

  @Test
  void shouldReadRecognisedAnswersAndIgnoreOtherKeys() {
    assertThat(
            scopingQuestionsMapper.toScopingQuestions(
                Map.of("priorLegalAid", "yesSameMatter", "futureQuestion", "value")))
        .isEqualTo(new ScopingQuestions().priorLegalAid(PriorLegalAid.YES_SAME_MATTER));
  }

  @Test
  void shouldReadUnknownOptionalAnswerAsNull() {
    assertThat(
            scopingQuestionsMapper.toScopingQuestions(
                Map.of(
                    "priorLegalAid", "yesSameMatter",
                    "familyLawClassification", "legacy")))
        .isEqualTo(new ScopingQuestions().priorLegalAid(PriorLegalAid.YES_SAME_MATTER));
  }

  @Test
  void shouldDiscardLegacyScopingAnswersOnRead() {
    assertThat(scopingQuestionsMapper.toScopingQuestions(Map.of("priorLegalAid", "same_matter")))
        .isNull();
    assertThat(scopingQuestionsMapper.toScopingQuestions(Map.of("unknown", true))).isNull();
    assertThat(scopingQuestionsMapper.toScopingQuestions("legacy value")).isNull();
    assertThat(scopingQuestionsMapper.toScopingQuestions(null)).isNull();
  }
}
