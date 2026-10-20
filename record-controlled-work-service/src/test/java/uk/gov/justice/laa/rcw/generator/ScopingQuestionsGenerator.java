package uk.gov.justice.laa.rcw.generator;

import uk.gov.justice.laa.rcw.model.FamilyLawClassification;
import uk.gov.justice.laa.rcw.model.PriorLegalAid;
import uk.gov.justice.laa.rcw.model.PrivateFamilyCaseScope;
import uk.gov.justice.laa.rcw.model.ScopingQuestions;

/** Generator for scoping-question models used by tests. */
public final class ScopingQuestionsGenerator {

  private ScopingQuestionsGenerator() {}

  /**
   * Creates valid scoping answers for a prior legal aid response.
   *
   * @param aid prior legal aid answer
   * @return valid default scoping answers
   */
  public static ScopingQuestions valid(PriorLegalAid aid) {
    return new ScopingQuestions()
        .priorLegalAid(aid)
        .familyLawClassification(FamilyLawClassification.PRIVATE)
        .needsAdviceOnEUOrInternationalMaintenance(false)
        .needsProtectiveInjunction(false)
        .privateFamilyCaseScope(PrivateFamilyCaseScope.DOMESTIC_ABUSE);
  }
}
