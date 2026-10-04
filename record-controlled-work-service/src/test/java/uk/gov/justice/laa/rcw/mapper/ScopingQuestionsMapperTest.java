package uk.gov.justice.laa.rcw.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.rcw.model.ApplicationScopingQuestions;
import uk.gov.justice.laa.rcw.model.FamilyLawClassification;
import uk.gov.justice.laa.rcw.model.PriorLegalAid;
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
                    .familyLawClassification(FamilyLawClassification.PUBLIC)))
        .isEqualTo(Map.of("priorLegalAid", "yesSameMatter", "familyLawClassification", "public"));
    assertThat(scopingQuestionsMapper.toDatastoreScopingQuestions(new ScopingQuestions()))
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

  @Test
  void shouldReadApplicationAnswersAndIgnoreOtherKeys() {
    assertThat(
            scopingQuestionsMapper.toApplicationScopingQuestions(
                Map.of("priorLegalAid", "yesSameMatter", "futureQuestion", "value")))
        .isEqualTo(new ApplicationScopingQuestions().priorLegalAid(PriorLegalAid.YES_SAME_MATTER));
  }

  @Test
  void shouldDiscardLegacyApplicationAnswersOnRead() {
    assertThat(
            scopingQuestionsMapper.toApplicationScopingQuestions(
                Map.of("priorLegalAid", "same_matter")))
        .isEqualTo(new ApplicationScopingQuestions());
    assertThat(scopingQuestionsMapper.toApplicationScopingQuestions(Map.of("unknown", true)))
        .isEqualTo(new ApplicationScopingQuestions());
    assertThat(scopingQuestionsMapper.toApplicationScopingQuestions("legacy value"))
        .isEqualTo(new ApplicationScopingQuestions());
    assertThat(scopingQuestionsMapper.toApplicationScopingQuestions(null)).isNull();
  }
}
